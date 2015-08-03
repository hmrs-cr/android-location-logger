package com.hmsoft.locationlogger.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.locatrack.LocatrackOnlineStorer;
import com.hmsoft.locationlogger.service.LocationService;

public class LoginActivity extends Activity  {

    private UserLoginTask mAuthTask = null;

    // UI references.
    private EditText mDeviceIdView;
    private EditText mDeviceKeyView;
    private View mProgressView;
    private View mLoginFormView;


    public static void start(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if(preferences.getBoolean(getString(R.string.pref_locatrack_activated_key), false)) {
            MainActivity.start(getApplicationContext());
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Set up the login form.
        mDeviceIdView = (EditText) findViewById(R.id.deviceId);

        mDeviceKeyView = (EditText) findViewById(R.id.deviceKey);
        mDeviceKeyView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);

        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
        mDeviceIdView.setText(preferences.getString(getString(R.string.pref_locatrack_deviceid_key), ""));
        mDeviceKeyView.setText(preferences.getString(getString(R.string.pref_locatrack_key_key), ""));

        findViewById(R.id.btnWorkOffline).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                        .edit()
                        .putBoolean(getString(R.string.pref_locatrack_activated_key), true)
                        .putBoolean(getString(R.string.pref_vehiclemode_enabled_key), false)
                        .putBoolean(getString(R.string.pref_instant_upload_enabled_key), false)
                        .putString(getString(R.string.pref_locatrack_key_key), "")
                        .putString(getString(R.string.pref_locatrack_deviceid_key), "")
                        .apply();
                MainActivity.start(getApplicationContext());
                finish();
            }
        });

        Intent intent = getIntent();
        if(intent != null) {
            Uri data = intent.getData();
            if(data != null) {
                String path = data.getPath();
                if(path != null) {
                    String[] parts = path.split("/");
                    if(parts.length == 5 && parts[1].equals("device")  && parts[2].equals("register")) {
                        String deviceId = parts[3];
                        String deviceKey = parts[4];
                        mDeviceIdView.setText(deviceId);
                        mDeviceKeyView.setText(deviceKey);
                        attemptLogin();
                    }
                }
            }
        }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    public void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mDeviceIdView.setError(null);
        mDeviceKeyView.setError(null);

        // Store values at the time of the login attempt.
        String deviceID = mDeviceIdView.getText().toString();
        String deviceKey = mDeviceKeyView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid deviceKey, if the user entered one.
        if (TextUtils.isEmpty(deviceKey) || !isDeviceKeyValid(deviceKey)) {
            mDeviceKeyView.setError(getString(R.string.error_invalid_devicekey));
            focusView = mDeviceKeyView;
            cancel = true;
        }

        // Check for a valid deviceID address.
        if (TextUtils.isEmpty(deviceID)) {
            mDeviceIdView.setError(getString(R.string.error_field_required));
            focusView = mDeviceIdView;
            cancel = true;
        } else if (!isDeviceIdValid(deviceID)) {
            mDeviceIdView.setError(getString(R.string.error_invalid_deviceid));
            focusView = mDeviceIdView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(deviceID, deviceKey);
            mAuthTask.execute((Void) null);
        }
    }

    private boolean isDeviceIdValid(String deviceId) {
        return deviceId.length() > 4;
    }

    private boolean isDeviceKeyValid(String key) {
        return key.length() > 4;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mId;
        private final String mKey;

        UserLoginTask(String id, String key) {
            mId = id;
            mKey = key;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            preferences
                    .edit()
                    .putString(getString(R.string.pref_locatrack_key_key), mKey)
                    .putString(getString(R.string.pref_locatrack_deviceid_key), mId)
                    .commit();

            LocatrackLocation location = LocationService.getBestLastLocation(getApplicationContext());
            if(BuildConfig.DEBUG && location == null) location = new LocatrackLocation("");
            boolean success = false;
            if(location != null) {
                location.event = LocatrackLocation.EVENT_LOGIN;

                LocatrackOnlineStorer onlineStorer = new LocatrackOnlineStorer(getApplicationContext());
                onlineStorer.configure();
                success = onlineStorer.storeLocation(location);
                preferences
                        .edit()
                        .putBoolean(getString(R.string.pref_locatrack_activated_key), success)
                        .putBoolean(getString(R.string.pref_vehiclemode_enabled_key), true)
                        .commit();
            }

            return success;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);

            if (success) {
                LocationService.enable(getApplicationContext());
                MainActivity.start(getApplicationContext());
                finish();
            } else {
                mDeviceKeyView.setError(getString(R.string.error_incorrect_devicekey));
                mDeviceKeyView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}


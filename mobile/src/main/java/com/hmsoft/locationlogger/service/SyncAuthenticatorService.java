package com.hmsoft.locationlogger.service;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Patterns;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;

import java.util.Locale;
import java.util.regex.Pattern;


/**
 * A bound Service that instantiates the authenticator
 * when started.
 */
public class SyncAuthenticatorService extends Service {
    private static final String TAG = "SyncAuthenticatorService";

    private static final String ACCOUNT = "NmeaLogger";
    private static Account syncnAccount = null;

    /*
     * Implement AbstractAccountAuthenticator and stub out all
     * of its methods
     */
    private static class Authenticator extends AbstractAccountAuthenticator {
        // Simple constructor
        public Authenticator(Context context) {
            super(context);
        }

        // Editing properties is not supported
        @Override
        public Bundle editProperties(
                AccountAuthenticatorResponse r, String s) {
            throw new UnsupportedOperationException();
        }

        // Don't add additional accounts
        @Override
        public Bundle addAccount(
                AccountAuthenticatorResponse r,
                String s,
                String s2,
                String[] strings,
                Bundle bundle) throws NetworkErrorException {
            return null;
        }

        // Ignore attempts to confirm credentials
        @Override
        public Bundle confirmCredentials(
                AccountAuthenticatorResponse r,
                Account account,
                Bundle bundle) throws NetworkErrorException {
            return null;
        }

        // Getting an authentication token is not supported
        @Override
        public Bundle getAuthToken(
                AccountAuthenticatorResponse r,
                Account account,
                String s,
                Bundle bundle) throws NetworkErrorException {
            throw new UnsupportedOperationException();
        }

        // Getting a label for the auth token is not supported
        @Override
        public String getAuthTokenLabel(String s) {
            throw new UnsupportedOperationException();
        }

        // Updating user credentials is not supported
        @Override
        public Bundle updateCredentials(
                AccountAuthenticatorResponse r,
                Account account,
                String s, Bundle bundle) throws NetworkErrorException {
            throw new UnsupportedOperationException();
        }

        // Checking features for the account is not supported
        @Override
        public Bundle hasFeatures(
                AccountAuthenticatorResponse r,
                Account account, String[] strings) throws NetworkErrorException {
            throw new UnsupportedOperationException();
        }
    }

    // Instance field that stores the authenticator object
    private Authenticator mAuthenticator;

    @Override
    public void onCreate() {
        // Create a new authenticator object
        mAuthenticator = new Authenticator(this);
    }

    /*
     * When the system binds to this Service to make the RPC call
     * return the authenticator's IBinder.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }

    public static String getGoogleAccount(Context context)  {
        android.accounts.Account[] accounts = AccountManager.get(context)
                .getAccountsByType("com.google");

        Pattern emailPattern = Patterns.EMAIL_ADDRESS;
        for (android.accounts.Account account : accounts) {
            if (emailPattern.matcher(account.name).matches()) {
                if(Logger.DEBUG) Logger.debug(TAG, "Google account found:%s", account.name);

                return String.format("%s-%s-%s", Build.MANUFACTURER, Build.MODEL, account.name);
            }
        }

        String defaultAccount = String.format(Locale.ENGLISH, "%s-%s@hmsoft.com",
                Build.MANUFACTURER, Build.MODEL);

        if(Logger.DEBUG) Logger.debug(TAG, "Default account name:%s", defaultAccount);
        return defaultAccount;
    }
    /**
     * Create a new dummy account for the sync adapter
     *
     * @param context The application context
     */
    public static Account getSyncAccount(Context context) {
        if(syncnAccount == null) {
            syncnAccount = new Account(ACCOUNT, context.getString(R.string.sync_account_type));
            AccountManager accountManager = (AccountManager)context.getSystemService(ACCOUNT_SERVICE);
            accountManager.addAccountExplicitly(syncnAccount, null, null);
        }
        return syncnAccount;
    }
}

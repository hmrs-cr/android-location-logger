package com.hmsoft.nmealogger.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.hmsoft.nmealogger.R;

public class TouchLinearLayout extends LinearLayout {

    public TouchLinearLayout(Context context) {
        super(context);
        setClickable(true);
        setLongClickable(true);
    }

    public TouchLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setLongClickable(true);
    }

    private Drawable mTouchFeedbackDrawable;

    @Override
    protected void onAttachedToWindow(){
        super.onAttachedToWindow();

        mTouchFeedbackDrawable = getResources().getDrawable(R.drawable.touch_selector);
    }

    @Override
    protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);
        mTouchFeedbackDrawable.setBounds(0, 0, getWidth(), getHeight());
        mTouchFeedbackDrawable.draw(canvas);

    }

    @Override
    protected void drawableStateChanged() {
        if (mTouchFeedbackDrawable != null) {
            mTouchFeedbackDrawable.setState(getDrawableState());
            invalidate();
        }
        super.drawableStateChanged();
    }
}
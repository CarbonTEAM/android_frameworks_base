/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.internal.util.slim.ColorHelper;

import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryStateRegistar;

import java.text.NumberFormat;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback{


    private BatteryStateRegistar mBatteryStateRegistar;
    private boolean mBatteryPresent;
    private boolean mBatteryCharging;
    private int mBatteryLevel = 0;
    private boolean mShow;
    private boolean mForceShow;
    private boolean mAttached;
    private int mRequestedVisibility;

    private int mNewColor;
    private int mOldColor;
    private Animator mColorTransitionAnimator;
    private ContentResolver mResolver;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            loadShowBatteryTextSetting();
        }
    };

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResolver = context.getContentResolver();
        mRequestedVisibility = getVisibility();

        mNewColor = Settings.System.getInt(mResolver,
            Settings.System.STATUS_BAR_BATTERY_STATUS_TEXT_COLOR,
            0xffffffff);
        mOldColor = mNewColor;
        mColorTransitionAnimator = createColorTransitionAnimator(0, 1);

        loadShowBatteryTextSetting();
    }

    public void setForceShown(boolean forceShow) {
        mForceShow = forceShow;
        updateVisibility();
    }

    public void setBatteryStateRegistar(BatteryStateRegistar batteryStateRegistar) {
        mBatteryStateRegistar = batteryStateRegistar;
        if (mAttached) {
            mBatteryStateRegistar.addStateChangedCallback(this);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        mRequestedVisibility = visibility;
        updateVisibility();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Respect font size setting.
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.battery_level_text_size));
     }

     @Override
         public void onBatteryLevelChanged(boolean present, int level, boolean pluggedIn, boolean charging) {
             mBatteryLevel = level;
             String percentage = NumberFormat.getPercentInstance().format((double) mBatteryLevel / 100.0);
             setText(percentage);
             if (mBatteryPresent != present  || mBatteryCharging != charging) {
               mBatteryPresent = present;
               mBatteryCharging = charging;
               loadShowBatteryTextSetting();
             }
         }

    @Override
    public void onPowerSaveChanged() {
        // Not used
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mBatteryStateRegistar != null) {
            mBatteryStateRegistar.addStateChangedCallback(this);
        }
        mResolver.registerContentObserver(Settings.System.getUriFor(
                STATUS_BAR_BATTERY_STATUS_STYLE), false, mObserver);
        mResolver.registerContentObserver(Settings.System.getUriFor(
                STATUS_BAR_BATTERY_STATUS_PERCENT_STYLE), false, mObserver);
        mAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttached = false;
        mResolver.unregisterContentObserver(mObserver);

        if (mBatteryStateRegistar != null) {
            mBatteryStateRegistar.removeStateChangedCallback(this);
        }
    }

    private void updateVisibility() {
        if (mBatteryStateRegistar != null && (mShow || mForceShow)) {
            super.setVisibility(mRequestedVisibility);
        } else {
            super.setVisibility(GONE);
        }
    }

    public void setTextColor(boolean isHeader) {
        if (isHeader) {
            int headerColor = Settings.System.getInt(mResolver,
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_TEXT_COLOR, 0xffffffff);
            setTextColor(headerColor);
        } else {
            mNewColor = Settings.System.getInt(mResolver,
                    Settings.System.STATUS_BAR_BATTERY_STATUS_TEXT_COLOR, 0xffffffff);
            if (!mBatteryCharging && mBatteryLevel > 16) {
                if (mOldColor != mNewColor) {
                    mColorTransitionAnimator.start();
                }
            } else {
                setTextColor(mNewColor);
            }
        }
    }

    private ValueAnimator createColorTransitionAnimator(float start, float end) {
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);

        animator.setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float position = animation.getAnimatedFraction();
                int blended = ColorHelper.getBlendColor(mOldColor, mNewColor, position);
                setTextColor(blended);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOldColor = mNewColor;
            }
        });
        return animator;
    }

    private void loadShowBatteryTextSetting() {
        int currentUserId = ActivityManager.getCurrentUser();
        int mode = Settings.System.getIntForUser(mResolver,
                Settings.System.STATUS_BAR_BATTERY_STATUS_PERCENT_STYLE, 2, currentUserId);

                boolean showNextPercent = mBatteryPresent && (
                        mPercentMode == BatteryController.PERCENTAGE_MODE_OUTSIDE
                        || (mBatteryCharging && mPercentMode == BatteryController.PERCENTAGE_MODE_INSIDE));
        int batteryStyle = Settings.System.getIntForUser(mResolver,
                Settings.System.STATUS_BAR_BATTERY_STATUS_STYLE, 0, currentUserId);

        switch (batteryStyle) {
            case 3: //BATTERY_METER_TEXT
                showNextPercent = true;
                break;
            case 4: //BATTERY_METER_GONE
                showNextPercent = false;
                break;
            default:
                break;
        }

        mShow = showNextPercent;
        updateVisibility();
    }
}

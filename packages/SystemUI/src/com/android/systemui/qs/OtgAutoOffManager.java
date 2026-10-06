/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.systemui.qs;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.android.systemui.CoreStartable;

import javax.inject.Inject;

/**
 * Turns the OTG connection off automatically when it has not been used for the configured
 * amount of minutes, and keeps it on while a peripheral is connected.
 *
 * <p>SystemUI owns the timer because it is the component that survives while the phone is
 * idle. Settings (the switch under Sound &amp; vibration) and the quick settings tile only
 * write {@link OtgManager#SETTING_OTG_ENABLED}, which this class observes to schedule, reschedule
 * or cancel the alarm.
 */
public class OtgAutoOffManager implements CoreStartable {

    private static final String TAG = "OtgAutoOffManager";

    private static final long MINUTE_MS = 60 * 1000L;
    private static final int ALARM_REQUEST_CODE = 0x4f5447;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private ContentObserver mSettingsObserver;
    private OtgUsbReceiver mUsbReceiver;
    private boolean mRegistered;

    @Inject
    public OtgAutoOffManager(Context context) {
        mContext = context;
    }

    @Override
    public void start() {
        if (!OtgManager.isSupported()) {
            Log.d(TAG, "Vendor charger HAL not available, OTG auto off is disabled");
            return;
        }

        final ContentResolver resolver = mContext.getContentResolver();
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                reconcile(mContext);
            }
        };
        resolver.registerContentObserver(
                Settings.Global.getUriFor(OtgManager.SETTING_OTG_ENABLED),
                /* notifyForDescendants= */ false, mSettingsObserver);
        resolver.registerContentObserver(
                Settings.Global.getUriFor(OtgManager.SETTING_AUTO_OFF_MINUTES),
                /* notifyForDescendants= */ false, mSettingsObserver);

        mUsbReceiver = new OtgUsbReceiver();
        mContext.registerReceiver(mUsbReceiver, OtgUsbReceiver.buildIntentFilter(),
                Context.RECEIVER_EXPORTED);
        mRegistered = true;

        // Restores the state after a reboot and heals any mismatch with the hardware.
        reconcile(mContext);
    }

    /**
     * Makes the hardware switch, the persisted state and the auto off timer agree with each
     * other.
     */
    public static void reconcile(Context context) {
        if (!OtgManager.isSupported()) {
            return;
        }
        if (!OtgManager.isEnabled(context)) {
            cancelAlarm(context);
            if (OtgManager.getSwitch() == 1) {
                OtgManager.setSwitch(false);
            }
            return;
        }
        if (isPeripheralConnected(context)) {
            // A peripheral is connected, it must never be powered off under it.
            cancelAlarm(context);
            return;
        }
        if (OtgManager.getSwitch() != 1) {
            OtgManager.setSwitch(true);
        }
        scheduleAlarm(context);
    }

    /** (Re)starts the auto off countdown. */
    public static void scheduleAlarm(Context context) {
        final AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        final PendingIntent pendingIntent = getPendingIntent(context);
        alarmManager.cancel(pendingIntent);
        final long triggerAt = SystemClock.elapsedRealtime()
                + OtgManager.getAutoOffMinutes(context) * MINUTE_MS;
        try {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarms are not permitted, using an inexact alarm instead", e);
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void cancelAlarm(Context context) {
        final AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager != null) {
            alarmManager.cancel(getPendingIntent(context));
        }
    }

    /**
     * @return {@code true} when the phone is currently supplying a peripheral, either because
     *         the charger HAL reports the connection as online or because a USB device is
     *         enumerated.
     */
    public static boolean isPeripheralConnected(Context context) {
        if (OtgManager.isOtgOnline()) {
            return true;
        }
        final UsbManager usbManager = context.getSystemService(UsbManager.class);
        return usbManager != null && !usbManager.getDeviceList().isEmpty();
    }

    private static PendingIntent getPendingIntent(Context context) {
        final Intent intent = new Intent(context, OtgAutoOffReceiver.class);
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

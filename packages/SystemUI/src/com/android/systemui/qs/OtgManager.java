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

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

import vendor.oplus.hardware.charger.ICharger;

/**
 * Keeps track of the OTG (host mode USB) connection state.
 *
 * <p>The state lives in {@link Settings.Global} so that it can be shared with Settings (the switch
 * in Sound &amp; vibration), while the hardware switch is driven through the vendor charger HAL.
 * This mirrors what the stock OxygenOS implementation does in its {@code OtgHelper}.
 */
public final class OtgManager {

    private static final String TAG = "OtgManager";

    private static final String CHARGER_SERVICE =
            "vendor.oplus.hardware.charger.ICharger/default";

    /** Global setting holding the desired OTG state: 0 = off, 1 = on. */
    public static final String SETTING_OTG_ENABLED = "otg_connection";

    /** Global setting holding the automatic turn off delay in minutes. */
    public static final String SETTING_AUTO_OFF_MINUTES = "otg_auto_off_minutes";

    public static final int MIN_AUTO_OFF_MINUTES = 5;
    public static final int MAX_AUTO_OFF_MINUTES = 30;
    public static final int DEFAULT_AUTO_OFF_MINUTES = 10;

    private static volatile ICharger sCharger;

    private OtgManager() {
    }

    /** @return {@code true} when this device exposes the vendor charger HAL used for OTG. */
    public static boolean isSupported() {
        try {
            return ServiceManager.isDeclared(CHARGER_SERVICE);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to query the charger HAL", t);
            return false;
        }
    }

    public static boolean isEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), SETTING_OTG_ENABLED, 0) == 1;
    }

    /** Persists the requested state and applies it to the hardware. */
    public static void setEnabled(Context context, boolean enabled) {
        Settings.Global.putInt(context.getContentResolver(), SETTING_OTG_ENABLED, enabled ? 1 : 0);
        setSwitch(enabled);
    }

    /** @return the configured automatic turn off delay, in minutes. */
    public static int getAutoOffMinutes(Context context) {
        final int minutes = Settings.Global.getInt(context.getContentResolver(),
                SETTING_AUTO_OFF_MINUTES, DEFAULT_AUTO_OFF_MINUTES);
        return isValidAutoOffMinutes(minutes) ? minutes : DEFAULT_AUTO_OFF_MINUTES;
    }

    public static void setAutoOffMinutes(Context context, int minutes) {
        if (!isValidAutoOffMinutes(minutes)) {
            return;
        }
        Settings.Global.putInt(context.getContentResolver(), SETTING_AUTO_OFF_MINUTES, minutes);
    }

    public static boolean isValidAutoOffMinutes(int minutes) {
        return minutes >= MIN_AUTO_OFF_MINUTES && minutes <= MAX_AUTO_OFF_MINUTES
                && minutes % 5 == 0;
    }

    /** @return {@code true} when a peripheral is currently being powered by the phone. */
    public static boolean isOtgOnline() {
        try {
            final ICharger charger = getCharger();
            return charger != null && charger.getPsyOtgOnline() == 1;
        } catch (RemoteException e) {
            Log.e(TAG, "getPsyOtgOnline failed", e);
            return false;
        }
    }

    /** @return the hardware switch state, or {@code -1} when it cannot be read. */
    public static int getSwitch() {
        try {
            final ICharger charger = getCharger();
            return charger == null ? -1 : charger.getPsyOtgSwitch();
        } catch (RemoteException e) {
            Log.e(TAG, "getPsyOtgSwitch failed", e);
            return -1;
        }
    }

    /** Turns the hardware OTG switch on or off. */
    public static void setSwitch(boolean enabled) {
        try {
            final ICharger charger = getCharger();
            if (charger == null) {
                Log.w(TAG, "Charger HAL unavailable, cannot set the OTG switch");
                return;
            }
            charger.setPsyOtgSwitch(enabled ? "1" : "0");
        } catch (RemoteException e) {
            Log.e(TAG, "setPsyOtgSwitch failed", e);
        }
    }

    private static ICharger getCharger() {
        final ICharger charger = sCharger;
        if (charger != null && charger.asBinder().isBinderAlive()) {
            return charger;
        }
        try {
            final IBinder binder = ServiceManager.getService(CHARGER_SERVICE);
            if (binder == null) {
                return null;
            }
            final ICharger newCharger = ICharger.Stub.asInterface(binder);
            sCharger = newCharger;
            return newCharger;
        } catch (Throwable t) {
            Log.e(TAG, "Unable to get the charger HAL", t);
            return null;
        }
    }
}

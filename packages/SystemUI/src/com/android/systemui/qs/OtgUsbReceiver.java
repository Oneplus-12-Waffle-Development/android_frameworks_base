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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.util.Log;

/**
 * Keeps the auto off countdown in sync with what is plugged into the phone: it stops while a
 * peripheral is being powered in host mode and restarts from scratch once it is unplugged.
 */
public class OtgUsbReceiver extends BroadcastReceiver {

    private static final String TAG = "OtgUsbReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                && !UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)
                && !UsbManager.ACTION_USB_STATE.equals(action)) {
            return;
        }
        // Whether this matters for the countdown depends on the direction of the USB role: a
        // peripheral in host mode stops it, a PC or a charger in device mode does not.
        Log.d(TAG, "USB connection changed (" + action + "), reconciling the auto off state");
        OtgAutoOffManager.reconcile(context);
    }

    public static IntentFilter buildIntentFilter() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_STATE);
        return filter;
    }
}

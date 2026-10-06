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
import android.util.Log;

/** Fired by {@link OtgAutoOffManager} once the configured auto off delay has elapsed. */
public class OtgAutoOffReceiver extends BroadcastReceiver {

    private static final String TAG = "OtgAutoOffReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (OtgAutoOffManager.isPeripheralConnected(context)) {
            // Still in use, start a fresh countdown instead of cutting the power.
            Log.d(TAG, "A peripheral is connected, restarting the auto off countdown");
            OtgAutoOffManager.scheduleAlarm(context);
            return;
        }
        if (!OtgManager.isEnabled(context)) {
            OtgAutoOffManager.cancelAlarm(context);
            return;
        }
        Log.d(TAG, "The auto off delay elapsed, turning the OTG connection off");
        OtgManager.setEnabled(context, false);
    }
}

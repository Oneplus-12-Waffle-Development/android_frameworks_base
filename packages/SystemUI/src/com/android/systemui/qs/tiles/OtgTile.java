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

package com.android.systemui.qs.tiles;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.OtgManager;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import javax.inject.Inject;

/** Quick settings tile: OTG connection */
public class OtgTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "otg";

    private static final Intent SOUND_SETTINGS = new Intent(Settings.ACTION_SOUND_SETTINGS);

    @Nullable
    private Icon mIcon = null;

    private boolean mObserving;
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }
    };

    @Inject
    public OtgTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        final boolean enabled = !OtgManager.isEnabled(mContext);
        OtgManager.setEnabled(mContext, enabled);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return SOUND_SETTINGS;
    }

    @Override
    public boolean isAvailable() {
        return OtgManager.isSupported();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = OtgManager.isEnabled(mContext);
        if (mIcon == null) {
            mIcon = maybeLoadResourceIcon(R.drawable.ic_qs_otg);
        }
        state.icon = mIcon;
        if (state.value) {
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_otg_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_otg_off);
            state.state = Tile.STATE_INACTIVE;
        }
        state.label = getTileLabel();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_otg_label);
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening && !mObserving) {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(OtgManager.SETTING_OTG_ENABLED),
                    /* notifyForDescendants= */ false, mObserver);
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(OtgManager.SETTING_AUTO_OFF_MINUTES),
                    /* notifyForDescendants= */ false, mObserver);
            mObserving = true;
            refreshState();
        } else if (!listening && mObserving) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserving = false;
        }
    }

    @Override
    protected void handleDestroy() {
        if (mObserving) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserving = false;
        }
        super.handleDestroy();
    }
}

/*
 * SPDX-FileCopyrightText: 2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.ctslauncher;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.os.Build;
import android.service.quicksettings.TileService;

/** Quick settings tile for toggling the torch status. The last selected brightness is used. */
public class CtsTileService extends TileService {
    private static final long SYNC_DELAY_MS = 500L;

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onClick() {
        super.onClick();

        final var intent = CtsDelayedActivity.createIntent(this, SYNC_DELAY_MS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}

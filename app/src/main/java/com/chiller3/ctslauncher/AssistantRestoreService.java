/*
 * SPDX-FileCopyrightText: 2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.ctslauncher;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class AssistantRestoreService extends Service {
    private static final String EXTRA_COMPONENT = "component";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable restoreAssistant = this::restoreAssistant;

    private ComponentName origComponent;

    public static Intent createIntent(Context context, ComponentName component) {
        final var intent = new Intent(context, AssistantRestoreService.class);
        intent.putExtra(EXTRA_COMPONENT, component);
        return intent;
    }

    public static <T> T getParcelableExtra(Intent intent, String name, Class<T> clazz) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // This is broken on API 33.
            return intent.getParcelableExtra(name, clazz);
        } else {
            //noinspection deprecation,unchecked
            T extra = intent.getParcelableExtra(name);
            return clazz.isInstance(extra) ? extra : null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        origComponent = getParcelableExtra(intent, EXTRA_COMPONENT, ComponentName.class);

        handler.removeCallbacks(restoreAssistant);
        handler.postDelayed(restoreAssistant, 1000);

        return START_NOT_STICKY;
    }

    private void restoreAssistant() {
        AssistantSwitcher.restoreAssistant(this, origComponent);
        stopSelf();
    }
}

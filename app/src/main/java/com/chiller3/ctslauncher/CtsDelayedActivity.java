/*
 * SPDX-FileCopyrightText: 2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.ctslauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class CtsDelayedActivity extends Activity {
    private static final String EXTRA_DELAY_MS = "delay_ms";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable launchCts = () -> {
        CtsLauncher.launch(this);
        finish();
    };

    public static Intent createIntent(Context context, long delayMs) {
        final var intent = new Intent(context, CtsDelayedActivity.class);
        intent.putExtra(EXTRA_DELAY_MS, delayMs);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final var delayMs = getIntent().getLongExtra(EXTRA_DELAY_MS, -1);
        if (delayMs < 0) {
            throw new IllegalStateException("Invalid delay: " + delayMs);
        }

        handler.postDelayed(launchCts, delayMs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // If this was a configuration change, the timer will restart afterwards.
        handler.removeCallbacks(launchCts);
    }
}

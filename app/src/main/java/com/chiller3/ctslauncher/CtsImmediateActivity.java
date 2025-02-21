/*
 * SPDX-FileCopyrightText: 2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.ctslauncher;

import android.app.Activity;
import android.os.Bundle;

public class CtsImmediateActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CtsLauncher.launch(this);
        finish();
    }
}

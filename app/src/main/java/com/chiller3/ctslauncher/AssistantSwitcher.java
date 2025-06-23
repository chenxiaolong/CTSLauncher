/*
 * SPDX-FileCopyrightText: 2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.ctslauncher;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public final class AssistantSwitcher {
    private static final String TAG = AssistantSwitcher.class.getSimpleName();

    // Google Assistant package name.
    private static final String PACKAGE_NAME = "com.google.android.googlequicksearchbox";

    // Settings.Secure.VOICE_INTERACTION_SERVICE
    private static final String VOICE_INTERACTION_SERVICE = "voice_interaction_service";

    private AssistantSwitcher() {}

    private static ComponentName getAssistantComponent(Context context) {
        final var rawComponent = Settings.Secure.getString(context.getContentResolver(),
                VOICE_INTERACTION_SERVICE);
        if (rawComponent == null || rawComponent.isEmpty()) {
            return null;
        }

        return ComponentName.unflattenFromString(rawComponent);
    }

    private static void setAssistantComponent(Context context, ComponentName component) {
        Settings.Secure.putString(context.getContentResolver(), VOICE_INTERACTION_SERVICE,
                component != null ? component.flattenToString() : "");
    }

    private static ComponentName findCtsComponent(Context context) {
        final var resolveInfos = context.getPackageManager().queryIntentServices(
                new Intent(VoiceInteractionService.SERVICE_INTERFACE)
                        .setPackage(PACKAGE_NAME),
                PackageManager.GET_META_DATA
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);

        for (final var resolveInfo : resolveInfos) {
            final var serviceInfo = resolveInfo.serviceInfo;

            if (!Manifest.permission.BIND_VOICE_INTERACTION.equals(serviceInfo.permission)) {
                continue;
            }

            if (!serviceInfo.metaData.containsKey(VoiceInteractionService.SERVICE_META_DATA)) {
                continue;
            }

            return new ComponentName(serviceInfo.packageName, serviceInfo.name);
        }

        return null;
    }

    public record SwitchResult(ComponentName component, boolean changed) {}

    public static SwitchResult switchAssistant(Context context) {
        final ComponentName origComponent = getAssistantComponent(context);
        final ComponentName ctsComponent = findCtsComponent(context);

        Log.i(TAG, "Original assistant: " + origComponent);
        Log.i(TAG, "CTS assistant: " + ctsComponent);

        if (ctsComponent == null) {
            Log.w(TAG, "Not switching due to null CTS component");
        } else if (ctsComponent.equals(origComponent)) {
            Log.i(TAG, "Not switching because component already matches");
        } else {
            Log.i(TAG, "Switching from " + origComponent + " to " + ctsComponent);

            try {
                setAssistantComponent(context, ctsComponent);

                // This is atrocious, but we have no way to synchronously determine when
                // VoiceInteractionManagerService has reloaded itself after observing the settings
                // change.
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted delay", e);
                }

                return new SwitchResult(origComponent, true);
            } catch (SecurityException e) {
                Log.w(TAG, "Failed to switch to CTS assistant", e);
            }
        }

        return new SwitchResult(null, false);
    }

    public static void restoreAssistant(Context context, ComponentName origComponent) {
        Log.i(TAG, "Switching assistant back to " + origComponent);

        try {
            setAssistantComponent(context, origComponent);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to switch back to original assistant", e);
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.ctslauncher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.widget.Toast;

public final class CtsLauncher {
    private static final String TAG = CtsLauncher.class.getSimpleName();

    // We just do plain old raw binder calls.
    private static final String BINDER_DESCRIPTOR =
            "com.android.internal.app.IVoiceInteractionManagerService";
    private static final int BINDER_TRANSACTION_SHOW_SESSION_FROM_SESSION = 3;

    // ActivityManager.INVOCATION_TIME_MS_KEY
    private static final String INVOCATION_TIME_MS_KEY = "invocation_time_ms";
    // Context.VOICE_INTERACTION_MANAGER_SERVICE
    private static final String VOICE_INTERACTION_MANAGER_SERVICE = "voiceinteraction";

    // PixelConfigOverlayCommon - config_defaultContextualSearchKey
    private static final String CS_KEY = "omni.entry_point";

    // These are similar to the ContextualSearchManager entry points, but are not the same.
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_UNKNOWN = 0;
    private static final int OMNIENT_ENTRYPOINT_LONG_PRESS_NAV_HANDLE = 1;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_LONG_PRESS_HOME = 2;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_LONG_PRESS_OVERVIEW = 3;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_LONG_PRESS_POWER = 4;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_EDGE_PANEL = 5;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_LONG_PRESS_META = 6;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_FILES_APP = 7;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_KIKI_APP = 8;
    /** @noinspection unused*/
    private static final int OMNIENT_ENTRYPOINT_ACCESSIBILITY_SYSTEM_ACTION = 9;

    private CtsLauncher() {}

    @SuppressWarnings("SameParameterValue")
    private static IBinder getService(String serviceName) throws Exception {
        @SuppressLint("PrivateApi")
        final var serviceManager = Class.forName("android.os.ServiceManager");
        @SuppressLint("DiscouragedPrivateApi")
        final var getService = serviceManager.getDeclaredMethod("getService", String.class);

        final var iBinder = (IBinder) getService.invoke(null, serviceName);
        if (iBinder == null) {
            throw new IllegalStateException("Service" + serviceName + " not found");
        }

        return iBinder;
    }

    // Since we only care about a single binder call and it differs across Android versions, just
    // implement the binder call ourselves.
    @SuppressWarnings("SameParameterValue")
    private static boolean showSessionFromSession(IBinder service, IBinder token,
              Bundle sessionArgs, int flags, String attributionTag) throws RemoteException {
        final var data = Parcel.obtain();
        final var reply = Parcel.obtain();

        try {
            data.writeInterfaceToken(BINDER_DESCRIPTOR);
            data.writeStrongBinder(token);
            if (sessionArgs != null) {
                data.writeInt(1);
                sessionArgs.writeToParcel(data, 0);
            } else {
                data.writeInt(0);
            }
            data.writeInt(flags);

            // API 23 through 33 do not have this parameter.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                data.writeString(attributionTag);
            }

            service.transact(BINDER_TRANSACTION_SHOW_SESSION_FROM_SESSION, data, reply, 0);
            reply.readException();

            return reply.readInt() != 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public static void launch(Context context) {
        // We can't launch the contextual search activity via ContextualSearchManagerService because
        // it requires the ACCESS_CONTEXTUAL_SEARCH privileged permission. Even if we had that, it
        // still wouldn't work on Android builds where the Google app is not installed as a system
        // app. ContextualSearchManagerService requires config_defaultContextualSearchPackageName to
        // specify the package name and its activity lookup uses the MATCH_FACTORY_ONLY flag.
        //
        // It's not possible to directly launch the contextual search activity either, even when
        // installed as a privileged app, since the activity is not exported and only the root and
        // system UIDs are allowed to launch unexported activities.
        //
        // Luckily, the Google app also supports being launched in contextual search mode via
        // VoiceInteractionManagerService, which was introduced in AOSP android-14.0.0_r50 [1]. It
        // handles both the VIMS and CSMS intents in the same OmnientActivity. Unlike CSMS, VIMS
        // does not require any special permissions.
        //
        // [1] https://android.googlesource.com/platform/frameworks/base/+/b26321f6af2acb4d30730002e43d99a3e5c5a3e6%5E%21/

        final var switchResult = AssistantSwitcher.switchAssistant(context);

        try {
            final var service = getService(VOICE_INTERACTION_MANAGER_SERVICE);

            // Same as AssistManager.startAssist().
            final var sessionArgs = new Bundle();
            sessionArgs.putLong(INVOCATION_TIME_MS_KEY, SystemClock.elapsedRealtime());

            // Needed to trigger showSessionFromSession()'s contextual search flow.
            sessionArgs.putInt(CS_KEY, OMNIENT_ENTRYPOINT_LONG_PRESS_NAV_HANDLE);

            // Same as AssistManager.startVoiceInteractor().
            final var flags = VoiceInteractionSession.SHOW_WITH_ASSIST
                    | VoiceInteractionSession.SHOW_WITH_SCREENSHOT
                    | VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE;

            final var attributionTag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? context.getAttributionTag() : null;

            final var result = showSessionFromSession(
                    service, null, sessionArgs, flags, attributionTag);

            if (!result) {
                Log.e(TAG, "showSessionFromSession failed");
                Toast.makeText(context, R.string.android_launch_error, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch CTS", e);
            Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (switchResult.changed()) {
                context.startService(AssistantRestoreService.createIntent(
                        context, switchResult.component()));
            }
        }
    }
}

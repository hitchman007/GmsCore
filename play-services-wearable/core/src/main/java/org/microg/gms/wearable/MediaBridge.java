/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Bridges the active Android media session to WearOS over the existing
 * Wearable Message API. Message paths and payload fields follow the public
 * compatibility contract documented in microg/GmsCore#3286.
 */
public final class MediaBridge {
    private static final String TAG = "GmsWearMediaBridge";
    public static final String MEDIA_PATH = "/wearable/media";
    public static final String MEDIA_COMMAND_PATH = "/wearable/media/command";

    private static MediaSessionManager sessionManager;
    private static MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private static MediaController activeController;
    private static MediaController.Callback controllerCallback;
    private static Context appContext;
    private static WearableImpl wearable;

    private MediaBridge() {
    }

    public static synchronized void start(Context context, WearableImpl wearableImpl) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        stop(context);
        appContext = context.getApplicationContext();
        wearable = wearableImpl;
        sessionManager = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessionManager == null) return;

        final ComponentName listener = new ComponentName(
                appContext,
                "org.microg.gms.wearable.notification.WearableNotificationService"
        );
        sessionsListener = controllers -> attach(first(controllers));
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listener);
            attach(first(sessionManager.getActiveSessions(listener)));
        } catch (SecurityException e) {
            Log.d(TAG, "Media-session access waits for notification-listener approval");
        }
    }

    public static synchronized void stop(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (activeController != null && controllerCallback != null) {
                activeController.unregisterCallback(controllerCallback);
            }
            if (sessionManager != null && sessionsListener != null) {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
            }
        }
        activeController = null;
        controllerCallback = null;
        sessionsListener = null;
        sessionManager = null;
        wearable = null;
        appContext = null;
    }

    public static synchronized boolean handleCommand(Context context, byte[] payload) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        int command = MediaControlCommand.decode(payload);
        if (command == MediaControlCommand.INVALID) return false;

        if (command == MediaControlCommand.VOLUME_UP || command == MediaControlCommand.VOLUME_DOWN) {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return false;
            audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    command == MediaControlCommand.VOLUME_UP
                            ? AudioManager.ADJUST_RAISE
                            : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
            );
            return true;
        }

        MediaController controller = activeController;
        if (controller == null) return false;
        MediaController.TransportControls controls = controller.getTransportControls();
        switch (command) {
            case MediaControlCommand.PLAY:
                controls.play();
                return true;
            case MediaControlCommand.PAUSE:
                controls.pause();
                return true;
            case MediaControlCommand.NEXT:
                controls.skipToNext();
                return true;
            case MediaControlCommand.PREVIOUS:
                controls.skipToPrevious();
                return true;
            default:
                return false;
        }
    }

    private static synchronized void attach(MediaController controller) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        if (activeController != null && controllerCallback != null) {
            activeController.unregisterCallback(controllerCallback);
        }
        activeController = controller;
        controllerCallback = null;
        if (controller == null) return;

        controllerCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                sendCurrentState();
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                sendCurrentState();
            }

            @Override
            public void onSessionDestroyed() {
                attach(null);
            }
        };
        controller.registerCallback(controllerCallback);
        sendCurrentState();
    }

    private static MediaController first(List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) return null;
        return controllers.get(0);
    }

    private static synchronized void sendCurrentState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        if (activeController == null || wearable == null || appContext == null) return;

        byte[] payload = encodeState(activeController);
        for (String nodeId : wearable.getConnectedNodeIds()) {
            wearable.sendMessage(appContext.getPackageName(), nodeId, MEDIA_PATH, payload);
        }
    }

    static byte[] encodeState(MediaController controller) {
        try {
            PlaybackState playback = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();

            boolean playing = playback != null && playback.getState() == PlaybackState.STATE_PLAYING;
            String title = metadata != null ? string(metadata.getText(MediaMetadata.METADATA_KEY_TITLE)) : "";
            String artist = metadata != null ? string(metadata.getText(MediaMetadata.METADATA_KEY_ARTIST)) : "";
            String album = metadata != null ? string(metadata.getText(MediaMetadata.METADATA_KEY_ALBUM)) : "";
            long position = playback != null ? playback.getPosition() : -1L;
            long duration = metadata != null ? metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) : -1L;

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(playing ? 1 : 0);
            out.writeUTF(title);
            out.writeUTF(artist);
            out.writeUTF(album);
            out.writeLong(position);
            out.writeLong(duration);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected in-memory media encoding failure", e);
        }
    }

    private static String string(CharSequence value) {
        return value != null ? value.toString() : "";
    }
}

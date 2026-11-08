package com.unplugged.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal const val ACTION_START_VOICE = "com.unplugged.app.action.START_VOICE"

internal fun voiceSessionIntent(context: Context): Intent =
    Intent(context, VoiceSessionActivity::class.java).apply {
        action = ACTION_START_VOICE
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

internal fun voiceSessionPendingIntent(context: Context, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        voiceSessionIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

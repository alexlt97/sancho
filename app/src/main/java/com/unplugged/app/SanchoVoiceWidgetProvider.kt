package com.unplugged.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class SanchoVoiceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_sancho_voice).apply {
                setOnClickPendingIntent(
                    R.id.widget_voice_button,
                    voiceSessionPendingIntent(context, appWidgetId),
                )
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

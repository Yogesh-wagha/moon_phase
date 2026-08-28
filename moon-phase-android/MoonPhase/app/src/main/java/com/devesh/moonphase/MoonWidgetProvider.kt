package com.devesh.moonphase

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

class MoonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle
    ) {
        render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, MoonWidgetProvider::class.java)
            )
            onUpdate(context, manager, ids)
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val info = MoonCalc.info(Instant.now())
        val views = RemoteViews(context.packageName, R.layout.widget_moon)

        views.setImageViewBitmap(
            R.id.widget_moon_image,
            MoonGraphics.bitmap(BITMAP_PX, info.illumination, info.waxing)
        )
        views.setTextViewText(
            R.id.widget_percent,
            String.format(Locale.getDefault(), "%d%%", info.illuminationPercent.roundToInt())
        )
        views.setTextViewText(R.id.widget_phase, info.phaseName)
        views.setContentDescription(
            R.id.widget_moon_image,
            "${info.phaseName}, ${info.illuminationPercent.roundToInt()} percent illuminated"
        )

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        manager.updateAppWidget(id, views)
    }

    private companion object {
        const val BITMAP_PX = 360
    }
}

package com.gaurang.coroswidgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.gaurang.coroswidgets.MainActivity
import com.gaurang.coroswidgets.R
import com.gaurang.coroswidgets.data.CorosWidgetPayload
import com.gaurang.coroswidgets.data.ExpressiveTileType
import com.gaurang.coroswidgets.data.WidgetRepository
import com.gaurang.coroswidgets.ui.ExpressiveWidgetRenderer
import java.util.concurrent.Executors

object CorosWidgetUpdateHelper {
    const val ACTION_FORCE_REFRESH = "com.gaurang.coroswidgets.ACTION_FORCE_REFRESH"
    private val executor = Executors.newSingleThreadExecutor()

    private val providerClasses = listOf(
        WeeklyMileageWidgetProvider::class.java,
        TrainingLoadWidgetProvider::class.java,
        NextWorkoutWidgetProvider::class.java,
        ZoneSpectrumWidgetProvider::class.java,
        RaceCountdownWidgetProvider::class.java,
        CommandCenterWidgetProvider::class.java
    )

    fun refreshAllWidgetsAsync(context: Context, forceServerRefresh: Boolean = false, onComplete: ((Result<CorosWidgetPayload>) -> Unit)? = null) {
        val appCtx = context.applicationContext
        // Render cached state immediately so widgets never appear blank
        renderAllFromCache(appCtx)

        executor.execute {
            val result = WidgetRepository.fetchAndCachePayload(appCtx, forceRefresh = forceServerRefresh)
            val payload = result.getOrElse { WidgetRepository.getCachedPayload(appCtx) }
            renderAllWithPayload(appCtx, payload)
            onComplete?.invoke(result)
        }
    }

    fun renderAllFromCache(context: Context) {
        val payload = WidgetRepository.getCachedPayload(context)
        renderAllWithPayload(context, payload)
    }

    private fun renderAllWithPayload(context: Context, payload: CorosWidgetPayload) {
        val manager = AppWidgetManager.getInstance(context)
        providerClasses.forEach { cls ->
            val tileType = getTileTypeForClass(cls)
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            for (id in ids) {
                updateSingleWidget(context, manager, id, tileType, payload)
            }
        }
    }

    fun updateSingleWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        tileType: ExpressiveTileType,
        payload: CorosWidgetPayload = WidgetRepository.getCachedPayload(context)
    ) {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val density = context.resources.displayMetrics.density
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)

        val defaultDims = when (tileType) {
            ExpressiveTileType.WEEKLY_MILEAGE -> Pair(820, 400)
            ExpressiveTileType.TRAINING_LOAD -> Pair(440, 420)
            ExpressiveTileType.NEXT_WORKOUT -> Pair(820, 210)
            ExpressiveTileType.ZONE_SPECTRUM -> Pair(440, 420)
            ExpressiveTileType.RACE_COUNTDOWN -> Pair(820, 400)
            ExpressiveTileType.COMMAND_CENTER -> Pair(820, 800)
        }

        val widthPx = if (minW > 60) (minW * density * 1.3f).toInt().coerceIn(420, 900) else defaultDims.first
        val heightPx = if (minH > 40) (minH * density * 1.3f).toInt().coerceIn(420, 900) else defaultDims.second
        val styleIdx = WidgetRepository.getStyleForTile(context, tileType)

        val bitmap = ExpressiveWidgetRenderer.renderTileBitmap(
            context = context,
            tileType = tileType,
            payload = payload,
            widthPx = widthPx,
            heightPx = heightPx,
            styleIndexOverride = styleIdx
        )

        val views = RemoteViews(context.packageName, R.layout.widget_expressive_canvas)
        views.setImageViewBitmap(R.id.widget_canvas_image, bitmap)

        // Tap body -> open companion app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            appWidgetId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_canvas_image, openPending)

        // Tap top-right hotspot -> force live COROS refresh
        val refreshIntent = Intent(context, getProviderClassForTile(tileType)).apply {
            action = ACTION_FORCE_REFRESH
        }
        val refreshPending = PendingIntent.getBroadcast(
            context,
            appWidgetId + 10000,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_hotspot, refreshPending)

        manager.updateAppWidget(appWidgetId, views)
    }

    private fun getTileTypeForClass(cls: Class<out BaseCorosWidgetProvider>): ExpressiveTileType =
        when (cls) {
            WeeklyMileageWidgetProvider::class.java -> ExpressiveTileType.WEEKLY_MILEAGE
            TrainingLoadWidgetProvider::class.java -> ExpressiveTileType.TRAINING_LOAD
            NextWorkoutWidgetProvider::class.java -> ExpressiveTileType.NEXT_WORKOUT
            ZoneSpectrumWidgetProvider::class.java -> ExpressiveTileType.ZONE_SPECTRUM
            RaceCountdownWidgetProvider::class.java -> ExpressiveTileType.RACE_COUNTDOWN
            CommandCenterWidgetProvider::class.java -> ExpressiveTileType.COMMAND_CENTER
            else -> ExpressiveTileType.WEEKLY_MILEAGE
        }

    private fun getProviderClassForTile(tileType: ExpressiveTileType): Class<out BaseCorosWidgetProvider> =
        when (tileType) {
            ExpressiveTileType.WEEKLY_MILEAGE -> WeeklyMileageWidgetProvider::class.java
            ExpressiveTileType.TRAINING_LOAD -> TrainingLoadWidgetProvider::class.java
            ExpressiveTileType.NEXT_WORKOUT -> NextWorkoutWidgetProvider::class.java
            ExpressiveTileType.ZONE_SPECTRUM -> ZoneSpectrumWidgetProvider::class.java
            ExpressiveTileType.RACE_COUNTDOWN -> RaceCountdownWidgetProvider::class.java
            ExpressiveTileType.COMMAND_CENTER -> CommandCenterWidgetProvider::class.java
        }
}

abstract class BaseCorosWidgetProvider(private val tileType: ExpressiveTileType) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val cached = WidgetRepository.getCachedPayload(context)
        for (id in appWidgetIds) {
            CorosWidgetUpdateHelper.updateSingleWidget(context, appWidgetManager, id, tileType, cached)
        }
        CorosWidgetUpdateHelper.refreshAllWidgetsAsync(context, forceServerRefresh = false)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        CorosWidgetUpdateHelper.updateSingleWidget(context, appWidgetManager, appWidgetId, tileType)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            CorosWidgetUpdateHelper.ACTION_FORCE_REFRESH -> {
                CorosWidgetUpdateHelper.refreshAllWidgetsAsync(context, forceServerRefresh = true)
            }
            Intent.ACTION_CONFIGURATION_CHANGED,
            Intent.ACTION_WALLPAPER_CHANGED -> {
                // Re-render widgets immediately when device wallpaper or Dark/Light theme changes
                CorosWidgetUpdateHelper.renderAllFromCache(context)
            }
        }
    }
}

class WeeklyMileageWidgetProvider : BaseCorosWidgetProvider(ExpressiveTileType.WEEKLY_MILEAGE)
class TrainingLoadWidgetProvider : BaseCorosWidgetProvider(ExpressiveTileType.TRAINING_LOAD)
class NextWorkoutWidgetProvider : BaseCorosWidgetProvider(ExpressiveTileType.NEXT_WORKOUT)
class ZoneSpectrumWidgetProvider : BaseCorosWidgetProvider(ExpressiveTileType.ZONE_SPECTRUM)
class RaceCountdownWidgetProvider : BaseCorosWidgetProvider(ExpressiveTileType.RACE_COUNTDOWN)
class CommandCenterWidgetProvider : BaseCorosWidgetProvider(ExpressiveTileType.COMMAND_CENTER)

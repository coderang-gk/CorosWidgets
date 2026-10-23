package com.gaurang.coroswidgets.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object WidgetRepository {
    private const val PREFS_NAME = "coros_expressive_widgets_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_CACHED_JSON = "cached_json"
    private const val KEY_THEME_MODE = "theme_mode"

    // Default to Gaurang's always-on home server (LAN + Tailscale automatic fallback)
    const val DEFAULT_SERVER_URL = "http://192.168.1.3:8095/api/v1/widget-data"
    const val TAILSCALE_SERVER_URL = "http://100.95.186.84:8095/api/v1/widget-data"

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, url.trim())
            .apply()
    }

    fun getThemeMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, "MONET_DYNAMIC") ?: "MONET_DYNAMIC"
    }

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun getStyleForTile(context: Context, tileType: ExpressiveTileType): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultIdx = tileType.ordinal
        return prefs.getInt("style_${tileType.name}", defaultIdx)
    }

    fun setStyleForTile(context: Context, tileType: ExpressiveTileType, styleIndex: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("style_${tileType.name}", styleIndex)
            .apply()
    }

    fun getCachedPayload(context: Context): CorosWidgetPayload {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHED_JSON, null)
        if (!raw.isNullOrBlank()) {
            try {
                return parseJson(raw)
            } catch (_: Exception) {
            }
        }
        return CorosWidgetPayload()
    }

    fun fetchAndCachePayload(context: Context, forceRefresh: Boolean = false): Result<CorosWidgetPayload> {
        val primaryUrl = getServerUrl(context)
        val candidates = listOf(primaryUrl, DEFAULT_SERVER_URL, TAILSCALE_SERVER_URL).distinct()
        var lastError: Exception? = null

        for (baseUrl in candidates) {
            try {
                val targetUrl = if (forceRefresh) {
                    if (baseUrl.contains("?")) "$baseUrl&refresh=1" else "$baseUrl?refresh=1"
                } else {
                    baseUrl
                }
                val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3500
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code from $targetUrl")
                }
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val parsed = parseJson(body)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CACHED_JSON, body)
                    .apply()
                return Result.success(parsed)
            } catch (e: Exception) {
                lastError = e
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Unable to reach server"))
    }

    fun resolveMissedWorkout(
        context: Context,
        eventId: String,
        action: String, // "DROP" or "SHIFT"
        targetDate: String = ""
    ): Result<CorosWidgetPayload> {
        val primaryUrl = getServerUrl(context)
        val candidates = listOf(primaryUrl, DEFAULT_SERVER_URL, TAILSCALE_SERVER_URL).distinct()
        var lastError: Exception? = null

        val reqBody = JSONObject().apply {
            put("event_id", eventId)
            put("action", action)
            put("target_date", targetDate)
        }.toString()

        for (baseUrl in candidates) {
            try {
                val cleanBase = baseUrl.substringBefore("/api/v1/")
                val targetUrl = "$cleanBase/api/v1/resolve-missed-workout"
                val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 12000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { os ->
                    os.write(reqBody.toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code from $targetUrl")
                }
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val parsed = parseJson(body)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CACHED_JSON, body)
                    .apply()
                return Result.success(parsed)
            } catch (e: Exception) {
                lastError = e
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Could not resolve missed workout"))
    }

    fun parseJson(raw: String): CorosWidgetPayload {
        val root = JSONObject(raw)
        val athObj = root.optJSONObject("athlete") ?: JSONObject()
        val athlete = AthleteProfile(
            nickname = athObj.optString("nickname", "gaurangk"),
            rhr = athObj.optInt("rhr", 44),
            maxHr = athObj.optInt("max_hr", 195),
            lthr = athObj.optInt("lthr", 172),
            ltspPace = athObj.optString("ltsp_pace", "4:56 /km"),
            weightKg = athObj.optDouble("weight_kg", 81.5)
        )

        val wmObj = root.optJSONObject("weekly_mileage") ?: JSONObject()
        val daysArr = wmObj.optJSONArray("days")
        val daysList = mutableListOf<DayBarData>()
        if (daysArr != null && daysArr.length() > 0) {
            for (i in 0 until daysArr.length()) {
                val d = daysArr.getJSONObject(i)
                daysList.add(
                    DayBarData(
                        index = d.optInt("index", i),
                        dayLetter = d.optString("day_letter", "M"),
                        dayName = d.optString("day_name", "Mon"),
                        date = d.optString("date", ""),
                        isToday = d.optBoolean("is_today", false),
                        actualKm = d.optDouble("actual_km", 0.0),
                        plannedKm = d.optDouble("planned_km", 0.0),
                        status = d.optString("status", "REST"),
                        workoutType = d.optString("workout_type", "Rest"),
                        completionPct = d.optInt("completion_pct", 0)
                    )
                )
            }
        } else {
            daysList.addAll(WeeklyMileageData.defaultDays())
        }

        val weeklyMileage = WeeklyMileageData(
            actualKm = wmObj.optDouble("actual_km", 22.35),
            plannedKm = wmObj.optDouble("planned_km", 44.0),
            expectedKmSoFar = wmObj.optDouble("expected_km_so_far", 24.0),
            expectedCompletionPct = wmObj.optInt("expected_completion_pct", 93),
            remainingKm = wmObj.optDouble("remaining_km", 21.6),
            completionPct = wmObj.optInt("completion_pct", 51),
            runsCompleted = wmObj.optInt("runs_completed", 3),
            runsPlanned = wmObj.optInt("runs_planned", 4),
            avgPace = wmObj.optString("avg_pace", "6:36 /km"),
            totalElevationM = wmObj.optInt("total_elevation_m", 16),
            days = daysList
        )

        val tlObj = root.optJSONObject("training_load") ?: JSONObject()
        val trainingLoad = TrainingLoadData(
            acuteTl = tlObj.optInt("acute_tl", 401),
            weeklyTl = tlObj.optInt("weekly_tl", 401),
            targetTlMin = tlObj.optInt("target_tl_min", 380),
            targetTlMax = tlObj.optInt("target_tl_max", 580),
            loadRatioPct = tlObj.optInt("load_ratio_pct", 69),
            statusBadge = tlObj.optString("status_badge", "PRODUCTIVE"),
            rhr = tlObj.optInt("rhr", 44),
            lthr = tlObj.optInt("lthr", 172),
            recoveryHours = tlObj.optInt("recovery_hours", 17),
            readinessScore = tlObj.optInt("readiness_score", 84)
        )

        val nwObj = root.optJSONObject("next_workout") ?: JSONObject()
        val nextWorkout = NextWorkoutData(
            title = nwObj.optString("title", "Long Run 20K"),
            workoutType = nwObj.optString("workout_type", "Long Run"),
            date = nwObj.optString("date", "2026-10-25"),
            dayBadge = nwObj.optString("day_badge", "SUN 25 OCT"),
            countdown = nwObj.optString("countdown", "In 2 days"),
            targetDistanceKm = nwObj.optDouble("target_distance_km", 20.0),
            targetPace = nwObj.optString("target_pace", "5:45–6:15 /km"),
            targetHrZone = nwObj.optString("target_hr_zone", "Zone 2–3 (145–160 bpm)"),
            watchSynced = nwObj.optBoolean("watch_synced", true)
        )

        val zsObj = root.optJSONObject("zone_spectrum") ?: JSONObject()
        val zoneSpectrum = ZoneSpectrumData(
            aerobicPct = zsObj.optInt("aerobic_pct", 75),
            thresholdPct = zsObj.optInt("threshold_pct", 25),
            targetRatio = zsObj.optString("target_ratio", "80 / 20"),
            easyAvgHr = zsObj.optInt("easy_avg_hr", 149),
            qualityAvgHr = zsObj.optInt("quality_avg_hr", 174),
            z2CeilingHr = zsObj.optInt("z2_ceiling_hr", 155),
            zoneCreepStatus = zsObj.optString("zone_creep_status", "NO ZONE CREEP")
        )

        val rcObj = root.optJSONObject("race_countdown") ?: JSONObject()
        val raceCountdown = RaceCountdownData(
            raceName = rcObj.optString("race_name", "PUNE HALF MARATHON"),
            raceDate = rcObj.optString("race_date", "2026-12-13"),
            daysLeft = rcObj.optInt("days_left", 51),
            currentWeek = rcObj.optInt("current_week", 3),
            totalWeeks = rcObj.optInt("total_weeks", 10),
            phase = rcObj.optString("phase", "AEROBIC + THRESHOLD BUILD"),
            goalTime = rcObj.optString("goal_time", "1:45:00"),
            goalPace = rcObj.optString("goal_pace", "4:58 /km"),
            currentLtsp = rcObj.optString("current_ltsp", "4:56 /km"),
            currentLthr = rcObj.optInt("current_lthr", 172),
            paceDeltaSec = rcObj.optInt("pace_delta_sec", -2),
            readinessTag = rcObj.optString("readiness_tag", "ON TRACK · SUB-1:45")
        )

        fun parseZones(obj: JSONObject): List<HrZoneBreakdown> {
            val arr = obj.optJSONArray("hr_zones") ?: return LastRunData.defaultZones()
            if (arr.length() == 0) return LastRunData.defaultZones()
            val list = mutableListOf<HrZoneBreakdown>()
            for (i in 0 until arr.length()) {
                val z = arr.getJSONObject(i)
                list.add(
                    HrZoneBreakdown(
                        zoneIndex = z.optInt("zone_index", i + 1),
                        name = z.optString("name", "Zone ${i + 1}"),
                        bpmRange = z.optString("bpm_range", ""),
                        seconds = z.optInt("seconds", 0),
                        duration = z.optString("duration", "0:00"),
                        percent = z.optInt("percent", 0)
                    )
                )
            }
            return list
        }

        fun parseSplits(obj: JSONObject, key: String, fallback: List<RunSplitItem>): List<RunSplitItem> {
            val arr = obj.optJSONArray(key) ?: return fallback
            if (arr.length() == 0) return fallback
            val list = mutableListOf<RunSplitItem>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                list.add(
                    RunSplitItem(
                        index = s.optInt("index", i + 1),
                        distanceKm = s.optDouble("distance_km", 1.0),
                        duration = s.optString("duration", ""),
                        pace = s.optString("pace", ""),
                        adjustedPace = s.optString("adjusted_pace", ""),
                        avgHr = s.optInt("avg_hr", 0),
                        cadence = s.optInt("cadence", 0),
                        powerWatts = s.optInt("power_watts", 0),
                        strideCm = s.optInt("stride_cm", 0)
                    )
                )
            }
            return list
        }

        fun parseStringList(obj: JSONObject, key: String, fallback: List<String>): List<String> {
            val arr = obj.optJSONArray(key) ?: return fallback
            if (arr.length() == 0) return fallback
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotBlank()) list.add(s)
            }
            return if (list.isNotEmpty()) list else fallback
        }

        fun parseRunObj(r: JSONObject, titleKey: String): LastRunData {
            return LastRunData(
                name = r.optString(titleKey, r.optString("name", "Easy + Strides")),
                date = r.optString("date", "2026-10-23"),
                dayLabel = r.optString("day_label", "Fri, 23 Oct"),
                distanceKm = r.optDouble("distance_km", 6.81),
                plannedKm = r.optDouble("planned_km", 7.0),
                adherencePct = r.optInt("adherence_pct", 97),
                verdictBadge = r.optString("verdict_badge", "100% On Target (7K)"),
                durationMinutes = r.optDouble("duration_minutes", 49.6),
                avgPace = r.optString("avg_pace", "7:14 /km"),
                avgHr = r.optInt("avg_hr", 149),
                maxHr = r.optInt("max_hr", 173),
                cadence = r.optInt("cadence", 152),
                maxCadence = r.optInt("max_cadence", 180),
                powerWatts = r.optInt("power_watts", 201),
                maxPowerWatts = r.optInt("max_power_watts", 413),
                strideLengthCm = r.optInt("stride_length_cm", 91),
                trainingLoad = r.optInt("training_load", 110),
                aerobicTe = r.optDouble("aerobic_te", 2.8),
                anaerobicTe = r.optDouble("anaerobic_te", 1.6),
                vo2Max = r.optInt("vo2_max", 51),
                stamina7d = r.optDouble("stamina_7d", 96.0),
                bestKmPace = r.optString("best_km_pace", "6:42 /km"),
                elevGainM = r.optInt("elev_gain_m", 6),
                caloriesKcal = r.optInt("calories_kcal", 703),
                physiologyNote = r.optString(
                    "physiology_note",
                    "Solid Aerobic Control: Avg HR 149 bpm stayed comfortably inside Zone 2 (<155 bpm)."
                ),
                coachGuidance = r.optString(
                    "coach_guidance",
                    "Up Next: Long Run 20K (SUN 25 OCT) at 5:45–6:15 /km in Zone 2–3 (145–160 bpm)."
                ),
                aiVerdictHeadline = r.optString(
                    "ai_verdict_headline",
                    "A+ Shakeout & Neuromuscular Pop (97% Volume · 94% Zone 2)"
                ),
                aiExecutiveSummary = r.optString(
                    "ai_executive_summary",
                    "You executed the dual purpose of this workout with textbook discipline: keeping heart rate comfortably inside Zone 2 while firing up fast-twitch recruitment without accumulating fatigue."
                ),
                aiInsights = parseStringList(r, "ai_insights", LastRunData.defaultInsights()),
                aiTips = parseStringList(r, "ai_tips", LastRunData.defaultTips()),
                hrZones = parseZones(r),
                splits = parseSplits(r, "splits", LastRunData.defaultSplits()),
                stages = parseSplits(r, "stages", LastRunData.defaultStages())
            )
        }

        val lrObj = root.optJSONObject("last_run") ?: JSONObject()
        val lastRun = parseRunObj(lrObj, "name")

        val missedObj = root.optJSONObject("missed_workout")
        val missedWorkout = if (missedObj != null) {
            val optArr = missedObj.optJSONArray("options")
            val optList = mutableListOf<MissedWorkoutOption>()
            if (optArr != null) {
                for (i in 0 until optArr.length()) {
                    val o = optArr.getJSONObject(i)
                    optList.add(
                        MissedWorkoutOption(
                            label = o.optString("label", "Drop & Move On"),
                            sublabel = o.optString("sublabel", ""),
                            action = o.optString("action", "DROP"),
                            targetDate = o.optString("target_date", ""),
                            isRecommended = o.optBoolean("is_recommended", false)
                        )
                    )
                }
            }
            MissedWorkoutData(
                eventId = missedObj.optString("event_id", ""),
                title = missedObj.optString("title", ""),
                date = missedObj.optString("date", ""),
                targetDistanceKm = missedObj.optDouble("target_distance_km", 0.0),
                workoutType = missedObj.optString("workout_type", "Run"),
                coachAdvice = missedObj.optString("coach_advice", ""),
                options = optList
            )
        } else null

        val recentArr = root.optJSONArray("recent_runs")
        val recentList = mutableListOf<LastRunData>()
        if (recentArr != null && recentArr.length() > 0) {
            for (i in 0 until recentArr.length()) {
                val r = recentArr.getJSONObject(i)
                recentList.add(parseRunObj(r, "title"))
            }
        } else {
            recentList.add(lastRun)
        }

        return CorosWidgetPayload(
            updatedLabel = root.optString("updated_label", "9:03 PM"),
            generatedAt = root.optString("generated_at", ""),
            athlete = athlete,
            weeklyMileage = weeklyMileage,
            trainingLoad = trainingLoad,
            nextWorkout = nextWorkout,
            zoneSpectrum = zoneSpectrum,
            raceCountdown = raceCountdown,
            lastRun = lastRun,
            missedWorkout = missedWorkout,
            recentRuns = recentList
        )
    }
}

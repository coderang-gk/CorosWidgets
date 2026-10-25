package com.gaurang.coroswidgets.calendar

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.gaurang.coroswidgets.data.NextWorkoutData

data class WorkoutDetailStructure(
    val title: String,
    val targetPace: String,
    val targetHrZone: String,
    val targetDistanceKm: Double,
    val steps: List<Pair<String, String>>,
    val coachGuidance: String,
    val isFromCalendar: Boolean
)

object CalendarWorkoutHelper {

    fun resolveWorkoutStructure(context: Context, workout: NextWorkoutData): WorkoutDetailStructure {
        // 1. Try reading directly from Google Calendar
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            val fromCal = queryCalendarForWorkout(context, workout)
            if (fromCal != null) {
                return fromCal
            }
        }

        // 2. Fallback to smart parsing based on title and workout type
        return fallbackStructure(workout)
    }

    private fun queryCalendarForWorkout(context: Context, workout: NextWorkoutData): WorkoutDetailStructure? {
        try {
            val uri: Uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART
            )

            // Search around the current window (from now - 1 day to + 7 days)
            val now = System.currentTimeMillis()
            val startWindow = now - (24 * 3600 * 1000L)
            val endWindow = now + (7 * 24 * 3600 * 1000L)

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(startWindow.toString(), endWindow.toString())

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use { c ->
                val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)

                while (c.moveToNext()) {
                    val calTitle = if (titleIdx != -1) c.getString(titleIdx) else ""
                    val calDesc = if (descIdx != -1) c.getString(descIdx) else ""

                    if (matchesWorkout(calTitle, workout.title)) {
                        val parsed = parseCalendarDescription(calTitle, calDesc, workout)
                        if (parsed != null) return parsed
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun matchesWorkout(calTitle: String, workoutTitle: String): Boolean {
        val cleanCal = calTitle.lowercase().replace("—", "-").replace("·", "-")
        val cleanWk = workoutTitle.lowercase().replace("—", "-").replace("·", "-")

        if (cleanCal.contains(cleanWk) || cleanWk.contains(cleanCal)) return true

        val keywords = listOf("5x1.2k", "intervals", "2x4k", "threshold", "long run 20k", "tempo")
        for (kw in keywords) {
            if (cleanCal.contains(kw) && cleanWk.contains(kw)) return true
        }
        return false
    }

    fun parseCalendarDescription(title: String, desc: String, fallbackWorkout: NextWorkoutData): WorkoutDetailStructure? {
        if (desc.isBlank()) return null

        val lines = desc.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val steps = mutableListOf<Pair<String, String>>()
        var mainPace = ""

        var currentSection = ""
        val sectionLines = mutableListOf<String>()

        fun flushSection() {
            if (currentSection.isNotBlank() && sectionLines.isNotEmpty()) {
                val detail = sectionLines.joinToString(" · ")
                val stepNum = steps.size + 1
                val label = "$stepNum. $currentSection"
                steps.add(label to detail)

                if (currentSection.contains("Main", ignoreCase = true)) {
                    val paceRegex = Regex("""\d:\d{2}[–-]\d:\d{2}(?:\s*/km)?""")
                    val m = paceRegex.find(detail)
                    if (m != null) {
                        mainPace = m.value.trim()
                        if (!mainPace.endsWith("/km")) mainPace += " /km"
                    }
                }
            }
            sectionLines.clear()
        }

        for (line in lines) {
            if (line.endsWith(":") && line.length < 35 && !line.startsWith("http")) {
                flushSection()
                currentSection = line.removeSuffix(":").trim()
            } else if (currentSection.isNotBlank()) {
                sectionLines.add(line)
            }
        }
        flushSection()

        if (steps.isEmpty()) return null

        val resolvedPace = if (mainPace.isNotBlank()) mainPace else "4:35–4:40 /km"
        val cleanTitle = title
            .replace(Regex("""^W\d+\s+[A-Za-z]+\s*[—·-]\s*"""), "")
            .trim()

        return WorkoutDetailStructure(
            title = if (cleanTitle.isNotBlank()) cleanTitle else fallbackWorkout.title,
            targetPace = resolvedPace,
            targetHrZone = if (title.contains("Interval", ignoreCase = true)) "Zone 5 (173–185 bpm)" else fallbackWorkout.targetHrZone,
            targetDistanceKm = fallbackWorkout.targetDistanceKm,
            steps = steps,
            coachGuidance = "Target pace executed from your Google Calendar training block. Focus on clean form and consistent split execution.",
            isFromCalendar = true
        )
    }

    fun fallbackStructure(workout: NextWorkoutData): WorkoutDetailStructure {
        val title = workout.title
        val isIntervals = title.contains("Interval", ignoreCase = true) || title.contains("5x1.2K", ignoreCase = true) || workout.workoutType.contains("Interval", ignoreCase = true)
        val isThreshold = title.contains("Threshold", ignoreCase = true) || workout.workoutType.contains("Threshold", ignoreCase = true)
        val isLongRun = title.contains("Long", ignoreCase = true) || workout.workoutType.contains("Long", ignoreCase = true)
        val isHMBlock = title.contains("HM Pace", ignoreCase = true) || title.contains("2x4K", ignoreCase = true)

        val steps = when {
            isIntervals -> listOf(
                "1. Warm-up" to "2.0 km easy aerobic jog (6:00–6:30 /km) + dynamic mobility",
                "2. Strides (Optional)" to "4 × 20 sec relaxed strides focusing on turnover & form",
                "3. Main Intervals" to "5 × 1.2 km repeats at 4:35–4:40 /km (Zone 5, 173–185 bpm)",
                "4. Float Recovery" to "2 minutes easy jogging (6:15–6:45 /km) between each repeat",
                "5. Cool-down" to "1.5–2.0 km gentle flush jog to clear lactate accumulation"
            )
            isHMBlock -> listOf(
                "1. Warm-up" to "5.0 km easy aerobic running (5:50–6:10 /km)",
                "2. Block 1" to "4.0 km at goal half marathon pace (4:58–5:02 /km)",
                "3. Float Recovery" to "1.0 km easy recovery jog (6:10–6:40 /km)",
                "4. Block 2" to "4.0 km at goal half marathon pace (4:58–5:02 /km)",
                "5. Cool-down" to "6.0 km easy aerobic running to finish 20K total volume"
            )
            isThreshold -> listOf(
                "1. Warm-up" to "2.0 km easy aerobic jog (<148 bpm) + dynamic mobility drills",
                "2. Main Work" to "Threshold repeats at 4:56 /km (${workout.targetHrZone}) with 3 min float recoveries",
                "3. Cool-down" to "1.5–2.0 km flush jog to clear lactate accumulation"
            )
            isLongRun -> listOf(
                "1. Warm-up" to "2.0 km gentle progression building into steady Zone 2",
                "2. Main Work" to "Sustained aerobic volume (${workout.targetDistanceKm.toInt()} km total) strictly under ${workout.targetHrZone} (5:45–6:15 /km)",
                "3. Hydration & Fuel" to "Take 25-30g carbs/electrolyte gel every 40-45 minutes"
            )
            title.contains("Strides", ignoreCase = true) -> listOf(
                "1. Warm-up" to "1.0 km gentle jog to elevate heart rate gradually",
                "2. Main Work" to "Continuous easy aerobic recovery run (5:55–6:25 /km, <152 bpm)",
                "3. Form Strides" to "6 × 20 sec relaxed strides on flat surface with full walk-back recovery"
            )
            else -> listOf(
                "1. Warm-up" to "1.0 km gentle jog to elevate heart rate gradually",
                "2. Main Work" to "Steady aerobic base running at conversational pace (<152 bpm)",
                "3. Strides" to "Optional 4 × 20s relaxed strides focusing on form and cadence"
            )
        }

        val targetPace = if (isIntervals) "4:35–4:40 /km" else if (isHMBlock) "4:58–5:02 /km" else workout.targetPace
        val hrZone = if (isIntervals) "Zone 5 (173–185 bpm)" else workout.targetHrZone

        val coachGuidance = when {
            isIntervals -> "Physiological Focus: VO2 Max & Neuromuscular Power. Execute the 5 repeats strictly at 4:35–4:40 /km. Keep the 2-minute recoveries truly active and gentle."
            isHMBlock -> "Physiological Focus: Lactate Clearance & Race-Pace Economy. Run the two 4K segments right at target HM pace (4:58–5:02 /km) without turning it into a race."
            isThreshold -> "Physiological Focus: Lactate Threshold. Maintain threshold intensity (Zone 4) to elevate your anaerobic threshold speed."
            isLongRun -> "Physiological Focus: Aerobic Endurance & Mitochondria Density. Keep heart rate strictly inside Zone 2 throughout."
            else -> "Physiological Focus: Aerobic Base Recovery. Keep effort conversational and heart rate low."
        }

        return WorkoutDetailStructure(
            title = workout.title,
            targetPace = targetPace,
            targetHrZone = hrZone,
            targetDistanceKm = workout.targetDistanceKm,
            steps = steps,
            coachGuidance = coachGuidance,
            isFromCalendar = false
        )
    }
}

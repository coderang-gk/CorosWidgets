package com.gaurang.coroswidgets.data

data class AthleteProfile(
    val nickname: String = "gaurangk",
    val rhr: Int = 44,
    val maxHr: Int = 195,
    val lthr: Int = 172,
    val ltspPace: String = "4:56 /km",
    val weightKg: Double = 81.5
)

data class DayBarData(
    val index: Int,
    val dayLetter: String,
    val dayName: String,
    val date: String,
    val isToday: Boolean,
    val actualKm: Double,
    val plannedKm: Double,
    val status: String,
    val workoutType: String,
    val completionPct: Int = if (plannedKm > 0) ((actualKm / plannedKm) * 100.0 + 0.5).toInt() else 0
)

data class WeeklyMileageData(
    val actualKm: Double = 22.35,
    val plannedKm: Double = 44.0,
    val expectedKmSoFar: Double = 24.0,
    val expectedCompletionPct: Int = 93,
    val remainingKm: Double = 21.6,
    val completionPct: Int = 51,
    val runsCompleted: Int = 3,
    val runsPlanned: Int = 4,
    val avgPace: String = "6:36 /km",
    val totalElevationM: Int = 16,
    val days: List<DayBarData> = defaultDays()
) {
    companion object {
        fun defaultDays(): List<DayBarData> = listOf(
            DayBarData(0, "M", "Mon", "2026-10-19", false, 0.0, 0.0, "REST", "Rest", 0),
            DayBarData(1, "T", "Tue", "2026-10-20", false, 8.53, 10.0, "COMPLETED", "2x4K Threshold", 85),
            DayBarData(2, "W", "Wed", "2026-10-21", false, 7.01, 7.0, "COMPLETED", "Easy 7K", 100),
            DayBarData(3, "T", "Thu", "2026-10-22", false, 0.0, 0.0, "REST", "Rest", 0),
            DayBarData(4, "F", "Fri", "2026-10-23", true, 6.81, 7.0, "COMPLETED", "Easy 7K + Strides", 97),
            DayBarData(5, "S", "Sat", "2026-10-24", false, 0.0, 0.0, "REST", "Rest", 0),
            DayBarData(6, "S", "Sun", "2026-10-25", false, 0.0, 20.0, "PLANNED", "Long Run 20K", 0)
        )
    }
}

data class TrainingLoadData(
    val acuteTl: Int = 401,
    val weeklyTl: Int = 401,
    val targetTlMin: Int = 380,
    val targetTlMax: Int = 580,
    val loadRatioPct: Int = 69,
    val statusBadge: String = "PRODUCTIVE",
    val rhr: Int = 44,
    val lthr: Int = 172,
    val recoveryHours: Int = 17,
    val readinessScore: Int = 84
)

data class NextWorkoutData(
    val title: String = "Long Run 20K",
    val workoutType: String = "Long Run",
    val date: String = "2026-10-25",
    val dayBadge: String = "SUN 25 OCT",
    val countdown: String = "In 2 days",
    val targetDistanceKm: Double = 20.0,
    val targetPace: String = "5:45–6:15 /km",
    val targetHrZone: String = "Zone 2–3 (145–160 bpm)",
    val watchSynced: Boolean = true
)

data class ZoneSpectrumData(
    val aerobicPct: Int = 75,
    val thresholdPct: Int = 25,
    val targetRatio: String = "80 / 20",
    val easyAvgHr: Int = 149,
    val qualityAvgHr: Int = 174,
    val z2CeilingHr: Int = 155,
    val zoneCreepStatus: String = "NO ZONE CREEP"
)

data class RaceCountdownData(
    val raceName: String = "PUNE HALF MARATHON",
    val raceDate: String = "2026-12-13",
    val daysLeft: Int = 51,
    val currentWeek: Int = 3,
    val totalWeeks: Int = 10,
    val phase: String = "AEROBIC + THRESHOLD BUILD",
    val goalTime: String = "1:45:00",
    val goalPace: String = "4:58 /km",
    val currentLtsp: String = "4:56 /km",
    val currentLthr: Int = 172,
    val paceDeltaSec: Int = -2,
    val readinessTag: String = "ON TRACK · SUB-1:45"
)

data class HrZoneBreakdown(
    val zoneIndex: Int,
    val name: String,
    val bpmRange: String,
    val seconds: Int,
    val duration: String,
    val percent: Int
)

data class RunSplitItem(
    val index: Int,
    val distanceKm: Double,
    val duration: String,
    val pace: String,
    val adjustedPace: String,
    val avgHr: Int,
    val cadence: Int,
    val powerWatts: Int,
    val strideCm: Int
)

data class MissedWorkoutOption(
    val label: String = "Drop & Move On",
    val sublabel: String = "",
    val action: String = "DROP",
    val targetDate: String = "",
    val isRecommended: Boolean = false
)

data class MissedWorkoutData(
    val eventId: String = "",
    val title: String = "",
    val date: String = "",
    val targetDistanceKm: Double = 0.0,
    val workoutType: String = "Run",
    val coachAdvice: String = "",
    val options: List<MissedWorkoutOption> = emptyList()
)

data class LastRunData(
    val name: String = "Easy + Strides",
    val date: String = "2026-10-23",
    val dayLabel: String = "Fri, 23 Oct",
    val distanceKm: Double = 6.81,
    val plannedKm: Double = 7.0,
    val adherencePct: Int = 97,
    val verdictBadge: String = "100% On Target (7K)",
    val durationMinutes: Double = 49.6,
    val avgPace: String = "7:14 /km",
    val avgHr: Int = 149,
    val maxHr: Int = 173,
    val cadence: Int = 152,
    val maxCadence: Int = 180,
    val powerWatts: Int = 201,
    val maxPowerWatts: Int = 413,
    val strideLengthCm: Int = 91,
    val trainingLoad: Int = 110,
    val aerobicTe: Double = 2.8,
    val anaerobicTe: Double = 1.6,
    val vo2Max: Int = 51,
    val stamina7d: Double = 96.0,
    val bestKmPace: String = "6:42 /km",
    val elevGainM: Int = 6,
    val caloriesKcal: Int = 703,
    val physiologyNote: String = "Solid Aerobic Control: Avg HR 149 bpm stayed comfortably inside Zone 2 (<155 bpm).",
    val coachGuidance: String = "Up Next: Long Run 20K (SUN 25 OCT) at 5:45–6:15 /km in Zone 2–3 (145–160 bpm).",
    val aiVerdictHeadline: String = "A+ Shakeout & Neuromuscular Pop (97% Volume · 94% Zone 2)",
    val aiExecutiveSummary: String = "You executed the dual purpose of 'Easy 7K + Strides' with textbook discipline: keeping 94% of the run strictly below your 155 bpm aerobic ceiling (149 bpm avg) before firing up fast-twitch fibers in the strides (peaking at 413W / 173 bpm) without building fatigue.",
    val aiInsights: List<String> = defaultInsights(),
    val aiTips: List<String> = defaultTips(),
    val hrZones: List<HrZoneBreakdown> = defaultZones(),
    val splits: List<RunSplitItem> = defaultSplits(),
    val stages: List<RunSplitItem> = defaultStages()
) {
    companion object {
        fun defaultInsights(): List<String> = listOf(
            "Aerobic Discipline: Your 6 km easy base block stayed at 144–151 bpm (23 bpm below your 172 bpm LTHR). Even as warm evening conditions slowed pace from 7:01/km to 7:37/km, you protected your heart rate instead of forcing pace.",
            "Strides Mechanics: During the 20s strides, pace jumped to ~3:51/km, power surged to 413W, and stride length opened up to ~163 cm while full recovery kept Anaerobic TE controlled at 1.6.",
            "Form Watchpoint — Easy Cadence: Your aerobic block cadence averaged 152 spm (91 cm stride). At ~7:14/km, 152 spm increases ground contact time; aim for lighter, quicker 158–162 spm steps."
        )

        fun defaultTips(): List<String> = listOf(
            "For Long Run 20K (SUN 25 OCT): Lock Km 1–12 strictly at 6:00–6:15/km (<152 bpm). Do not bank time in the first hour.",
            "Late-Run Progression: Only press toward 5:45–5:50/km after Km 14 if heart rate is still under 156 bpm.",
            "Fueling & Cadence Cue: Take 25–30g carbs at 45 min & 85 min, and focus on a crisp 160+ spm rhythm to prevent late-run quad heaviness."
        )
        fun defaultZones(): List<HrZoneBreakdown> = listOf(
            HrZoneBreakdown(1, "Z1–2 Aerobic Base", "<155 bpm", 2763, "46:03", 94),
            HrZoneBreakdown(2, "Z3 Aerobic Power", "156–163 bpm", 125, "2:05", 4),
            HrZoneBreakdown(3, "Z4 LTHR Threshold", "164–175 bpm", 70, "1:10", 2),
            HrZoneBreakdown(4, "Z5 VO2 Max / Anaer.", "176–182 bpm", 0, "0:00", 0),
            HrZoneBreakdown(5, "Z6 Max Effort", ">182 bpm", 0, "0:00", 0)
        )

        fun defaultSplits(): List<RunSplitItem> = listOf(
            RunSplitItem(1, 1.0, "7:08", "7:08", "7:00", 144, 151, 207, 94),
            RunSplitItem(2, 1.0, "7:01", "7:01", "7:00", 147, 154, 206, 91),
            RunSplitItem(3, 1.0, "7:11", "7:11", "7:11", 150, 154, 201, 90),
            RunSplitItem(4, 1.0, "7:30", "7:30", "7:30", 147, 153, 192, 87),
            RunSplitItem(5, 1.0, "7:29", "7:29", "7:29", 148, 154, 196, 87),
            RunSplitItem(6, 1.0, "7:37", "7:37", "7:37", 151, 153, 190, 86),
            RunSplitItem(7, 0.07, "0:19", "4:15", "4:33", 152, 163, 218, 143),
            RunSplitItem(8, 0.08, "0:19", "3:51", "4:38", 164, 165, 252, 155),
            RunSplitItem(9, 0.08, "0:19", "3:52", "4:03", 152, 156, 232, 163),
            RunSplitItem(10, 0.08, "0:19", "3:51", "4:13", 150, 155, 221, 163),
            RunSplitItem(11, 0.08, "0:19", "3:53", "4:15", 156, 159, 183, 159)
        )

        fun defaultStages(): List<RunSplitItem> = listOf(
            RunSplitItem(1, 1.0, "7:08", "7:08", "7:00", 144, 149, 205, 94),
            RunSplitItem(2, 5.0, "36:48", "7:22", "7:21", 149, 154, 197, 88),
            RunSplitItem(3, 0.07, "0:19", "4:15", "4:33", 152, 164, 224, 143),
            RunSplitItem(4, 0.08, "0:19", "3:51", "4:38", 164, 168, 269, 155),
            RunSplitItem(5, 0.08, "0:19", "3:52", "4:03", 151, 158, 253, 163),
            RunSplitItem(6, 0.08, "0:19", "3:51", "4:13", 150, 159, 237, 163),
            RunSplitItem(7, 0.08, "0:19", "3:53", "4:15", 156, 161, 203, 159)
        )
    }
}

data class CorosWidgetPayload(
    val updatedLabel: String = "9:03 PM",
    val generatedAt: String = "2026-10-23 21:03:08",
    val athlete: AthleteProfile = AthleteProfile(),
    val weeklyMileage: WeeklyMileageData = WeeklyMileageData(),
    val trainingLoad: TrainingLoadData = TrainingLoadData(),
    val nextWorkout: NextWorkoutData = NextWorkoutData(),
    val zoneSpectrum: ZoneSpectrumData = ZoneSpectrumData(),
    val raceCountdown: RaceCountdownData = RaceCountdownData(),
    val lastRun: LastRunData = LastRunData(),
    val missedWorkout: MissedWorkoutData? = null,
    val recentRuns: List<LastRunData> = listOf(
        LastRunData(),
        LastRunData(
            name = "Easy 7K",
            date = "2026-10-21",
            dayLabel = "Wed, 21 Oct",
            distanceKm = 7.01,
            plannedKm = 7.0,
            adherencePct = 100,
            verdictBadge = "100% On Target (7K)",
            durationMinutes = 48.2,
            avgPace = "6:53 /km",
            avgHr = 149,
            maxHr = 160,
            cadence = 156,
            maxCadence = 166,
            powerWatts = 209,
            maxPowerWatts = 248,
            strideLengthCm = 94,
            trainingLoad = 93,
            aerobicTe = 3.0,
            anaerobicTe = 0.2,
            vo2Max = 51,
            stamina7d = 98.0,
            bestKmPace = "6:26 /km",
            elevGainM = 4,
            caloriesKcal = 695,
            physiologyNote = "Solid Aerobic Control: Avg HR 149 bpm stayed comfortably inside Zone 2 (<155 bpm).",
            hrZones = listOf(
                HrZoneBreakdown(1, "Z1–2 Aerobic Base", "<155 bpm", 2647, "44:07", 92),
                HrZoneBreakdown(2, "Z3 Aerobic Power", "156–163 bpm", 245, "4:05", 8),
                HrZoneBreakdown(3, "Z4 LTHR Threshold", "164–175 bpm", 0, "0:00", 0),
                HrZoneBreakdown(4, "Z5 VO2 Max / Anaer.", "176–182 bpm", 0, "0:00", 0),
                HrZoneBreakdown(5, "Z6 Max Effort", ">182 bpm", 0, "0:00", 0)
            ),
            splits = listOf(
                RunSplitItem(1, 1.0, "6:26", "6:26", "6:26", 140, 156, 217, 99),
                RunSplitItem(2, 1.0, "6:34", "6:34", "6:34", 150, 158, 222, 96),
                RunSplitItem(3, 1.0, "6:45", "6:45", "6:45", 147, 157, 215, 95),
                RunSplitItem(4, 1.0, "6:49", "6:49", "6:49", 151, 157, 214, 94),
                RunSplitItem(5, 1.0, "6:44", "6:44", "6:44", 152, 157, 211, 95),
                RunSplitItem(6, 1.0, "7:03", "7:03", "7:03", 152, 157, 207, 91),
                RunSplitItem(7, 1.0, "7:52", "7:52", "7:52", 147, 154, 187, 83)
            )
        ),
        LastRunData(
            name = "2x4K Threshold",
            date = "2026-10-20",
            dayLabel = "Tue, 20 Oct",
            distanceKm = 8.53,
            plannedKm = 10.0,
            adherencePct = 85,
            verdictBadge = "85% of 10K Target",
            durationMinutes = 49.7,
            avgPace = "5:21 /km",
            avgHr = 174,
            maxHr = 186,
            cadence = 160,
            maxCadence = 174,
            powerWatts = 269,
            maxPowerWatts = 317,
            strideLengthCm = 117,
            trainingLoad = 198,
            aerobicTe = 3.3,
            anaerobicTe = 3.7,
            vo2Max = 51,
            stamina7d = 108.0,
            bestKmPace = "4:58 /km",
            elevGainM = 10,
            caloriesKcal = 828,
            physiologyNote = "Quality Threshold Stimulus: Avg HR 174 bpm right near your 172 bpm LTHR ceiling (Load: 198 TL).",
            hrZones = listOf(
                HrZoneBreakdown(1, "Z1–2 Aerobic Base", "<155 bpm", 241, "4:01", 9),
                HrZoneBreakdown(2, "Z3 Aerobic Power", "156–163 bpm", 127, "2:07", 5),
                HrZoneBreakdown(3, "Z4 LTHR Threshold", "164–175 bpm", 576, "9:36", 21),
                HrZoneBreakdown(4, "Z5 VO2 Max / Anaer.", "176–182 bpm", 1517, "25:17", 55),
                HrZoneBreakdown(5, "Z6 Max Effort", ">182 bpm", 281, "4:41", 10)
            ),
            splits = listOf(
                RunSplitItem(1, 0.36, "2:15", "6:16", "6:16", 132, 154, 228, 104),
                RunSplitItem(2, 1.0, "5:13", "5:13", "5:13", 166, 161, 275, 119),
                RunSplitItem(3, 1.0, "5:12", "5:12", "5:12", 174, 162, 278, 119),
                RunSplitItem(4, 1.0, "5:12", "5:12", "5:12", 177, 163, 277, 118),
                RunSplitItem(5, 1.0, "4:59", "4:59", "4:59", 181, 165, 289, 121),
                RunSplitItem(6, 0.16, "1:59", "12:24", "12:24", 169, 114, 175, 71),
                RunSplitItem(7, 1.0, "5:13", "5:13", "5:13", 171, 162, 270, 118),
                RunSplitItem(8, 1.0, "5:14", "5:14", "5:14", 180, 162, 270, 118),
                RunSplitItem(9, 1.0, "5:16", "5:16", "5:16", 181, 165, 273, 115),
                RunSplitItem(10, 1.0, "5:03", "5:03", "5:03", 182, 166, 280, 119)
            )
        )
    )
)

enum class ExpressiveTileType(
    val title: String,
    val subtitle: String,
    val gridSpan: String,
    val aspectRatio: Float,
    val isSquare: Boolean
) {
    WEEKLY_MILEAGE(
        "Mileage Bars",
        "4 stacked capsule bars ('Device Info' style)",
        "2x2",
        1.0f,
        true
    ),
    TRAINING_LOAD(
        "EvoLab Ring",
        "Two-tone donut ring ('Battery Ring' style)",
        "2x2",
        1.0f,
        true
    ),
    NEXT_WORKOUT(
        "Next Run Pods",
        "Circle + squircle pods ('Clock Battery' style)",
        "2x2",
        1.0f,
        true
    ),
    ZONE_SPECTRUM(
        "80/20 Split Shapes",
        "Top pill bar + geometric icons ('Battery Icons' style)",
        "2x2",
        1.0f,
        true
    ),
    RACE_COUNTDOWN(
        "Pune HM Scallop",
        "12-lobe flower cookie ('UV Index / Pressure' style)",
        "2x2",
        1.0f,
        true
    ),
    COMMAND_CENTER(
        "Liquid Progress",
        "Royal liquid fill + scalloped badge ('Battery Progress' style)",
        "2x2",
        1.0f,
        false
    )
}

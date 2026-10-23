<div align="center">

# CorosWidgets

<img src="assets/icon.png" width="128" height="128" alt="CorosWidgets App Icon" />

### Material 3 Training Hub & Home-Screen Widgets for COROS Runners

[![GitHub Release](https://img.shields.io/github/v/release/coderang-gk/CorosWidgets?style=flat-square&color=blue)](https://github.com/coderang-gk/CorosWidgets/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B%20(API%2031%2B)-green.svg?style=flat-square)](https://developer.android.com)

An open-source Android companion app and Material 3 widget suite for runners using COROS watches, backed by a lightweight Python sync service.

Connects directly to the **COROS Training Hub API** (`teamapi.coros.com`) and your scheduled training plan on **Google Calendar** to provide post-run physiological analysis, active-days mileage tracking, and 12 Material You home-screen widgets.

</div>

---

## Architecture

```
[ COROS Training Hub API ]          [ Google Calendar ]
       │                                     │
       │ (Activities, EvoLab, FIT)           │ (Planned Workouts)
       ▼                                     ▼
┌─────────────────────────────────────────────────────────┐
│            Python Widget Server (:8095)                 │
│  - MD5 Session Auth & GZIP Stream Parser                │
│  - Physiological Analyzer (LTHR, Drift, Cadence, TE)    │
│  - Optional Gemini / OpenAI Generative Commentary       │
│  - Missed-Workout Resolution (Google Calendar Patch)    │
└───────────────────────────┬─────────────────────────────┘
                            │ JSON over HTTP (LAN / Tailscale)
                            ▼
┌─────────────────────────────────────────────────────────┐
│                   Android Application                   │
│  - Training Hub: Post-run verdict, tips, active days    │
│  - Missed Session Resolution Card (1-tap calendar sync) │
│  - 12 Material You Widgets (androidx.graphics.shapes)   │
│  - Native Post-Run Android System Notifications         │
└─────────────────────────────────────────────────────────┘
```

---

## Key Capabilities

### 1. Training Hub & Post-Run Analysis
- **Execution & Physiological Verdict**: Cross-references completed COROS activity streams against planned distance and pace targets, analyzing heart-rate drift relative to your Lactate Threshold Heart Rate (`LTHR`) and Aerobic / Anaerobic Training Effect (`TE`).
- **Biomechanical Watchpoints**: Evaluates cadence turnover against stride length to identify ground contact time issues during aerobic runs and power output surges during intervals or strides.
- **Actionable Next-Workout Tips**: Specific pacing caps, fueling intervals, and heart-rate ceilings tailored to the next upcoming workout on your calendar.
- **Active-Days Mileage Tracker**: Displays progress bars solely for days with scheduled or completed runs, omitting rest days.
- **Missed-Workout Resolution**: Detects unscheduled training gaps and offers context-aware resolutions (e.g. drop the session to protect a key long run, or shift to recovery) that patch Google Calendar directly via the API.
- **Native Android Notifications**: Emits a local Android notification upon activity synchronization with an executive verdict and direct deep-link into the run analysis sheet.

### 2. Material 3 Home-Screen Widgets
The suite includes 12 widget renderers that dynamically query the device's system palette (`system_accent1_*`, `system_accent2_*`, and `system_accent3_*`) for native color integration across light and dark modes:

| Style | Name | Category | Primary Metrics Displayed |
|---|---|---|---|
| 0 | Capsule Stack | Cards | Weekly Mileage, Acute Load, Aerobic Split, Race Countdown |
| 1 | EvoLab Ring | Sculptural | Acute Training Load (TL) and Readiness % |
| 2 | Contrast Pods | Sculptural | Actual vs. Planned Volume Pods with Target Pill |
| 3 | 80/20 Split Bar | Cards | Segmented Aerobic Base vs. Threshold HR Balance |
| 4 | Cookie-12 Seal | Sculptural | 16-lobe Scallop Race Countdown and Threshold Pace |
| 5 | Horizon Split | Cards | Progress Horizon with Cookie-9 Badge |
| 6 | Nested Plan | Cards | Asymmetric 3-Session Recent and Upcoming Stack |
| 7 | Slanted Pill | Sculptural | Diagonal Stadium Capsule with Completed Distance & % |
| 8 | Tonal Ellipses | Sculptural | Concentric Oval Stack for Threshold and Goal Pace |
| 9 | Flower Dial | Sculptural | 12-lobe Flower Dial with 7-Day Training Status Dots |
| 10 | Display Split | Cards | Condensed Typography Countdown and Training Week |
| 11 | EvoLab Splits | Cards | 4-Row Metric Summary Table |

---

## Repository Structure

```
CorosWidgets/
├── backend/
│   ├── .env.example         # Configuration template
│   ├── coros_client.py      # COROS API client (session auth & activity streams)
│   ├── gcal_client.py       # Google Calendar OAuth client & event patcher
│   └── widget_server.py     # HTTP server serving unified widget and coach payloads
└── android/
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── java/com/gaurang/coroswidgets/
        │   ├── MainActivity.kt               # Main application and bottom sheet views
        │   ├── data/
        │   │   ├── WidgetDataModels.kt       # Domain models
        │   │   └── WidgetRepository.kt       # HTTP client and local persistence
        │   ├── notifications/
        │   │   └── CorosNotificationHelper.kt# Local notification channels and dispatch
        │   ├── ui/
        │   │   ├── ExpressiveTheme.kt        # System dynamic color mapping
        │   │   └── ExpressiveWidgetRenderer.kt # Canvas renderers for all 12 widget styles
        │   └── widgets/
        │       └── CorosWidgetProviders.kt   # AppWidgetProvider implementations
        └── res/                              # Adaptive icons, layouts, and XML metadata
```

---

## Getting Started

### Prerequisites
- Python 3.9+
- Android Studio Ladybug / Jellyfish or Android SDK CLI (`compileSdk 36`, `minSdk 31`)
- A COROS user account
- *(Optional)* Google Calendar API credentials (`credentials.json`) if syncing structured training plans

---

### Step 1: Backend Deployment

1. Clone the repository:
   ```bash
   git clone https://github.com/coderang-gk/CorosWidgets.git
   cd CorosWidgets/backend
   ```

2. Copy and populate the environment configuration:
   ```bash
   cp .env.example .env
   ```

3. Configure your credentials in `.env`:
   ```ini
   COROS_EMAIL=runner@example.com
   COROS_PASSWORD=your_password
   COROS_REGION=teamapi.coros.com

   # Goal race parameters
   RACE_NAME=PUNE HALF MARATHON
   RACE_DATE=2026-12-13
   PLAN_START_DATE=2026-10-05
   TOTAL_PLAN_WEEKS=10
   GOAL_TIME=1:45:00
   GOAL_PACE=4:58 /km
   GOAL_PACE_SEC=298

   # Port
   WIDGET_SERVER_PORT=8095
   ```

4. *(Optional LLM Configuration)*:
   The backend includes a zero-cost, built-in physiological analyzer that evaluates threshold drift, training effect, and cadence mechanics locally without external dependencies.
   If you want generative natural-language commentary, provide an API key. Free-tier keys from Google AI Studio (`gemini-2.5-flash`) are fully supported:
   ```ini
   GEMINI_API_KEY=your_google_ai_studio_key
   GEMINI_MODEL=gemini-2.5-flash
   ```

5. *(Optional Google Calendar Setup)*:
   If tracking a calendar plan, place your Google Cloud OAuth client file as `backend/credentials.json` and set:
   ```ini
   GCAL_CALENDAR_NAME=Pune Half Marathon 2026 Training
   ```

6. Run the server:
   ```bash
   python3 widget_server.py
   ```
   For persistent background operation, configure a systemd service or cron `@reboot` task.

---

### Step 2: Android Build and Installation

1. Connect your Android device with USB debugging enabled.
2. Build and install the debug APK:
   ```bash
   cd ../android
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Open the app on your device.
4. Tap the **Settings** icon in the top-right corner and enter your backend URL:
   ```text
   http://<SERVER_IP>:8095/api/v1/widget-data
   ```
   *(Tailscale IPs are supported for seamless remote syncing outside your home network).*

---

## API Specifications

The server exposes the following endpoints on port `8095`:

### `GET /api/v1/widget-data`
Returns the consolidated training payload, including weekly mileage totals, acute load ratios, readiness scores, next workout targets, last run physiological breakdown, and missed workout alerts.

**Query Parameters:**
- `refresh=1`: Bypasses the 15-minute disk cache and forces a live sync with COROS and Google Calendar.

### `POST /api/v1/resolve-missed-workout`
Resolves an uncompleted workout on Google Calendar.

**Request Body:**
```json
{
  "event_id": "google_calendar_event_id",
  "action": "DROP" | "SHIFT",
  "target_date": "YYYY-MM-DD"
}
```

- `DROP`: Prefixes the calendar event summary with `[SKIPPED]` to remove it from active volume tracking.
- `SHIFT`: Moves the calendar event to the specified `target_date`.

### `GET /health`
Returns service status and server timestamp:
```json
{
  "status": "healthy",
  "timestamp": 1792784500.12
}
```

---

## License

This project is licensed under the MIT License.

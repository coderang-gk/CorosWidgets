# COROS AI Run Coach & Material 3 Android Widgets (`CorosWidgets`)

An open-source **AI Run Coach**, **Training Hub Android App**, and **12 Material 3 Home Screen Widgets** powered directly by the **COROS Training Hub API** (`teamapi.coros.com`) and optional **Google Calendar** training plan sync.

Built for runners who want more than raw stats—combining **post-run AI coaching verdicts, physiological drift & form analysis, and actionable tips for your next workout** with stock Pixel / Material You dynamic color widgets on your Android home screen.

---

## ✨ Key Features

### 1. 🧠 AI Run Coach & Training Hub (Android App)
- **Zero-Cost Built-In Coach + Optional Free Gemini / OpenAI LLM**: Works out of the box with **zero paid subscriptions or API keys** using a built-in COROS EvoLab Physiological Analyzer (evaluating your LTHR, Zone 2 ceiling, cardiac drift, stride length, running power, cadence, and plan adherence). Want generative LLM coaching? Drop in a **free Google AI Studio `GEMINI_API_KEY`** (or `OPENAI_API_KEY`) in `.env` and the server automatically caches LLM analysis per run.
- **Executive Post-Run Verdict**: Automatically cross-references your completed COROS workout against your scheduled training plan (distance adherence, Zone 2 ceiling control vs. LTHR threshold stimulus, aerobic vs. anaerobic training effect).
- **Deep AI Physiological & Form Insights**: Analyzes heart-rate drift, interval/stride power & stride-length mechanics, and biomechanical watchpoints (such as easy-day cadence turnover).
- **Actionable Next-Run Tips**: Concrete pacing caps, progression rules, cadence cues, and fueling reminders tailored specifically to your next scheduled workout.
- **Training-Days-Only Mileage Tracker**: Tracks your weekly mileage completion percentage and displays individual progress bars **only for your active/scheduled training days** (automatically hiding rest days).

### 2. 🎨 12 Material 3 Home Screen Widgets (`Widgets` Tab)
All 12 widgets dynamically adapt to your device's **Material You (Monet) wallpaper palette** and **System Dark/Light Theme** using official `androidx.graphics:graphics-shapes` geometry:

| # | Widget Name | Style Category | Description |
|---|---|---|---|
| **1** | **Capsule Stack** | Data Cards | 4 stacked capsule progress bars for Weekly Mileage, Acute Load, Z2 Split, and Race Countdown |
| **2** | **EvoLab Ring** | Sculptural | Two-tone concentric donut gauge showing Acute Training Load (`TL`) and Readiness `%` |
| **3** | **Contrast Pods** | Sculptural | Asymmetric Circle + Squircle pods comparing completed vs. planned weekly volume |
| **4** | **80/20 Split Bar** | Data Cards | Two-tone segmented pill bar tracking your 80/20 Aerobic Base vs. Threshold HR balance |
| **5** | **Cookie-12 Seal** | Sculptural | Full-bleed 16-lobe Material 3 scalloped cookie seal counting down days to race day |
| **6** | **Horizon Split** | Data Cards | Liquid progress horizon card with a 9-lobe Cookie badge for weekly volume |
| **7** | **Nested Plan** | Data Cards | Asymmetric 3-run workout stack showing recent completed sessions and your next workout |
| **8** | **Slanted Pill** | Sculptural | Broad `+45°` Pixel Weather-inspired diagonal stadium capsule with mileage & completion badge |
| **9** | **Tonal Ellipses** | Sculptural | Layered concentric oval stack comparing Current EvoLab LT Pace vs. Goal Race Pace |
| **10** | **Flower Dial** | Sculptural | 12-lobe flower cookie dial with 7-day training status dots and Readiness `%` |
| **11** | **Display Split** | Data Cards | Tall condensed split-typography race countdown with current training week badge |
| **12** | **EvoLab Splits** | Data Cards | Clean 4-row summary table for Last Run, Next Run, Threshold Pace/LTHR, and Weekly Volume |

---

## 🏗️ Project Structure

```text
CorosWidgets/
├── backend/
│   ├── .env.example         # Template for COROS credentials, goal race & optional Google Calendar
│   ├── coros_client.py      # COROS Training Hub API client (MD5 auth, activity list & GZIP detail parser)
│   ├── gcal_client.py       # Optional Google Calendar training plan parser
│   └── widget_server.py     # Lightweight Python HTTP server (:8095) generating AI Coach analysis & widget JSON
└── android/
    └── app/src/main/java/com/gaurang/coroswidgets/
        ├── MainActivity.kt                  # Two-section app (Training Hub + Widgets gallery) & AI Coach BottomSheet
        ├── data/WidgetDataModels.kt         # Data models for athlete profile, runs, AI analysis & widgets
        ├── data/WidgetRepository.kt         # HTTP sync client with automatic LAN/Tailscale fallback & offline cache
        ├── ui/ExpressiveTheme.kt            # Dynamic Material You system color extraction (Dark & Light)
        ├── ui/ExpressiveWidgetRenderer.kt   # High-DPI Canvas + RoundedPolygon renderer for all 12 widget designs
        └── widgets/CorosWidgetProviders.kt  # Android AppWidgetProvider receivers & background sync helper
```

---

## 🚀 Quick Start Guide (Replicate with Your COROS Account)

### Step 1: Clone the Repository
```bash
git clone https://github.com/coderang-gk/CorosWidgets.git
cd CorosWidgets
```

### Step 2: Configure Your COROS Account & Goal Race
Copy `backend/.env.example` to `backend/.env` and fill in your COROS Training Hub email, password, and target race details:
```bash
cp backend/.env.example backend/.env
```

Edit `backend/.env`:
```ini
COROS_EMAIL=your_coros_email@example.com
COROS_PASSWORD=your_coros_password
COROS_REGION=teamapi.coros.com

# Customize your goal race
RACE_NAME=PUNE HALF MARATHON
RACE_DATE=2026-12-13
PLAN_START_DATE=2026-10-05
TOTAL_PLAN_WEEKS=10
GOAL_TIME=1:45:00
GOAL_PACE=4:58 /km
GOAL_PACE_SEC=298

# Optional: Free Gemini API key (https://aistudio.google.com/apikey) or OpenAI key
# Leave blank to use the built-in zero-cost COROS EvoLab Physiological Coach!
GEMINI_API_KEY=
```

*(Optional)* If you keep your training plan on **Google Calendar** (e.g. events like `W3 Fri — Easy 7K + Strides` or `Long Run 20K`), place your Google Calendar API `credentials.json` and `token.json` inside `backend/` and set `GCAL_CALENDAR_NAME` in `.env`. If omitted, the server works standalone using your COROS activities!

### Step 3: Start the Backend Server
The backend uses Python's standard library (plus `google-api-python-client` only if using Google Calendar):
```bash
cd backend
python3 widget_server.py
```
The server listens on `http://0.0.0.0:8095`:
- `GET /api/v1/widget-data` — Returns the unified JSON payload (auto-refreshes every 15 minutes).
- `GET /api/v1/refresh` — Forces a live sync with COROS Training Hub & Google Calendar.

### Step 4: Build & Install the Android App
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
1. Open **COROS Widgets** on your phone.
2. Tap the **Settings (Tune)** icon in the top-right corner and enter your backend server URL (`http://<YOUR_SERVER_LAN_OR_TAILSCALE_IP>:8095/api/v1/widget-data`).
3. Browse your **Training Hub** for AI Coach post-run analysis, or switch to the **Widgets** tab and tap any of the 12 designs to pin it to your home screen!

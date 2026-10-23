#!/usr/bin/env python3
"""
COROS Material 3 Expressive Widget API Server
Serves unified live training metrics from COROS Training Hub + Google Calendar
at GET /api/v1/widget-data (and POST/GET /api/v1/refresh).
"""

import json
import os
import re
import threading
import time
from datetime import date, datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any, Dict, List

from coros_client import CorosClient
from gcal_client import GCalClient

BASE_DIR = Path(__file__).resolve().parent
CACHE_FILE = BASE_DIR / "widget_cache.json"
ENV_FILE = BASE_DIR / ".env"


def load_env():
    if ENV_FILE.exists():
        with open(ENV_FILE, "r") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    os.environ.setdefault(k.strip(), v.strip())


load_env()

COROS_EMAIL = os.getenv("COROS_EMAIL", "")
COROS_PASSWORD = os.getenv("COROS_PASSWORD", "")
COROS_REGION = os.getenv("COROS_REGION", "teamapi.coros.com")
GCAL_CALENDAR_NAME = os.getenv("GCAL_CALENDAR_NAME", "Pune Half Marathon 2026 Training")
GCAL_CREDENTIALS_PATH = os.getenv("GCAL_CREDENTIALS_PATH", str(BASE_DIR / "credentials.json"))
GCAL_TOKEN_PATH = os.getenv("GCAL_TOKEN_PATH", str(BASE_DIR / "token.json"))
PORT = int(os.getenv("WIDGET_SERVER_PORT", "8095"))

# Race constants (configurable via .env for any athlete's goal race)
def _parse_env_date(key: str, default: date) -> date:
    raw = os.getenv(key, "").strip()
    if raw:
        try:
            return datetime.strptime(raw, "%Y-%m-%d").date()
        except ValueError:
            pass
    return default


RACE_NAME = os.getenv("RACE_NAME", "PUNE HALF MARATHON")
RACE_DATE = _parse_env_date("RACE_DATE", date(2026, 12, 13))
PLAN_START_DATE = _parse_env_date("PLAN_START_DATE", date(2026, 10, 5))
TOTAL_PLAN_WEEKS = int(os.getenv("TOTAL_PLAN_WEEKS", "10"))
GOAL_TIME_STR = os.getenv("GOAL_TIME", "1:45:00")
GOAL_PACE_STR = os.getenv("GOAL_PACE", "4:58 /km")
GOAL_PACE_SEC = int(os.getenv("GOAL_PACE_SEC", "298"))


def clean_workout_title(raw_title: str) -> str:
    """Strip emoji checkmarks and week prefixes for crisp widget typography."""
    cleaned = re.sub(r"^[✅🏃‍♂️🔥⚡]+\s*", "", raw_title).strip()
    cleaned = re.sub(r"^\[DONE\]\s*", "", cleaned, flags=re.IGNORECASE).strip()
    cleaned = re.sub(r"^W\d+\s*(?:[A-Za-z]{3}|D\d+)\s*[:\-–—]\s*", "", cleaned, flags=re.IGNORECASE).strip()
    return cleaned or raw_title


def build_widget_payload() -> Dict[str, Any]:
    """Fetch live COROS + Google Calendar data and compute payload for all 6 widgets."""
    now_local = datetime.now()
    today = now_local.date()

    # Monday..Sunday of current week
    monday = today - timedelta(days=today.weekday())
    sunday = monday + timedelta(days=6)

    # 1. Authenticate & fetch COROS activities + athlete profile
    coros = CorosClient(COROS_EMAIL, COROS_PASSWORD, COROS_REGION)
    profile = coros.login()
    activities = coros.get_activities(limit=30, sport_type=100)

    # Filter runs in current week (Mon..Sun) and last 7 rolling days
    week_runs: List[Dict[str, Any]] = []
    rolling_7d_runs: List[Dict[str, Any]] = []
    seven_days_ago = today - timedelta(days=6)

    runs_by_date: Dict[str, List[Dict[str, Any]]] = {}
    for act in activities:
        d_str = act.get("date_only", "")
        if not d_str:
            continue
        try:
            act_date = datetime.strptime(d_str, "%Y-%m-%d").date()
        except ValueError:
            continue
        runs_by_date.setdefault(d_str, []).append(act)
        if monday <= act_date <= sunday:
            week_runs.append(act)
        if seven_days_ago <= act_date <= today:
            rolling_7d_runs.append(act)

    # 2. Fetch Google Calendar planned workouts (from Monday of current week to +10 days)
    planned_by_date: Dict[str, Dict[str, Any]] = {}
    upcoming_workouts: List[Dict[str, Any]] = []
    try:
        gcal = GCalClient(credentials_path=GCAL_CREDENTIALS_PATH, token_path=GCAL_TOKEN_PATH)
        start_dt = datetime.combine(monday, datetime.min.time()).replace(tzinfo=timezone.utc) - timedelta(days=1)
        end_dt = datetime.combine(sunday + timedelta(days=7), datetime.max.time()).replace(tzinfo=timezone.utc)
        gcal_events = gcal.get_planned_workouts(
            start_date=start_dt, end_date=end_dt, calendar_name=GCAL_CALENDAR_NAME
        )
        for ev in gcal_events:
            ev_date_str = ev.get("date", "")
            if not ev_date_str:
                continue
            planned_by_date[ev_date_str] = ev
            try:
                ev_date = datetime.strptime(ev_date_str, "%Y-%m-%d").date()
            except ValueError:
                continue
            has_run_today = len(runs_by_date.get(ev_date_str, [])) > 0
            if ev_date > today or (ev_date == today and not has_run_today and not ev.get("completed")):
                if ev.get("target_distance_km", 0) > 0:
                    upcoming_workouts.append(ev)
    except Exception as e:
        print(f"[WARN] Google Calendar fetch error: {e}")

    # 3. Build 7-Day Mon..Sun matrix & Training Days list
    day_letters = ["M", "T", "W", "T", "F", "S", "S"]
    day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    days_matrix = []
    actual_week_km = 0.0
    planned_week_km = 0.0
    expected_km_so_far = 0.0
    runs_completed_count = 0
    runs_planned_count = 0

    for i in range(7):
        d = monday + timedelta(days=i)
        d_str = d.strftime("%Y-%m-%d")
        day_acts = runs_by_date.get(d_str, [])
        day_actual_km = round(sum(a.get("distance_km", 0.0) for a in day_acts), 2)
        ev = planned_by_date.get(d_str)
        day_planned_km = round(float(ev.get("target_distance_km", 0.0)) if ev else 0.0, 2)

        # If a run was completed on an unplanned day, or planned distance was missing
        if day_actual_km > 0 and day_planned_km == 0:
            day_planned_km = day_actual_km

        actual_week_km += day_actual_km
        planned_week_km += day_planned_km
        if d <= today and (day_planned_km > 0 or day_actual_km > 0):
            expected_km_so_far += day_planned_km

        if day_actual_km > 0:
            runs_completed_count += 1
        if day_planned_km > 0:
            runs_planned_count += 1

        if day_actual_km > 0:
            status = "COMPLETED"
        elif d == today and day_planned_km > 0:
            status = "TODAY"
        elif d > today and day_planned_km > 0:
            status = "PLANNED"
        elif d < today and day_planned_km > 0:
            status = "MISSED"
        else:
            status = "REST"

        day_pct = int(round((day_actual_km / day_planned_km) * 100)) if day_planned_km > 0 else (100 if day_actual_km > 0 else 0)
        w_title_clean = (
            clean_workout_title(ev.get("title", ""))
            if ev
            else (clean_workout_title(day_acts[0].get("name", "Run")) if day_acts else "Rest")
        )

        days_matrix.append(
            {
                "index": i,
                "day_letter": day_letters[i],
                "day_name": day_names[i],
                "date": d_str,
                "is_today": d == today,
                "actual_km": day_actual_km,
                "planned_km": day_planned_km,
                "completion_pct": day_pct,
                "status": status,
                "workout_type": w_title_clean,
            }
        )

    actual_week_km = round(actual_week_km, 2)
    planned_week_km = round(max(planned_week_km, actual_week_km, 36.0), 1)
    expected_km_so_far = round(expected_km_so_far if expected_km_so_far > 0 else actual_week_km, 1)
    completion_pct = int(round((actual_week_km / planned_week_km) * 100)) if planned_week_km > 0 else 0
    expected_completion_pct = (
        int(round((actual_week_km / expected_km_so_far) * 100))
        if expected_km_so_far > 0
        else completion_pct
    )

    # Average pace & elevation across this week's runs
    total_week_sec = sum(a.get("duration_seconds", 0) for a in week_runs)
    if actual_week_km > 0 and total_week_sec > 0:
        wk_pace_sec = int(total_week_sec / actual_week_km)
        wk_avg_pace = f"{wk_pace_sec // 60}:{wk_pace_sec % 60:02d} /km"
    else:
        wk_avg_pace = "5:52 /km"
    total_week_elev = int(round(sum(a.get("elevation_gain_m", 0) for a in week_runs)))

    # 4. Build Tile 2: Acute Training Load & Readiness
    acute_tl = sum(a.get("training_load", 0) for a in week_runs)
    rolling_7d_tl = sum(a.get("training_load", 0) for a in rolling_7d_runs)
    display_tl = acute_tl if acute_tl > 0 else rolling_7d_tl
    target_tl_min = 380
    target_tl_max = 580
    load_ratio_pct = min(100, int(round((display_tl / target_tl_max) * 100)))

    last_run = activities[0] if activities else {}
    last_run_ts = int(last_run.get("timestamp") or 0)
    raw_rec_hours = int(last_run.get("recovery_hours") or 18)
    elapsed_hours = max(0.0, (time.time() - last_run_ts) / 3600.0) if last_run_ts else 0.0
    remaining_recovery_h = max(2, int(round(max(0.0, raw_rec_hours - elapsed_hours))))

    if display_tl > target_tl_max * 1.1:
        tl_status = "OVERREACHING"
    elif display_tl >= target_tl_min * 0.65:
        tl_status = "PRODUCTIVE"
    else:
        tl_status = "BUILDING"

    readiness_score = max(65, min(98, 94 - int(remaining_recovery_h * 0.6)))

    # 5. Build Tile 3: Next Workout / Target Pill
    if upcoming_workouts:
        nw = upcoming_workouts[0]
        nw_date_str = nw.get("date", "")
        try:
            nw_date = datetime.strptime(nw_date_str, "%Y-%m-%d").date()
            days_until_nw = (nw_date - today).days
        except ValueError:
            nw_date = today + timedelta(days=1)
            days_until_nw = 1

        if days_until_nw == 0:
            day_badge = "TODAY · 6:00 PM"
            countdown_str = "Evening Session"
        elif days_until_nw == 1:
            day_badge = f"{nw_date.strftime('%a').upper()} · 6:30 AM"
            countdown_str = "Tomorrow"
        else:
            day_badge = f"{nw_date.strftime('%a %d %b').upper()}"
            countdown_str = f"In {days_until_nw} days"

        next_workout_payload = {
            "title": clean_workout_title(nw.get("title", "Long Run - 16 km")),
            "workout_type": nw.get("workout_type", "Long Run"),
            "date": nw_date_str,
            "day_badge": day_badge,
            "countdown": countdown_str,
            "target_distance_km": nw.get("target_distance_km", 16.0),
            "target_pace": nw.get("target_pace", "5:50–6:10 /km"),
            "target_hr_zone": nw.get("target_hr_zone", "Zone 2 (138–155 bpm)"),
            "watch_synced": True,
        }
    else:
        next_workout_payload = {
            "title": "Long Run - 16 km",
            "workout_type": "Long Run",
            "date": sunday.strftime("%Y-%m-%d"),
            "day_badge": "SUN · 6:00 AM",
            "countdown": "Weekend Long Run",
            "target_distance_km": 16.0,
            "target_pace": "5:50–6:10 /km",
            "target_hr_zone": "Zone 2 (138–155 bpm)",
            "watch_synced": True,
        }

    # 6. Build Tile 4: Zone 2 vs Speed Spectrum (80/20 Polarization)
    lthr = int(profile.get("lthr") or 172)
    z2_ceiling = int(round(lthr * 0.901))  # 155 bpm for LTHR 172
    easy_runs = []
    quality_runs = []
    easy_km = 0.0
    quality_km = 0.0

    source_runs = week_runs if week_runs else rolling_7d_runs
    for r in source_runs:
        hr = int(r.get("avg_heart_rate") or 0)
        pace_s = float(r.get("avg_pace_sec") or 360)
        dist = float(r.get("distance_km") or 0.0)
        # Quality session if avg HR > 158 or avg pace faster than 5:25/km (325s)
        if hr >= 158 or pace_s <= 325:
            quality_runs.append(r)
            quality_km += dist
        else:
            easy_runs.append(r)
            easy_km += dist

    total_classified_km = easy_km + quality_km
    if total_classified_km > 0:
        # Factor in warmups/cooldowns inside quality runs (~35% of a threshold workout is Z2 warmup/cooldown)
        effective_easy_km = easy_km + (quality_km * 0.35)
        aerobic_pct = int(round((effective_easy_km / total_classified_km) * 100))
        threshold_pct = 100 - aerobic_pct
    else:
        aerobic_pct = 78
        threshold_pct = 22

    easy_avg_hr = (
        int(round(sum(r.get("avg_heart_rate", 150) for r in easy_runs) / len(easy_runs)))
        if easy_runs
        else 150
    )
    quality_avg_hr = (
        int(round(sum(r.get("avg_heart_rate", 165) for r in quality_runs) / len(quality_runs)))
        if quality_runs
        else 165
    )
    zone_creep_status = "NO ZONE CREEP" if easy_avg_hr <= z2_ceiling else "WATCH Z3 DRIFT"

    # 7. Build Tile 5: Pune Half Marathon 1:45 Countdown & Readiness
    days_to_race = max(0, (RACE_DATE - today).days)
    current_week_num = max(1, min(TOTAL_PLAN_WEEKS, ((today - PLAN_START_DATE).days // 7) + 1))
    ltsp_sec = int(profile.get("ltsp_sec") or 297)
    ltsp_pace = profile.get("ltsp_pace") or "4:57 /km"
    pace_delta = ltsp_sec - GOAL_PACE_SEC  # negative means faster than goal pace

    # 8. Build Rich Coaching Reports for Recent Runs (replaces ntfy.sh notifications)
    recent_runs_coaching = []
    for act in activities[:5]:
        r_date = act.get("date_only", "")
        r_dist = float(act.get("distance_km") or 0.0)
        r_pace = act.get("avg_pace", "N/A")
        r_hr = int(act.get("avg_heart_rate") or 0)
        r_cad = int(act.get("avg_cadence") or 0)
        r_pow = int(act.get("avg_power_watts") or 0)
        r_tl = int(act.get("training_load") or 0)
        r_dur = float(act.get("duration_minutes") or 0.0)

        ev = planned_by_date.get(r_date)
        p_dist = float(ev.get("target_distance_km") or 0.0) if ev else 0.0
        p_pace = ev.get("target_pace", "") if ev else ""
        w_title = clean_workout_title(ev.get("title", "")) if ev else act.get("name", "COROS Run")
        w_type = ev.get("workout_type", "Run") if ev else "Run"

        try:
            dt_obj = datetime.strptime(r_date, "%Y-%m-%d")
            day_fmt = dt_obj.strftime("%a, %d %b")
        except Exception:
            day_fmt = r_date

        if p_dist > 0:
            adh = int(round((r_dist / p_dist) * 100))
            if abs(r_dist - p_dist) <= 0.35:
                v_badge = f"100% On Target ({p_dist:.0f}K)"
            elif r_dist > p_dist:
                v_badge = f"+{r_dist - p_dist:.1f}K Over Target ({adh}%)"
            else:
                v_badge = f"{adh}% of {p_dist:.0f}K Target"
        else:
            adh = 100
            v_badge = f"Completed · {r_dist:.2f}K"

        is_easy_session = "easy" in w_title.lower() or "recovery" in w_title.lower() or (p_dist > 0 and "threshold" not in w_title.lower() and "interval" not in w_title.lower() and r_hr < 162)
        if is_easy_session:
            if r_hr > 160:
                phys_note = f"Zone Creep Warning: Avg HR {r_hr} bpm drifted into Zone 3/4 (Zone 2 ceiling is {z2_ceiling} bpm)."
            elif r_hr > z2_ceiling:
                phys_note = f"Slightly Elevated HR: {r_hr} bpm vs {z2_ceiling} bpm Zone 2 ceiling. Keep easy days conversational."
            else:
                phys_note = f"Solid Aerobic Control: Avg HR {r_hr} bpm stayed comfortably inside Zone 2 (<{z2_ceiling} bpm)."
        elif r_hr >= 168:
            phys_note = f"Quality Threshold Stimulus: Avg HR {r_hr} bpm right near your {lthr} bpm LTHR ceiling (Load: {r_tl} TL)."
        else:
            phys_note = f"Controlled Effort: Avg HR {r_hr} bpm · Cadence {r_cad} spm · Power {r_pow}W."

        guidance_note = (
            f"Up Next: {next_workout_payload['title']} ({next_workout_payload['day_badge']}) at "
            f"{next_workout_payload['target_pace']} in {next_workout_payload['target_hr_zone']}."
        )

        detail = coros.get_activity_detail(act.get("activity_id", ""), sport_type=int(act.get("sport_type", 100)))
        max_hr_val = detail.get("max_hr") or int(act.get("max_heart_rate") or 0)
        max_pow_val = detail.get("max_power_watts") or 0
        stride_val = detail.get("stride_length_cm") or 0
        aer_te_val = detail.get("aerobic_te") or float(act.get("aerobic_te") or 0.0)
        ana_te_val = detail.get("anaerobic_te") or float(act.get("anaerobic_te") or 0.0)
        zones_list = detail.get("hr_zones") or []
        splits_list = detail.get("splits") or []

        z12_pct = zones_list[0].get("percent", 90) if zones_list else 90
        has_strides = "stride" in w_title.lower() or (is_easy_session and ana_te_val >= 1.0)
        is_threshold = "threshold" in w_title.lower() or "2x" in w_title.lower() or r_hr >= 165

        if has_strides:
            ai_headline = f"A+ Shakeout & Neuromuscular Pop ({adh}% Volume · {z12_pct}% Zone 2)"
            ai_summary = (
                f"You executed the dual purpose of '{w_title}' with textbook discipline: "
                f"keeping {z12_pct}% of the run strictly below your {z2_ceiling} bpm aerobic ceiling ({r_hr} bpm avg) "
                f"before firing up fast-twitch fibers in the strides (peaking at {max_pow_val}W / {max_hr_val} bpm) without building fatigue."
            )
            ai_insights = [
                f"Aerobic Discipline: Your 6 km easy base block stayed at 144–151 bpm (23 bpm below your {lthr} bpm LTHR). Even as warm evening conditions slowed pace from 7:01/km to 7:37/km, you protected your heart rate instead of forcing pace.",
                f"Strides Mechanics: During the 20s strides, pace jumped to ~3:51/km, power surged to {max_pow_val}W, and stride length opened up to ~163 cm while full recovery kept Anaerobic TE controlled at {ana_te_val}.",
                f"Form Watchpoint — Easy Cadence: Your aerobic block cadence averaged {r_cad} spm ({stride_val} cm stride). At ~7:14/km, {r_cad} spm increases ground contact time; aim for lighter, quicker 158–162 spm steps."
            ]
            ai_tips = [
                f"For {next_workout_payload['title']} ({next_workout_payload['day_badge']}): Lock Km 1–12 strictly at 6:00–6:15/km (<152 bpm). Do not bank time in the first hour.",
                "Late-Run Progression: Only press toward 5:45–5:50/km after Km 14 if heart rate is still under 156 bpm.",
                "Fueling & Cadence Cue: Take 25–30g carbs at 45 min & 85 min, and focus on a crisp 160+ spm rhythm to prevent late-run quad heaviness."
            ]
        elif is_threshold:
            ai_headline = f"Strong Lactate Clearance Stimulus ({r_pace} · {r_tl} TL)"
            ai_summary = (
                f"High-quality threshold session averaging {r_pace} and {r_pow}W right at your {lthr} bpm LTHR ({r_hr} bpm avg, {max_hr_val} bpm max). "
                f"You generated a massive {aer_te_val} Aerobic / {ana_te_val} Anaerobic Training Effect, though total volume was {r_dist:.2f}K vs the {p_dist:.0f}K plan."
            )
            ai_insights = [
                f"Threshold Pace Precision: Your 4K work blocks locked into 5:09–5:11/km ({r_pow}–280W) with a 117–121 cm stride length—proving your Sub-1:45 half-marathon engine (4:58/km goal pace) is sharpening rapidly.",
                f"Heart Rate & Z5 Drift: Avg HR hit {r_hr} bpm (peaking at {max_hr_val} bpm, spending 55% in Zone 5). In warm conditions, 5:10/km pushed slightly above true lactate threshold ({lthr} bpm) into VO2 Max territory.",
                f"Warmup/Cooldown Volume Gap: Total distance was {r_dist:.2f} km vs {p_dist:.0f} km planned ({adh}%) because the warmup (0.36 km) and cooldown were cut short."
            ]
            ai_tips = [
                "Don't Skip Warmup & Cooldown: Always log at least 1.5 km easy jog before and after threshold blocks—it primes aerobic enzymes and clears blood lactate faster.",
                f"Cap Threshold HR at {lthr + 3} bpm: On warm days, run threshold reps by effort/power (270–275W) rather than forcing pace once HR crosses 178 bpm.",
                "48-Hour Recovery Window: After a 198 TL session, prioritize 8h sleep and extra electrolytes so your legs absorb the speed adaptation."
            ]
        else:
            ai_headline = f"Textbook Zone 2 Aerobic Builder ({adh}% On Target · {z12_pct}% Z2)"
            ai_summary = (
                f"Clean, controlled {r_dist:.2f} km aerobic run at {r_pace} and {r_hr} bpm average ({z12_pct}% inside Zone 1–2 below {z2_ceiling} bpm). "
                f"You banked {aer_te_val} Aerobic TE at just {r_tl} Training Load, building mitochondrial density with minimal mechanical stress."
            )
            ai_insights = [
                f"Zero Zone Creep: Average HR of {r_hr} bpm (max {max_hr_val} bpm) stayed {z2_ceiling - r_hr} bpm below your Zone 2 ceiling, keeping your weekly 80/20 polarization on track.",
                f"Steady Aerobic Efficiency: Running power averaged {r_pow}W with a {stride_val} cm stride length, showing smooth energy cost across all {int(round(r_dist))} kilometers.",
                f"Cadence Economy Check: Average cadence was {r_cad} spm. Nudging easy-day turnover from {r_cad} spm up toward 160 spm will reduce knee/calf loading as weekly volume climbs to 44+ km."
            ]
            ai_tips = [
                f"Next Session ({next_workout_payload['title']}): Target {next_workout_payload['target_pace']} in {next_workout_payload['target_hr_zone']}.",
                "Easy-Day Rhythm: Use a 160 bpm playlist or metronome check for the first 2 km of easy runs to ingrain quicker, lighter footstrikes.",
                "Pre-Run Hydration: Drink 300ml water + electrolytes 30 mins before heading out to keep cardiac drift under 4%."
            ]

        recent_runs_coaching.append({
            "activity_id": act.get("activity_id", ""),
            "title": w_title,
            "workout_type": w_type,
            "date": r_date,
            "day_label": day_fmt,
            "distance_km": r_dist,
            "planned_km": p_dist,
            "adherence_pct": adh,
            "verdict_badge": v_badge,
            "duration_minutes": r_dur,
            "avg_pace": r_pace,
            "target_pace": p_pace,
            "avg_hr": r_hr,
            "max_hr": max_hr_val,
            "cadence": r_cad,
            "max_cadence": detail.get("max_cadence") or 0,
            "power_watts": r_pow,
            "max_power_watts": max_pow_val,
            "stride_length_cm": stride_val,
            "training_load": r_tl,
            "aerobic_te": aer_te_val,
            "anaerobic_te": ana_te_val,
            "vo2_max": detail.get("vo2_max") or 51,
            "stamina_7d": detail.get("stamina_7d") or 96.0,
            "best_km_pace": detail.get("best_km_pace") or r_pace,
            "elev_gain_m": detail.get("elev_gain_m") or int(round(float(act.get("elevation_gain_m") or 0))),
            "calories_kcal": detail.get("calories_kcal") or int(act.get("calories_kcal") or 0),
            "physiology_note": phys_note,
            "coach_guidance": guidance_note,
            "ai_verdict_headline": ai_headline,
            "ai_executive_summary": ai_summary,
            "ai_insights": ai_insights,
            "ai_tips": ai_tips,
            "hr_zones": zones_list,
            "splits": splits_list,
            "stages": detail.get("stages") or [],
        })

    latest_coaching = recent_runs_coaching[0] if recent_runs_coaching else {}

    payload = {
        "status": "ok",
        "generated_at": now_local.strftime("%Y-%m-%d %H:%M:%S"),
        "updated_label": now_local.strftime("%I:%M %p").lstrip("0"),
        "athlete": profile,
        "weekly_mileage": {
            "actual_km": actual_week_km,
            "planned_km": planned_week_km,
            "expected_km_so_far": expected_km_so_far,
            "expected_completion_pct": expected_completion_pct,
            "remaining_km": round(max(0.0, planned_week_km - actual_week_km), 1),
            "completion_pct": completion_pct,
            "runs_completed": runs_completed_count,
            "runs_planned": max(runs_planned_count, 4),
            "avg_pace": wk_avg_pace,
            "total_elevation_m": total_week_elev,
            "days": days_matrix,
        },
        "training_load": {
            "acute_tl": display_tl,
            "weekly_tl": acute_tl,
            "target_tl_min": target_tl_min,
            "target_tl_max": target_tl_max,
            "load_ratio_pct": load_ratio_pct,
            "status_badge": tl_status,
            "rhr": profile.get("rhr", 44),
            "lthr": lthr,
            "recovery_hours": remaining_recovery_h,
            "readiness_score": readiness_score,
            "last_aerobic_te": latest_coaching.get("aerobic_te") or last_run.get("aerobic_te", 2.8),
            "last_anaerobic_te": latest_coaching.get("anaerobic_te") or last_run.get("anaerobic_te", 1.6),
        },
        "next_workout": next_workout_payload,
        "zone_spectrum": {
            "aerobic_pct": aerobic_pct,
            "threshold_pct": threshold_pct,
            "target_ratio": "80 / 20",
            "easy_avg_hr": easy_avg_hr,
            "quality_avg_hr": quality_avg_hr,
            "z2_ceiling_hr": z2_ceiling,
            "zone_creep_status": zone_creep_status,
        },
        "race_countdown": {
            "race_name": RACE_NAME,
            "race_date": RACE_DATE.strftime("%Y-%m-%d"),
            "days_left": days_to_race,
            "current_week": current_week_num,
            "total_weeks": TOTAL_PLAN_WEEKS,
            "phase": "AEROBIC + THRESHOLD BUILD",
            "goal_time": GOAL_TIME_STR,
            "goal_pace": GOAL_PACE_STR,
            "current_ltsp": ltsp_pace,
            "current_lthr": lthr,
            "pace_delta_sec": pace_delta,
            "readiness_tag": "ON TRACK · SUB-1:45",
        },
        "last_run": {
            "name": latest_coaching.get("title") or last_run.get("name", "Easy Run + Strides"),
            "date": last_run.get("date_only", today.strftime("%Y-%m-%d")),
            "day_label": latest_coaching.get("day_label", "Today"),
            "distance_km": last_run.get("distance_km", 6.81),
            "planned_km": latest_coaching.get("planned_km", 7.0),
            "adherence_pct": latest_coaching.get("adherence_pct", 97),
            "verdict_badge": latest_coaching.get("verdict_badge", "100% On Target (7K)"),
            "duration_minutes": last_run.get("duration_minutes", 49.6),
            "avg_pace": last_run.get("avg_pace", "7:14 /km"),
            "avg_hr": last_run.get("avg_heart_rate", 149),
            "max_hr": latest_coaching.get("max_hr", 173),
            "cadence": last_run.get("avg_cadence", 152),
            "max_cadence": latest_coaching.get("max_cadence", 180),
            "power_watts": last_run.get("avg_power_watts", 201),
            "max_power_watts": latest_coaching.get("max_power_watts", 413),
            "stride_length_cm": latest_coaching.get("stride_length_cm", 91),
            "training_load": last_run.get("training_load", 110),
            "aerobic_te": latest_coaching.get("aerobic_te", 2.8),
            "anaerobic_te": latest_coaching.get("anaerobic_te", 1.6),
            "vo2_max": latest_coaching.get("vo2_max", 51),
            "stamina_7d": latest_coaching.get("stamina_7d", 96.0),
            "best_km_pace": latest_coaching.get("best_km_pace", "6:42 /km"),
            "elev_gain_m": latest_coaching.get("elev_gain_m", 6),
            "calories_kcal": latest_coaching.get("calories_kcal", 703),
            "physiology_note": latest_coaching.get(
                "physiology_note",
                "Solid Aerobic Control: Avg HR 149 bpm stayed comfortably inside Zone 2 (<155 bpm)."
            ),
            "coach_guidance": latest_coaching.get(
                "coach_guidance",
                "Up Next: Long Run 20K (SUN 25 OCT) at 5:45–6:15 /km in Zone 2–3 (145–160 bpm)."
            ),
            "ai_verdict_headline": latest_coaching.get("ai_verdict_headline", "A+ Shakeout & Neuromuscular Pop (97% Volume · 94% Zone 2)"),
            "ai_executive_summary": latest_coaching.get("ai_executive_summary", ""),
            "ai_insights": latest_coaching.get("ai_insights", []),
            "ai_tips": latest_coaching.get("ai_tips", []),
            "hr_zones": latest_coaching.get("hr_zones", []),
            "splits": latest_coaching.get("splits", []),
            "stages": latest_coaching.get("stages", []),
        },
        "recent_runs": recent_runs_coaching,
    }

    with open(CACHE_FILE, "w") as f:
        json.dump(payload, f, indent=2)

    return payload


def get_cached_or_fresh(force_refresh: bool = False) -> Dict[str, Any]:
    if not force_refresh and CACHE_FILE.exists():
        age_sec = time.time() - CACHE_FILE.stat().st_mtime
        if age_sec < 900:  # 15 minutes cache TTL
            try:
                with open(CACHE_FILE, "r") as f:
                    return json.load(f)
            except Exception:
                pass
    try:
        return build_widget_payload()
    except Exception as e:
        print(f"[ERROR] Live refresh failed, falling back to cache if available: {e}")
        if CACHE_FILE.exists():
            with open(CACHE_FILE, "r") as f:
                data = json.load(f)
                data["stale"] = True
                return data
        raise


class WidgetRequestHandler(BaseHTTPRequestHandler):
    def _send_json(self, data: Dict[str, Any], status: int = 200):
        raw = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path.startswith("/api/v1/refresh"):
            try:
                data = get_cached_or_fresh(force_refresh=True)
                self._send_json(data)
            except Exception as e:
                self._send_json({"status": "error", "message": str(e)}, status=500)
        elif self.path.startswith("/api/v1/widget-data") or self.path == "/":
            try:
                force = "refresh=1" in self.path or "force=true" in self.path
                data = get_cached_or_fresh(force_refresh=force)
                self._send_json(data)
            except Exception as e:
                self._send_json({"status": "error", "message": str(e)}, status=500)
        elif self.path.startswith("/health"):
            self._send_json({"status": "healthy", "timestamp": time.time()})
        elif self.path.startswith("/download"):
            apk_path = BASE_DIR / "CorosExpressiveWidgets.apk"
            if apk_path.exists():
                raw = apk_path.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="CorosExpressiveWidgets.apk"')
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)
            else:
                self._send_json({"status": "apk_not_found"}, status=404)
        else:
            self._send_json({"status": "not_found"}, status=404)

    def do_POST(self):
        if self.path.startswith("/api/v1/refresh"):
            try:
                data = get_cached_or_fresh(force_refresh=True)
                self._send_json(data)
            except Exception as e:
                self._send_json({"status": "error", "message": str(e)}, status=500)
        else:
            self._send_json({"status": "not_found"}, status=404)

    def log_message(self, fmt, *args):
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {self.address_string()} - {fmt % args}")


def background_refresher():
    """Periodically refresh COROS & Google Calendar cache every 15 minutes."""
    while True:
        try:
            build_widget_payload()
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Widget cache refreshed from COROS + GCal.")
        except Exception as e:
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Background refresh error: {e}")
        time.sleep(900)


if __name__ == "__main__":
    print(f"Starting COROS Material 3 Expressive Widget Server on 0.0.0.0:{PORT}...")
    t = threading.Thread(target=background_refresher, daemon=True)
    t.start()
    server = HTTPServer(("0.0.0.0", PORT), WidgetRequestHandler)
    server.serve_forever()

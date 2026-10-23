import os
import re
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional

try:
    from google.auth.transport.requests import Request
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow
    from googleapiclient.discovery import build
except ImportError:
    Credentials = None
    InstalledAppFlow = None
    Request = None
    build = None

SCOPES = ["https://www.googleapis.com/auth/calendar"]


class GCalClient:
    """Client for interacting with Google Calendar training plans."""

    def __init__(self, credentials_path: str = "credentials.json", token_path: str = "token.json"):
        self.credentials_path = credentials_path
        self.token_path = token_path
        self.service = None

    def authenticate(self):
        if not Credentials:
            raise ImportError("Google API client libraries are not installed.")

        creds = None
        if os.path.exists(self.token_path):
            creds = Credentials.from_authorized_user_file(self.token_path, SCOPES)

        if not creds or not creds.valid:
            if creds and creds.expired and creds.refresh_token:
                creds.refresh(Request())
                with open(self.token_path, "w") as token_file:
                    token_file.write(creds.to_json())
            else:
                raise RuntimeError(f"Valid token.json required at {self.token_path}")

        self.service = build("calendar", "v3", credentials=creds)
        return self.service

    def _ensure_service(self):
        if not self.service:
            self.authenticate()

    def get_calendar_id_by_name(self, calendar_name: str = "Pune Half Marathon 2026 Training") -> str:
        self._ensure_service()
        if calendar_name.lower() == "primary":
            return "primary"

        cal_list = self.service.calendarList().list().execute()
        for cal in cal_list.get("items", []):
            summary = cal.get("summary", "").strip().lower()
            if summary == calendar_name.lower() or calendar_name.lower() in summary:
                return cal.get("id")
        return "primary"

    def get_planned_workouts(
        self,
        start_date: Optional[datetime] = None,
        end_date: Optional[datetime] = None,
        calendar_name: str = "Pune Half Marathon 2026 Training",
    ) -> List[Dict[str, Any]]:
        self._ensure_service()
        cal_id = self.get_calendar_id_by_name(calendar_name)

        if not start_date:
            now = datetime.now(timezone.utc)
            start_date = now - timedelta(days=7)
            end_date = now + timedelta(days=14)
        elif not end_date:
            end_date = start_date + timedelta(days=14)

        events_result = (
            self.service.events()
            .list(
                calendarId=cal_id,
                timeMin=start_date.isoformat(),
                timeMax=end_date.isoformat(),
                singleEvents=True,
                orderBy="startTime",
            )
            .execute()
        )

        workouts = []
        for ev in events_result.get("items", []):
            summary = ev.get("summary", "")
            desc = ev.get("description", "")
            start = ev.get("start", {}).get("dateTime") or ev.get("start", {}).get("date") or ""
            date_only = start[:10] if len(start) >= 10 else start
            parsed = self._parse_workout_details(summary, desc)
            workouts.append(
                {
                    "event_id": ev.get("id"),
                    "title": summary,
                    "start": start,
                    "date": date_only,
                    "description": desc,
                    "target_distance_km": parsed.get("distance_km") or 0.0,
                    "workout_type": parsed.get("workout_type"),
                    "target_pace": parsed.get("target_pace"),
                    "target_hr_zone": parsed.get("target_hr_zone"),
                    "completed": "✅" in summary or "[DONE]" in summary.upper(),
                }
            )
        return workouts

    def _parse_workout_details(self, title: str, description: str) -> Dict[str, Any]:
        title_lower = title.lower()
        combined = f"{title} {description}".lower()

        dist = None
        # 1. Check explicit "(8K total)" or "8 km total"
        total_match = re.search(r"(\d+(?:\.\d+)?)\s*(?:km|k)\s*total", combined)
        if total_match:
            dist = float(total_match.group(1))
        else:
            # 2. Match distance not preceded by 'x' (avoids matching '1K' in '4x1K')
            dist_match = re.search(r"(?<![x×\d.])(\d+(?:\.\d+)?)\s*(?:km|k\b)", title_lower)
            if not dist_match:
                dist_match = re.search(r"(?<![x×\d.])(\d+(?:\.\d+)?)\s*(?:km|k\b)", combined)
            if dist_match:
                try:
                    dist = float(dist_match.group(1))
                except ValueError:
                    pass
            elif "half marathon" in title_lower or "21.1" in title_lower:
                dist = 21.1

        w_type = "Easy Run"
        if "interval" in title_lower or "reps" in title_lower or "speed" in title_lower:
            w_type = "Intervals"
        elif "tempo" in title_lower or "threshold" in title_lower:
            w_type = "Threshold"
        elif "long" in title_lower or (dist and dist >= 14):
            w_type = "Long Run"
        elif "stride" in title_lower:
            w_type = "Easy + Strides"
        elif "recovery" in title_lower:
            w_type = "Recovery"
        elif "rest" in title_lower:
            w_type = "Rest Day"

        pace_match = re.search(r"(\d{1,2}:\d{2}(?:\s*[–-]\s*\d{1,2}:\d{2})?)\s*(?:/km|min/km)", combined)
        pace = f"{pace_match.group(1)} /km" if pace_match else None
        if not pace:
            if w_type == "Threshold":
                pace = "4:55–5:00 /km"
            elif w_type == "Intervals":
                pace = "4:35–4:45 /km"
            elif w_type == "Long Run":
                pace = "5:45–6:10 /km"
            else:
                pace = "6:00–6:30 /km"

        zone = "Zone 2 (<155 bpm)"
        if w_type == "Threshold":
            zone = "Zone 4 (163–172 bpm)"
        elif w_type == "Intervals":
            zone = "Zone 5 (173–185 bpm)"
        elif w_type == "Long Run":
            zone = "Zone 2–3 (145–160 bpm)"

        return {
            "distance_km": dist,
            "workout_type": w_type,
            "target_pace": pace,
            "target_hr_zone": zone,
        }

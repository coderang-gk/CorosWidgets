import gzip
import hashlib
import json
import ssl
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from typing import Any, Dict, List, Optional


class CorosClient:
    """Client for interacting with COROS Training Hub API."""

    def __init__(self, email: str, password: str, region: str = "teamapi.coros.com"):
        self.email = email
        self.password = password
        self.region = region
        self.base_url = f"https://{region}"
        self.access_token: Optional[str] = None
        self.user_id: Optional[str] = None
        self.profile: Dict[str, Any] = {}
        self.headers: Dict[str, str] = {
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ),
            "Content-Type": "application/json;charset=UTF-8",
        }
        self.ssl_context = ssl.create_default_context()

    def _http_request(self, method: str, url: str, data: Optional[dict] = None) -> dict:
        headers = dict(self.headers)
        body = json.dumps(data).encode("utf-8") if data is not None else None
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            try:
                with urllib.request.urlopen(req, context=self.ssl_context, timeout=15) as resp:
                    raw = resp.read()
                    if raw[:2] == b"\x1f\x8b":
                        raw = gzip.decompress(raw)
                    return json.loads(raw.decode("utf-8"))
            except urllib.error.URLError as e:
                if "CERTIFICATE_VERIFY_FAILED" in str(e):
                    ctx = ssl._create_unverified_context()
                    with urllib.request.urlopen(req, context=ctx, timeout=15) as resp:
                        raw = resp.read()
                        if raw[:2] == b"\x1f\x8b":
                            raw = gzip.decompress(raw)
                        return json.loads(raw.decode("utf-8"))
                raise
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            raise ConnectionError(f"HTTP {e.code}: {err_body}")
        except Exception as e:
            raise ConnectionError(f"Request failed: {str(e)}")

    def login(self) -> Dict[str, Any]:
        """Authenticate with COROS Training Hub and capture athlete profile & EvoLab zones."""
        if not self.email or not self.password:
            raise ValueError("COROS_EMAIL and COROS_PASSWORD must be configured.")

        hashed_pwd = hashlib.md5(self.password.encode("utf-8")).hexdigest()
        payload = {"account": self.email, "pwd": hashed_pwd, "accountType": 2}
        res_data = self._http_request("POST", f"{self.base_url}/account/login", data=payload)

        if res_data.get("result") != "0000" and res_data.get("code") != "0000":
            err_msg = res_data.get("message") or res_data.get("msg") or "Authentication failed"
            raise ValueError(f"COROS Authentication Error: {err_msg}")

        data = res_data.get("data", {})
        self.access_token = data.get("accessToken")
        self.user_id = data.get("userId")
        if not self.access_token:
            raise ValueError("No access token returned from COROS login.")

        self.headers["accesstoken"] = self.access_token

        zone_data = data.get("zoneData") or {}
        if isinstance(zone_data, str):
            try:
                zone_data = json.loads(zone_data)
            except Exception:
                zone_data = {}

        ltsp_sec = int(data.get("ltsp") or zone_data.get("ltsp") or 297)
        ltsp_str = f"{ltsp_sec // 60}:{ltsp_sec % 60:02d} /km"

        self.profile = {
            "nickname": data.get("nickname") or "Gaurang",
            "rhr": int(data.get("rhr") or zone_data.get("rhr") or 44),
            "max_hr": int(data.get("maxHr") or zone_data.get("maxHr") or 195),
            "lthr": int(data.get("lthr") or zone_data.get("lthr") or 172),
            "ltsp_sec": ltsp_sec,
            "ltsp_pace": ltsp_str,
            "weight_kg": float(data.get("weight") or 81.5),
            "stamina_level": int(data.get("staminaLevel") or 0),
        }
        return self.profile

    def _ensure_authenticated(self):
        if not self.access_token:
            self.login()

    def get_activities(self, limit: int = 30, sport_type: Optional[int] = None) -> List[Dict[str, Any]]:
        self._ensure_authenticated()
        params: Dict[str, Any] = {"pageNumber": 1, "size": limit}
        if sport_type is not None:
            params["sportType"] = sport_type

        query_url = f"{self.base_url}/activity/query?{urllib.parse.urlencode(params)}"
        try:
            result_json = self._http_request("GET", query_url)
        except ConnectionError as e:
            if "401" in str(e) or "403" in str(e):
                self.login()
                result_json = self._http_request("GET", query_url)
            else:
                raise

        raw_list = result_json.get("data", {}).get("dataList", [])
        return [self._format_activity(item) for item in raw_list]

    def _format_activity(self, item: Dict[str, Any]) -> Dict[str, Any]:
        distance_m = float(item.get("distance", 0))
        distance_km = round(distance_m / 1000.0, 2)
        duration_s = int(item.get("totalTime", 0) or item.get("workoutTime", 0))
        duration_mins = round(duration_s / 60.0, 1)

        avg_pace_str = "N/A"
        sec_per_km = item.get("avgSpeed") or item.get("avg5x10s") or item.get("adjustedPace")
        if sec_per_km and sec_per_km > 0:
            mins = int(sec_per_km // 60)
            secs = int(sec_per_km % 60)
            avg_pace_str = f"{mins}:{secs:02d} /km"
        elif distance_km > 0 and duration_s > 0:
            calc_sec_per_km = duration_s / distance_km
            mins = int(calc_sec_per_km // 60)
            secs = int(calc_sec_per_km % 60)
            avg_pace_str = f"{mins}:{secs:02d} /km"

        start_time_ts = int(item.get("startTime", 0) or 0)
        start_dt_str = (
            datetime.fromtimestamp(start_time_ts).strftime("%Y-%m-%d %H:%M:%S")
            if start_time_ts
            else "Unknown"
        )
        date_only = (
            datetime.fromtimestamp(start_time_ts).strftime("%Y-%m-%d")
            if start_time_ts
            else ""
        )

        raw_cal = item.get("calorie", 0)
        kcal = round(raw_cal / 1000.0) if raw_cal > 2000 else raw_cal

        return {
            "activity_id": str(item.get("labelId") or item.get("sportDataId") or ""),
            "name": item.get("name") or "COROS Run",
            "sport_type": item.get("sportType", 100),
            "device": item.get("device", "COROS PACE Pro"),
            "date": start_dt_str,
            "date_only": date_only,
            "timestamp": start_time_ts,
            "distance_km": distance_km,
            "duration_minutes": duration_mins,
            "duration_seconds": duration_s,
            "avg_pace": avg_pace_str,
            "avg_pace_sec": float(sec_per_km or (duration_s / distance_km if distance_km > 0 else 0)),
            "avg_heart_rate": int(item.get("avgHr") or 0),
            "max_heart_rate": int(item.get("maxHr") or 0),
            "avg_cadence": int(item.get("avgCadence") or 0),
            "avg_power_watts": int(item.get("avgPower") or 0),
            "elevation_gain_m": float(item.get("ascent") or item.get("elevationGain") or 0),
            "training_load": int(item.get("trainingLoad") or 0),
            "aerobic_te": round(float(item.get("aerobicEffect") or item.get("te") or 0), 1),
            "anaerobic_te": round(float(item.get("anaerobicEffect") or 0), 1),
            "calories_kcal": kcal,
            "recovery_hours": int(item.get("recoveryTime") or 0),
        }

    @staticmethod
    def _fmt_pace(sec_per_km: float) -> str:
        if not sec_per_km or sec_per_km <= 0:
            return "N/A"
        s = int(round(sec_per_km))
        return f"{s // 60}:{s % 60:02d} /km"

    @staticmethod
    def _fmt_duration(seconds: int) -> str:
        if seconds <= 0:
            return "0:00"
        m = seconds // 60
        s = seconds % 60
        return f"{m}:{s:02d}"

    def get_activity_detail(self, label_id: str, sport_type: int = 100) -> Dict[str, Any]:
        """Fetch full COROS workout detail including EvoLab summary, HR zones, and 1km / stage splits."""
        if not label_id:
            return {}
        self._ensure_authenticated()
        url = f"{self.base_url}/activity/detail/query?labelId={urllib.parse.quote(str(label_id))}&sportType={sport_type}"
        try:
            res = self._http_request("POST", url, data={})
        except Exception as e:
            print(f"[WARN] Failed to fetch activity detail for {label_id}: {e}")
            return {}

        data = res.get("data") or {}
        summary = data.get("summary") or {}

        # Extract HR zones (type 126 is COROS LTHR 5/6-zone breakdown)
        hr_zones_raw = []
        for z in data.get("zoneList") or []:
            if z.get("type") == 126:
                hr_zones_raw = z.get("zoneItemList") or []
                break
        if not hr_zones_raw:
            for z in data.get("zoneList") or []:
                if z.get("type") in (1, 2):
                    hr_zones_raw = z.get("zoneItemList") or []
                    break

        zone_names = [
            "Z1 Recovery",
            "Z2 Aerobic End.",
            "Z3 Aerobic Power",
            "Z4 Threshold",
            "Z5 Anaerobic End.",
            "Z6 Anaerobic Pwr",
        ]
        hr_zones = []
        # Combine Z0 + Z1 into Z1-Z2 or list 5 meaningful zones
        if len(hr_zones_raw) >= 6:
            z0 = hr_zones_raw[0]
            z1 = hr_zones_raw[1]
            sec_easy = int(z0.get("second", 0)) + int(z1.get("second", 0))
            pct_easy = int(z0.get("percent", 0)) + int(z1.get("percent", 0))
            hr_zones.append({
                "zone_index": 1,
                "name": "Z1–2 Aerobic Base",
                "bpm_range": f"<{z1.get('rightScope', 155)} bpm",
                "seconds": sec_easy,
                "duration": self._fmt_duration(sec_easy),
                "percent": pct_easy,
            })
            labels = [
                (2, "Z3 Aerobic Power"),
                (3, "Z4 LTHR Threshold"),
                (4, "Z5 VO2 Max / Anaer."),
                (5, "Z6 Max Effort"),
            ]
            for idx, label in labels:
                zi = hr_zones_raw[idx]
                sec = int(zi.get("second", 0))
                left = zi.get("leftScope", 0)
                right = zi.get("rightScope", 200)
                rng = f">{left} bpm" if right >= 220 else f"{left}–{right} bpm"
                hr_zones.append({
                    "zone_index": idx,
                    "name": label,
                    "bpm_range": rng,
                    "seconds": sec,
                    "duration": self._fmt_duration(sec),
                    "percent": int(zi.get("percent", 0)),
                })
        else:
            for i, zi in enumerate(hr_zones_raw[:5]):
                sec = int(zi.get("second", 0))
                hr_zones.append({
                    "zone_index": i + 1,
                    "name": zone_names[min(i, len(zone_names) - 1)],
                    "bpm_range": f"{zi.get('leftScope', 0)}–{zi.get('rightScope', 0)} bpm",
                    "seconds": sec,
                    "duration": self._fmt_duration(sec),
                    "percent": int(zi.get("percent", 0)),
                })

        # Extract 1km splits (LAP type 10) and workout stages (LAP type 2)
        km_splits = []
        stage_splits = []
        for lap in data.get("lapList") or []:
            ltype = lap.get("type")
            if ltype in (10, 2):
                target = km_splits if ltype == 10 else stage_splits
                for item in lap.get("lapItemList") or []:
                    dist_km = round(float(item.get("distance", 0)) / 100000.0, 2)
                    if dist_km <= 0.01:
                        continue
                    pace_s = float(item.get("avgPace") or 0)
                    adj_pace_s = float(item.get("adjustedPace") or pace_s)
                    time_s = int(round(float(item.get("time", 0)) / 100.0))
                    target.append({
                        "index": int(item.get("lapIndex", len(target) + 1)),
                        "distance_km": dist_km,
                        "duration": self._fmt_duration(time_s),
                        "pace": self._fmt_pace(pace_s).replace(" /km", ""),
                        "adjusted_pace": self._fmt_pace(adj_pace_s).replace(" /km", ""),
                        "avg_hr": int(item.get("avgHr") or 0),
                        "cadence": int(item.get("avgCadence") or 0),
                        "power_watts": int(item.get("avgPower") or 0),
                        "stride_cm": int(item.get("avgStrideLength") or 0),
                    })

        raw_cal = int(summary.get("calories") or 0)
        kcal = round(raw_cal / 1000) if raw_cal > 2000 else raw_cal
        best_km_sec = float(summary.get("bestKm") or 0)

        return {
            "aerobic_te": round(float(summary.get("aerobicEffect") or 0), 1),
            "anaerobic_te": round(float(summary.get("anaerobicEffect") or 0), 1),
            "max_hr": int(summary.get("maxHr") or 0),
            "max_power_watts": int(summary.get("maxPower") or 0),
            "max_cadence": int(summary.get("maxCadence") or 0),
            "stride_length_cm": int(summary.get("avgStepLen") or 0),
            "vo2_max": int(summary.get("currentVo2Max") or 51),
            "stamina_7d": round(float(summary.get("staminaLevel7d") or 0), 1),
            "best_km_pace": self._fmt_pace(best_km_sec),
            "elev_gain_m": int(round(float(summary.get("elevGain") or 0))),
            "calories_kcal": kcal,
            "hr_zones": hr_zones,
            "splits": km_splits[:16],
            "stages": stage_splits[:14],
        }

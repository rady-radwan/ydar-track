"""Writes a sample Ydar Track data file (used for the Windows app screenshot check on GitHub)."""
import json, random, sys, time
random.seed(7)
now = int(time.time() * 1000)
H = 3_600_000
day = 86_400_000
projects = [
    {"id": 1, "name": "Studying", "color": -18905, "emoji": "💻", "totalGoalMin": 6000},
    {"id": 2, "name": "PE exam", "color": -18905, "emoji": "📚", "parentId": 1},
    {"id": 3, "name": "Revit", "color": -11563050, "emoji": "🏗️", "weeklyGoalMin": 300},
    {"id": 4, "name": "Gym", "color": -13983334, "emoji": "🏋️"},
    {"id": 5, "name": "Part 107", "color": -4882724, "emoji": "🚁", "archived": True},
]
sessions = []
sid = 100
for d in range(1, 22):
    start_day = now - d * day
    for pid in random.sample([1, 2, 3, 4], 2):
        dur = random.choice([30, 45, 60, 90, 120]) * 60_000
        st = start_day - random.randint(1, 6) * H
        sessions.append({"id": sid, "projectId": pid, "start": st, "end": st + dur, "note": ""})
        sid += 1
data = {"version": 4, "nextId": sid, "projects": projects, "sessions": sessions,
        "active": {"projectId": 2, "start": now - 37 * 60_000, "note": "Thermo practice", "targetMillis": 3_600_000}}
json.dump(data, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False)

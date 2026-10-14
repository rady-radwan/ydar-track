# Ydar Track — Android app (v4)

**Build yourself, one hour at a time.**

Track how much time you spend on each project and subproject, climb 10 ranks every week, grow your lifetime legacy, and see it all in charts.

## What's new in v4
- **New logo**: a head built like a construction project — half golden low-poly face, half concrete building with scaffolding and a crane. New app icon (with themed/monochrome icon) and notification icon.
- **Dark gold theme**: charcoal and construction gold, now the default (Light and Auto are still in Settings).
- **10 ranks** by your average per day this week (resets every Monday): 🚧 Site Tourist → 📐 Blueprint Rookie (30m) → ⛏️ Groundbreaker (1h) → 🧱 Foundation Builder (1.5h) → 🔨 Bricklayer (2h) → 🏗️ Steel Framer (2.5h) → 👷 Site Engineer (3h) → 🏢 Master Builder (3.5h) → 🌆 Skyline Architect (4h) → 👑 Skyscraper Legend (5h+). Each has its own meaning, badge and sound.
- **What it takes today**: the rank bar and pop-up show how many hours you need to work *today* to reach the next rank, and the ladder shows "+X today" for every higher rank.
- **Rank-up celebration** with a special message the moment you reach a higher rank.
- **Your Legacy**: lifetime hours as a tower — one floor every 10 hours, milestones at 10, 25, 50, 100, 250, 500, 1000 h… It never resets. On Home, in the rank pop-up and in Stats.
- **Encouraging quotes**: your full list, plus lines for Mondays, streaks, late nights and when you're close to the next rank. A quote of the day on the Home banner.
- **Archive** finished projects: hidden from the start list (a "Show archived" link brings them back, faded); faded in an *Archived* section on the Projects tab with **Unarchive**. Their hours still count everywhere.
- **Total-hours goal** per project (e.g. 100 h): a bar with hours so far, hours to go, % and an estimate of weeks left at your recent pace.
- **Excel**: Projects sheet has *Total goal (h)*, *Hours so far*, *% of goal* and *Archived*; Summary has your legacy and this week's rank. Import reads these columns too.

## Track tab
- **Open timer**: counts up until you press *Stop & save*.
- **Countdown**: pick 25m, 45m, 1h, 1h 30m, 2h, 3h or a custom length. When the time is up, the session is saved automatically and you get an alert, even if the app is closed. Need more time? Tap **+15m**.
- Tap ▶ on a project (or on a subproject, using the arrow to open the list) to start. Starting another project while one is running saves the first one.
- The running timer shows in your notifications with a live clock and a **Stop & save** button.
- Forgot to press start? Tap **Started at … · change**.
- **Add past session** for work you didn't time.

## Projects tab
- Projects and **subprojects** (one level: *Part 107 › Airspace*, *BIM › Revit*).
- Each has an **emoji or short label** (🏗️ 📐 📚 … or MEP, BIM, VDC, PMP…) and a color.
- Optional **weekly goal** and **total-hours goal**, each with a progress bar.
- **Archive** a finished project (Edit → Archived, or the Archive button on its card).
- Deleting a project also deletes its subprojects and their sessions (the app asks first).

## Stats tab
- Ranges: **This week, This month, This year, All time**, or **any dates you pick**.
- Total time; average **per day**, **per active day**, **per week**, **per session**; longest session.
- **Averages** table comparing week / month / year / all time / your custom range side by side.
- Ring chart and bars by project. Tap a project to see its subprojects.
- Hours per day, month or year, with a dashed daily-average line.

## Excel (⋮ menu on History or Projects)
- **Export to Excel**: an `.xlsx` with three sheets.
  - *Sessions*: Project, Subproject, Date, Start, End, Hours, Minutes, Note. Hours and Minutes are formulas, so they update if you edit times in Excel.
  - *Summary*: totals per project and subproject.
  - *Projects*: emoji and colors.
- **Share Excel file…**: send the export by email, WhatsApp or Drive.
- **Import from Excel or CSV**: reads `.xlsx` and `.csv`. Column names are matched flexibly (*Project/Category*, *Date/Day*, *Start/From*, *End/To*, *Hours*, *Minutes*, *Duration*, *Note*). You see a summary before anything is added. Rows already in the app are skipped, so importing the same file twice is safe. Missing projects and subprojects are created.
- **Get a blank import template**: an empty workbook with the right columns and a *How to fill* sheet.

Your data stays on your phone. Android's automatic backup includes it when backup is on.

## Building
Open the folder in Android Studio, then **Build → Generate App Bundles or APKs → Generate APKs**.
The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Font: Barlow by Jeremy Tribby, SIL Open Font License.

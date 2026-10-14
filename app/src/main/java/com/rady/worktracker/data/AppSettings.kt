package com.rady.worktracker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads and saves [Settings] in the phone's preferences, and shares them with the whole app. */
object AppSettings {
    private lateinit var prefs: SharedPreferences
    private val _flow = MutableStateFlow(Settings())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val current: Settings get() = _flow.value

    @Volatile private var ready = false

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val d = Settings()
        _flow.value = Settings(
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", d.theme.name)!!) }.getOrDefault(d.theme),
            dailyGoalMin = prefs.getInt("dailyGoalMin", d.dailyGoalMin),
            lapOn = prefs.getBoolean("lapOn", d.lapOn),
            lapMin = prefs.getInt("lapMin", d.lapMin),
            halfOn = prefs.getBoolean("halfOn", d.halfOn),
            fiveOn = prefs.getBoolean("fiveOn", d.fiveOn),
            endOn = prefs.getBoolean("endOn", d.endOn),
            rankPopup = prefs.getBoolean("rankPopup", d.rankPopup),
            rankSound = prefs.getBoolean("rankSound", d.rankSound),
        )
        // v4: the app's look is dark gold — switch to Dark once (can be changed in Settings).
        if (!prefs.getBoolean("v4ThemeDark", false)) {
            prefs.edit().putBoolean("v4ThemeDark", true).putString("theme", ThemeMode.DARK.name).apply()
            _flow.value = _flow.value.copy(theme = ThemeMode.DARK)
        }
        ready = true
    }

    /** The highest rank already celebrated this week: (week start as epoch day, level), or null the first time. */
    fun rankSeen(): Pair<Long, Int>? {
        if (!prefs.contains("rankSeenWeek")) return null
        return prefs.getLong("rankSeenWeek", 0L) to prefs.getInt("rankSeenLevel", 0)
    }

    fun setRankSeen(weekEpochDay: Long, level: Int) {
        prefs.edit().putLong("rankSeenWeek", weekEpochDay).putInt("rankSeenLevel", level).apply()
    }

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_flow.value)
        _flow.value = s
        prefs.edit()
            .putString("theme", s.theme.name)
            .putInt("dailyGoalMin", s.dailyGoalMin)
            .putBoolean("lapOn", s.lapOn)
            .putInt("lapMin", s.lapMin)
            .putBoolean("halfOn", s.halfOn)
            .putBoolean("fiveOn", s.fiveOn)
            .putBoolean("endOn", s.endOn)
            .putBoolean("rankPopup", s.rankPopup)
            .putBoolean("rankSound", s.rankSound)
            .apply()
    }
}

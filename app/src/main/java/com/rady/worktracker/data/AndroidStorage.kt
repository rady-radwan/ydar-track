package com.rady.worktracker.data

import android.content.Context
import android.content.SharedPreferences

/** [KeyValues] on top of Android's SharedPreferences. */
class AndroidKeyValues(private val sp: SharedPreferences) : KeyValues {
    override fun getString(key: String, def: String?) = sp.getString(key, def)
    override fun getInt(key: String, def: Int) = sp.getInt(key, def)
    override fun getLong(key: String, def: Long) = sp.getLong(key, def)
    override fun getBoolean(key: String, def: Boolean) = sp.getBoolean(key, def)
    override fun contains(key: String) = sp.contains(key)
    override fun edit(): KeyValues.Editor = object : KeyValues.Editor {
        val e = sp.edit()
        override fun putString(key: String, value: String?) = apply { e.putString(key, value) }
        override fun putInt(key: String, value: Int) = apply { e.putInt(key, value) }
        override fun putLong(key: String, value: Long) = apply { e.putLong(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { e.putBoolean(key, value) }
        override fun apply() = e.apply()
    }
}

/** Opens the data file and settings (safe to call many times). */
object AndroidInit {
    fun init(context: Context) {
        val app = context.applicationContext
        Repo.init(app.filesDir)
        AppSettings.init(AndroidKeyValues(app.getSharedPreferences("settings", Context.MODE_PRIVATE)))
    }
}

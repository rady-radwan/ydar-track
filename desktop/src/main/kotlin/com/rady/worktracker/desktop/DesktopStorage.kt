package com.rady.worktracker.desktop

import com.rady.worktracker.data.KeyValues
import java.io.File
import java.util.Properties

object DesktopPaths {
    /** %APPDATA%\Ydar Track on Windows. */
    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        File(base, "Ydar Track").apply { mkdirs() }
    }
}

/** Settings saved in a small .properties file. */
class PropertiesKeyValues(private val file: File) : KeyValues {
    private val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }

    override fun getString(key: String, def: String?): String? = props.getProperty(key) ?: def
    override fun getInt(key: String, def: Int) = props.getProperty(key)?.toIntOrNull() ?: def
    override fun getLong(key: String, def: Long) = props.getProperty(key)?.toLongOrNull() ?: def
    override fun getBoolean(key: String, def: Boolean) = props.getProperty(key)?.toBooleanStrictOrNull() ?: def
    override fun contains(key: String) = props.containsKey(key)

    override fun edit(): KeyValues.Editor = object : KeyValues.Editor {
        private val changes = LinkedHashMap<String, String?>()
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value.toString() }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value.toString() }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value.toString() }
        override fun apply() {
            synchronized(props) {
                changes.forEach { (k, v) -> if (v == null) props.remove(k) else props.setProperty(k, v) }
                runCatching { file.outputStream().use { props.store(it, "Ydar Track settings") } }
            }
        }
    }
}

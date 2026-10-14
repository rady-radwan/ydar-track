package com.rady.worktracker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves everything to one small JSON file in the app's private storage.
 * Android's automatic backup includes this file, so your data comes back
 * when you set up a new phone with the same Google account.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "worktracker.json")

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            // Never silently lose data: keep the unreadable file aside before starting fresh.
            runCatching {
                file.copyTo(File(file.parentFile, "worktracker-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
            }
            AppData()
        }
    }

    fun save(data: AppData) {
        val text = toJson(data).toString()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        fun toJson(d: AppData): JSONObject {
            val root = JSONObject()
            root.put("version", 4)
            root.put("nextId", d.nextId)

            val projects = JSONArray()
            d.projects.forEach { p ->
                val o = JSONObject().put("id", p.id).put("name", p.name).put("color", p.color).put("emoji", p.emoji)
                if (p.parentId != null) o.put("parentId", p.parentId)
                if (p.weeklyGoalMin > 0) o.put("weeklyGoalMin", p.weeklyGoalMin)
                if (p.totalGoalMin > 0) o.put("totalGoalMin", p.totalGoalMin)
                if (p.archived) o.put("archived", true)
                projects.put(o)
            }
            root.put("projects", projects)

            val sessions = JSONArray()
            d.sessions.forEach { s ->
                sessions.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("projectId", s.projectId)
                        .put("start", s.start)
                        .put("end", s.end)
                        .put("note", s.note)
                )
            }
            root.put("sessions", sessions)

            d.active?.let { a ->
                val o = JSONObject().put("projectId", a.projectId).put("start", a.start).put("note", a.note)
                if (a.targetMillis != null) o.put("targetMillis", a.targetMillis)
                root.put("active", o)
            }
            return root
        }

        fun fromJson(o: JSONObject): AppData {
            val projects = mutableListOf<Project>()
            o.optJSONArray("projects")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    projects += Project(
                        id = p.getLong("id"),
                        name = p.getString("name"),
                        color = p.getInt("color"),
                        emoji = p.optString("emoji", ""),
                        parentId = if (p.has("parentId") && !p.isNull("parentId")) p.getLong("parentId") else null,
                        weeklyGoalMin = p.optInt("weeklyGoalMin", 0),
                        totalGoalMin = p.optInt("totalGoalMin", 0),
                        archived = p.optBoolean("archived", false),
                    )
                }
            }
            val sessions = mutableListOf<Session>()
            o.optJSONArray("sessions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    sessions += Session(
                        id = s.getLong("id"),
                        projectId = s.getLong("projectId"),
                        start = s.getLong("start"),
                        end = s.getLong("end"),
                        note = s.optString("note", ""),
                    )
                }
            }
            val active = o.optJSONObject("active")?.let { a ->
                ActiveTimer(
                    projectId = a.getLong("projectId"),
                    start = a.getLong("start"),
                    note = a.optString("note", ""),
                    targetMillis = if (a.has("targetMillis") && !a.isNull("targetMillis")) a.getLong("targetMillis") else null,
                )
            }
            val maxId = (projects.map { it.id } + sessions.map { it.id }).maxOrNull() ?: 0L
            val nextId = maxOf(o.optLong("nextId", 1L), maxId + 1)
            return AppData(projects, sessions, active, nextId)
        }
    }
}

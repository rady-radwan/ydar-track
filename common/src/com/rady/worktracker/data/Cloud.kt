package com.rady.worktracker.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ydar Track's online sync through Supabase (a free hosted database).
 * The same code runs in the Android app and the Windows app.
 */
object CloudConfig {
    const val URL = "https://acvtlkfhkybyckvbiczh.supabase.co"
    /** Public key for this app — safe to ship; every row is protected by row-level security. */
    const val KEY = "sb_publishable_uKavHMfwXCol_F8ZoSzhAQ_7XdFb6Bf"
}

class CloudException(message: String, val code: Int = 0, val errorCode: String = "") : IOException(message)

data class CloudSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val userId: String,
    val email: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("access", accessToken).put("refresh", refreshToken).put("exp", expiresAtMs)
        .put("user", userId).put("email", email)

    companion object {
        fun fromJson(o: JSONObject) = CloudSession(
            o.getString("access"), o.getString("refresh"), o.optLong("exp"), o.getString("user"), o.optString("email"),
        )
    }
}

private object Http {
    fun call(method: String, url: String, body: String?, headers: Map<String, String>): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("apikey", CloudConfig.KEY)
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    fun fail(code: Int, text: String): Nothing {
        val o = runCatching { JSONObject(text) }.getOrNull()
        val errCode = o?.optString("error_code")?.takeIf { it.isNotBlank() } ?: o?.optString("error") ?: ""
        val msg = o?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: o?.optString("error_description")?.takeIf { it.isNotBlank() }
            ?: o?.optString("message")?.takeIf { it.isNotBlank() }
            ?: text.take(200)
        throw CloudException(friendly(errCode, msg, code), code, errCode)
    }

    private fun friendly(errCode: String, msg: String, code: Int): String = when {
        errCode == "email_not_confirmed" -> "Please confirm your email first — open the link we sent you, then sign in."
        errCode == "invalid_credentials" || msg.contains("Invalid login", true) -> "Wrong email or password."
        errCode == "user_already_exists" || msg.contains("already registered", true) -> "This email already has an account — use Sign in."
        errCode == "weak_password" || msg.contains("Password should", true) -> msg
        errCode == "over_email_send_rate_limit" -> "Too many emails were sent. Please wait a few minutes and try again."
        code == 401 -> "Your sign-in expired. Please sign in again."
        else -> msg
    }
}

/** Email + password accounts (Supabase Auth). */
object CloudAuth {
    private fun session(o: JSONObject): CloudSession {
        val user = o.getJSONObject("user")
        return CloudSession(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAtMs = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000L,
            userId = user.getString("id"),
            email = user.optString("email"),
        )
    }

    /** Creates an account. Returns a session, or null when the email must be confirmed first. */
    fun signUp(email: String, password: String): CloudSession? {
        val (code, text) = Http.call("POST", "${CloudConfig.URL}/auth/v1/signup", JSONObject().put("email", email.trim()).put("password", password).toString(), emptyMap())
        if (code !in 200..299) Http.fail(code, text)
        val o = JSONObject(text)
        return if (o.has("access_token")) session(o) else null
    }

    fun signIn(email: String, password: String): CloudSession {
        val (code, text) = Http.call("POST", "${CloudConfig.URL}/auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password).toString(), emptyMap())
        if (code !in 200..299) Http.fail(code, text)
        return session(JSONObject(text))
    }

    fun refresh(s: CloudSession): CloudSession {
        val (code, text) = Http.call("POST", "${CloudConfig.URL}/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", s.refreshToken).toString(), emptyMap())
        if (code !in 200..299) Http.fail(code, text)
        return session(JSONObject(text))
    }

    /** Sends a password-reset email. */
    fun resetPassword(email: String) {
        val (code, text) = Http.call("POST", "${CloudConfig.URL}/auth/v1/recover", JSONObject().put("email", email.trim()).toString(), emptyMap())
        if (code !in 200..299) Http.fail(code, text)
    }
}

/** Supabase tables as the [SyncRemote] the sync engine talks to. */
class SupabaseRemote(private val s: CloudSession) : SyncRemote {
    private val auth = mapOf("Authorization" to "Bearer ${s.accessToken}")
    private val rest = "${CloudConfig.URL}/rest/v1"

    private fun upsert(table: String, conflict: String, rows: JSONArray) {
        if (rows.length() == 0) return
        val (code, text) = Http.call(
            "POST", "$rest/$table?on_conflict=${URLEncoder.encode(conflict, "UTF-8")}", rows.toString(),
            auth + ("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        if (code !in 200..299) Http.fail(code, text)
    }

    private fun pull(table: String, afterRev: Long): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var after = afterRev
        while (true) {
            val (code, text) = Http.call("GET", "$rest/$table?select=*&rev=gt.$after&order=rev.asc&limit=1000", null, auth)
            if (code !in 200..299) Http.fail(code, text)
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) out += arr.getJSONObject(i)
            if (arr.length() < 1000) break
            after = arr.getJSONObject(arr.length() - 1).getLong("rev")
        }
        return out
    }

    override fun pushProjects(rows: List<Pair<Project, Boolean>>) {
        rows.chunked(500).forEach { chunk ->
            val arr = JSONArray()
            chunk.forEach { (p, del) ->
                arr.put(JSONObject()
                    .put("user_id", s.userId).put("id", p.id).put("name", p.name).put("color", p.color).put("emoji", p.emoji)
                    .put("parent_id", p.parentId ?: JSONObject.NULL)
                    .put("weekly_goal_min", p.weeklyGoalMin).put("total_goal_min", p.totalGoalMin)
                    .put("archived", p.archived).put("deleted", del))
            }
            upsert("projects", "user_id,id", arr)
        }
    }

    override fun pushSessions(rows: List<Pair<Session, Boolean>>) {
        rows.chunked(500).forEach { chunk ->
            val arr = JSONArray()
            chunk.forEach { (x, del) ->
                arr.put(JSONObject()
                    .put("user_id", s.userId).put("id", x.id).put("project_id", x.projectId)
                    .put("start_ms", x.start).put("end_ms", x.end).put("note", x.note).put("deleted", del))
            }
            upsert("sessions", "user_id,id", arr)
        }
    }

    override fun pushTimer(timer: ActiveTimer?, changedMs: Long) {
        val o = JSONObject().put("user_id", s.userId).put("changed_ms", changedMs)
            .put("project_id", timer?.projectId ?: JSONObject.NULL)
            .put("start_ms", timer?.start ?: JSONObject.NULL)
            .put("target_ms", timer?.targetMillis ?: JSONObject.NULL)
            .put("note", timer?.note ?: "")
        upsert("active_timer", "user_id", JSONArray().put(o))
    }

    override fun pullProjects(afterRev: Long): List<RemoteProject> = pull("projects", afterRev).map { o ->
        RemoteProject(
            Project(
                id = o.getLong("id"), name = o.optString("name"), color = o.optInt("color"), emoji = o.optString("emoji"),
                parentId = if (o.isNull("parent_id")) null else o.getLong("parent_id"),
                weeklyGoalMin = o.optInt("weekly_goal_min"), totalGoalMin = o.optInt("total_goal_min"),
                archived = o.optBoolean("archived"),
            ),
            deleted = o.optBoolean("deleted"), rev = o.getLong("rev"),
        )
    }

    override fun pullSessions(afterRev: Long): List<RemoteSession> = pull("sessions", afterRev).map { o ->
        RemoteSession(
            Session(o.getLong("id"), o.getLong("project_id"), o.getLong("start_ms"), o.getLong("end_ms"), o.optString("note")),
            deleted = o.optBoolean("deleted"), rev = o.getLong("rev"),
        )
    }

    override fun pullTimer(): RemoteTimer? {
        val (code, text) = Http.call("GET", "$rest/active_timer?select=*", null, auth)
        if (code !in 200..299) Http.fail(code, text)
        val arr = JSONArray(text)
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        val timer = if (o.isNull("project_id") || o.isNull("start_ms")) null else ActiveTimer(
            projectId = o.getLong("project_id"), start = o.getLong("start_ms"), note = o.optString("note"),
            targetMillis = if (o.isNull("target_ms")) null else o.getLong("target_ms"),
        )
        return RemoteTimer(timer, o.optLong("changed_ms"), o.optLong("rev"))
    }
}

/**
 * Keeps the account and sync bookkeeping in two small files in [dir]
 * and runs a full sync. Platform code supplies the data and how to save it.
 */
class CloudSync(private val dir: File) {
    private val sessionFile = File(dir, "cloud_session.json")
    private val stateFile = File(dir, "cloud_sync_state.json")

    @Volatile var session: CloudSession? = load(); private set

    private fun load(): CloudSession? = runCatching { CloudSession.fromJson(JSONObject(sessionFile.readText())) }.getOrNull()

    private fun saveSession(s: CloudSession?) {
        session = s
        if (s == null) sessionFile.delete() else sessionFile.writeText(s.toJson().toString())
    }

    val signedIn: Boolean get() = session != null
    val email: String? get() = session?.email

    fun readState(): SyncState = runCatching {
        val o = JSONObject(stateFile.readText())
        val snap = HashMap<String, String>()
        o.optJSONObject("snapshot")?.let { s -> s.keys().forEach { k -> snap[k] = s.getString(k) } }
        SyncState(o.optLong("projectsRev"), o.optLong("sessionsRev"), o.optLong("lastSyncMs"), snap)
    }.getOrDefault(SyncState())

    private fun writeState(st: SyncState) {
        val snap = JSONObject()
        st.snapshot.forEach { (k, v) -> snap.put(k, v) }
        val o = JSONObject().put("projectsRev", st.projectsRev).put("sessionsRev", st.sessionsRev)
            .put("lastSyncMs", st.lastSyncMs).put("snapshot", snap)
        val tmp = File(dir, "cloud_sync_state.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(stateFile)) { stateFile.delete(); tmp.renameTo(stateFile) }
    }

    val lastSyncMs: Long get() = readState().lastSyncMs

    /** Signs in (or creates the account). Returns a message when the email still needs confirming. */
    fun signIn(email: String, password: String, create: Boolean): String? {
        if (create) {
            val s = CloudAuth.signUp(email, password)
            if (s == null) return "Account created. Open the confirmation link we emailed to $email, then come back and tap Sign in."
            saveSession(s)
        } else {
            saveSession(CloudAuth.signIn(email, password))
        }
        // A different account starts a fresh sync history.
        stateFile.delete()
        return null
    }

    fun signOut() {
        saveSession(null)
        stateFile.delete()
    }

    private fun validSession(): CloudSession {
        val s = session ?: throw CloudException("Not signed in")
        if (System.currentTimeMillis() < s.expiresAtMs - 120_000L) return s
        return try {
            CloudAuth.refresh(s).also { saveSession(it) }
        } catch (e: CloudException) {
            if (e.code in 400..499) saveSession(null)
            throw e
        }
    }

    /**
     * Sends local changes, downloads changes from your other devices and merges them.
     * [current] gives the newest local data; [update] saves a change to it.
     */
    @Synchronized
    fun syncNow(current: () -> AppData, update: ((AppData) -> AppData) -> Unit): SyncResult {
        val s = validSession()
        val result = SyncEngine.run(current(), readState(), SupabaseRemote(s), System.currentTimeMillis())
        update(result.apply)
        writeState(result.newState)
        return result
    }
}

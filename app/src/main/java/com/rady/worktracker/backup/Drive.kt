package com.rady.worktracker.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DriveAuth {
    /** Only lets Ydar Track see the files it created itself — not the rest of your Drive. */
    const val SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_FILE)))
            .build()

    /** Access token without showing any screen, or null if you need to approve again. */
    suspend fun silentToken(context: Context): String? {
        val result = Identity.getAuthorizationClient(context).authorize(request()).await()
        return if (result.hasResolution()) null else result.accessToken
    }
}

data class DriveFile(val id: String, val name: String, val size: Long, val createdTime: String)

class DriveException(message: String, val code: Int) : IOException(message)

/** A small Google Drive REST client — just what backups need. */
class DriveClient(private val token: String) {
    companion object {
        const val FOLDER_NAME = "Ydar Track Backups"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.body(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw DriveException("Google Drive error $code: ${text.take(300)}", code)
        return text
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun email(): String? = withContext(Dispatchers.IO) {
        val o = JSONObject(open("$API/about?fields=user(emailAddress)").body())
        o.optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }
    }

    suspend fun folderId(): String = withContext(Dispatchers.IO) {
        val query = "name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val found = JSONObject(open("$API/files?q=${q(query)}&fields=files(id,name)&spaces=drive").body())
        val files = found.optJSONArray("files")
        if (files != null && files.length() > 0) return@withContext files.getJSONObject(0).getString("id")

        val conn = open("$API/files?fields=id", "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", "application/vnd.google-apps.folder")
        conn.outputStream.use { it.write(meta.toString().toByteArray()) }
        JSONObject(conn.body()).getString("id")
    }

    /** Uploads one file (multipart upload; backups are small). */
    suspend fun upload(folderId: String, name: String, mime: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val boundary = "ydar${System.nanoTime()}"
        val meta = JSONObject().put("name", name).put("mimeType", mime).put("parents", JSONArray().put(folderId))
        val conn = open("$UPLOAD/files?uploadType=multipart&fields=id", "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        conn.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
            out.write("--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        JSONObject(conn.body()).getString("id")
    }

    suspend fun list(folderId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and trashed=false"
        val o = JSONObject(
            open("$API/files?q=${q(query)}&orderBy=createdTime%20desc&pageSize=200&fields=files(id,name,size,createdTime)").body()
        )
        val arr = o.optJSONArray("files") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            DriveFile(f.getString("id"), f.optString("name"), f.optString("size").toLongOrNull() ?: 0L, f.optString("createdTime"))
        }
    }

    suspend fun download(id: String): ByteArray = withContext(Dispatchers.IO) {
        val conn = open("$API/files/$id?alt=media")
        if (conn.responseCode !in 200..299) conn.body()
        conn.inputStream.use { it.readBytes() }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val conn = open("$API/files/$id", "DELETE")
        val code = conn.responseCode
        if (code !in 200..299 && code != 404) conn.body()
        Unit
    }
}

/** Backup files: all projects, sessions, goals and the running timer, as JSON. */
object BackupFile {
    const val PREFIX = "YdarTrack-backup-"

    fun name(now: Long = System.currentTimeMillis()): String =
        PREFIX + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(now)) + ".json"

    fun create(data: AppData): ByteArray {
        val s = AppSettings.current
        val o = JSONObject()
            .put("app", "Ydar Track")
            .put("format", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("data", Store.toJson(data))
            .put("settings", JSONObject().put("dailyGoalMin", s.dailyGoalMin).put("lapOn", s.lapOn).put("lapMin", s.lapMin))
        return o.toString().toByteArray()
    }

    /** Reads a backup made by [create] (or a raw data file). */
    fun read(bytes: ByteArray): AppData {
        val o = JSONObject(String(bytes))
        return Store.fromJson(o.optJSONObject("data") ?: o)
    }
}

/** One backup to Drive — used by "Back up now" and by the scheduled worker. */
object DriveBackup {
    suspend fun run(context: Context, token: String, keep: Int): String {
        val client = DriveClient(token)
        val folder = client.folderId()
        val name = BackupFile.name()
        client.upload(folder, name, "application/json", BackupFile.create(Repo.current))
        // keep only the newest backups
        client.list(folder).filter { it.name.startsWith(BackupFile.PREFIX) }
            .drop(keep.coerceAtLeast(1))
            .forEach { runCatching { client.delete(it.id) } }
        AppSettings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupStatus = "OK") }
        return name
    }
}

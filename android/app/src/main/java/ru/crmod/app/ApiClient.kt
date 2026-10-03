package ru.crmod.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ModelItem(
    val id: Int,
    val name: String,
    val description: String,
    val filename: String,
    val size: Long,
    val labels: List<String>,
)

class ApiClient(server: String) {
    private val server = server.trimEnd('/')
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun models(): List<ModelItem> {
        val request = Request.Builder().url("$server/api/models/?active=1").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Сервер ответил ${response.code}")
            val array = JSONArray(text)
            return List(array.length()) { index -> parse(array.getJSONObject(index)) }
        }
    }

    private fun parse(item: JSONObject): ModelItem {
        val labels = item.optJSONArray("labels")
        return ModelItem(
            id = item.getInt("id"),
            name = item.getString("name"),
            description = item.optString("description"),
            filename = item.optString("filename"),
            size = item.optLong("size"),
            labels = if (labels == null) emptyList() else List(labels.length()) { labels.optString(it) },
        )
    }

    fun download(id: Int, target: File) {
        val request = Request.Builder().url("$server/api/models/$id/file/").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Сервер ответил ${response.code}")
            val body = response.body ?: throw IllegalStateException("Пустой ответ")
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            part.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            if (part.length() <= 0) {
                part.delete()
                throw IllegalStateException("Файл модели пустой")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IllegalStateException("Не удалось сохранить файл")
        }
    }
}

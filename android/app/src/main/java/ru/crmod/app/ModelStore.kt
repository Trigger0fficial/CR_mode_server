package ru.crmod.app

import android.content.Context
import org.json.JSONArray
import java.io.File

data class LocalModel(val file: File, val labels: List<String>)

/** Скачанные модели и их классы лежат в папке приложения. */
object ModelStore {
    fun dir(context: Context) = File(context.filesDir, "models").also { it.mkdirs() }

    fun modelFile(context: Context, id: Int) = File(dir(context), "$id.onnx")

    fun labelsFile(context: Context, id: Int) = File(dir(context), "$id.labels.json")

    fun isReady(context: Context, id: Int) = modelFile(context, id).length() > 0

    fun saveLabels(context: Context, id: Int, labels: List<String>) {
        val array = JSONArray()
        labels.forEach { array.put(it) }
        labelsFile(context, id).writeText(array.toString())
    }

    fun labels(context: Context, id: Int): List<String> {
        val file = labelsFile(context, id)
        if (!file.isFile) return emptyList()
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return List(array.length()) { array.optString(it) }
    }

    fun remove(context: Context, id: Int) {
        modelFile(context, id).delete()
        labelsFile(context, id).delete()
    }

    fun selected(context: Context): LocalModel? {
        val id = Prefs.selectedId(context)
        if (id < 0) return null
        val file = modelFile(context, id)
        if (file.length() <= 0) return null
        return LocalModel(file, labels(context, id))
    }
}

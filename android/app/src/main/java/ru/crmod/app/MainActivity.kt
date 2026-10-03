package ru.crmod.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import ru.crmod.app.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ModelsAdapter
    private val clock = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            renderPower()
            clock.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = ModelsAdapter { item -> onModel(item) }
        adapter.context = this
        adapter.selectedId = Prefs.selectedId(this)
        binding.models.layoutManager = LinearLayoutManager(this)
        binding.models.adapter = adapter
        binding.power.setOnClickListener { toggle() }
        binding.server.setText(Prefs.server(this))
        binding.serverSave.setOnClickListener {
            Prefs.saveServer(this, binding.server.text.toString())
            binding.server.setText(Prefs.server(this))
            load()
        }
        askNotifications()
        load()
    }

    override fun onResume() {
        super.onResume()
        clock.post(tick)
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        clock.removeCallbacks(tick)
        super.onPause()
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
    }

    private fun load() {
        val client = ApiClient(Prefs.server(this))
        binding.status.text = "Загружаю список моделей…"
        thread {
            try {
                val items = client.models()
                runOnUiThread {
                    adapter.selectedId = Prefs.selectedId(this)
                    adapter.submit(items)
                    renderStatus(items)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    binding.status.text = "Нет связи с сервером"
                    Toast.makeText(this, error.message ?: "Нет связи с сервером", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderStatus(items: List<ModelItem>) {
        val selected = items.firstOrNull { it.id == Prefs.selectedId(this) }
        binding.status.text = when {
            selected != null && ModelStore.isReady(this, selected.id) -> "Включена: ${selected.name}"
            items.isEmpty() -> "На сервере нет активных моделей"
            else -> "Модель не включена"
        }
    }

    private fun onModel(item: ModelItem) {
        if (!item.filename.endsWith(".onnx", ignoreCase = true)) {
            Toast.makeText(this, "Телефон запускает только .onnx", Toast.LENGTH_LONG).show()
            return
        }
        if (!ModelStore.isReady(this, item.id)) {
            download(item)
            return
        }
        Prefs.select(this, item.id)
        adapter.selectedId = item.id
        adapter.notifyDataSetChanged()
        binding.status.text = "Включена: ${item.name}"
    }

    private fun download(item: ModelItem) {
        adapter.busyId = item.id
        adapter.notifyDataSetChanged()
        val client = ApiClient(Prefs.server(this))
        thread {
            try {
                client.download(item.id, ModelStore.modelFile(this, item.id))
                ModelStore.saveLabels(this, item.id, item.labels)
                runOnUiThread {
                    adapter.busyId = -1
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Скачано: ${item.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                ModelStore.remove(this, item.id)
                runOnUiThread {
                    adapter.busyId = -1
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, error.message ?: "Скачивание не удалось", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggle() {
        if (OverlayService.running) {
            stopService(Intent(this, OverlayService::class.java))
            renderPower()
            return
        }
        if (ModelStore.selected(this) == null) {
            Toast.makeText(this, "Сначала скачайте и включите модель", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
            Toast.makeText(this, "Разрешите показ поверх других окон", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        renderPower()
    }

    private fun renderPower() {
        val running = OverlayService.running
        binding.power.setBackgroundResource(if (running) R.drawable.bg_round_on else R.drawable.bg_round)
        binding.powerIcon.setImageResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
        binding.timer.setTextColor(if (running) getColor(R.color.accent) else getColor(R.color.ink))
        binding.hint.text = when {
            !running -> "Нажмите, чтобы показать плашку поверх игры"
            Battle.active -> "Бой идёт, карты распознаются"
            else -> "Откройте бой и нажмите «В бой» на плашке"
        }
        if (!running || OverlayService.startedAt == 0L) {
            if (!running) binding.timer.text = "00:00"
            return
        }
        val seconds = ((System.currentTimeMillis() - OverlayService.startedAt) / 1000).toInt()
        binding.timer.text = "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

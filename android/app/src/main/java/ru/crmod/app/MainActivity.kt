package ru.crmod.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
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
    private var shown: Screen? = null
    private var iconColor = 0

    /** Три состояния экрана: всё выключено, плашка висит, идёт бой. */
    private enum class Screen { OFF, READY, BATTLE }

    private val tick = object : Runnable {
        override fun run() {
            render()
            clock.postDelayed(this, 500)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        iconColor = getColor(R.color.muted)
        adapter = ModelsAdapter(this) { item -> onModel(item) }
        adapter.selectedId = Prefs.selectedId(this)
        binding.models.layoutManager = LinearLayoutManager(this)
        binding.models.adapter = adapter
        binding.models.itemAnimator?.apply {
            changeDuration = Motion.NORMAL
            moveDuration = Motion.NORMAL
        }

        binding.power.setOnClickListener { toggle() }
        binding.power.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> Motion.press(view, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Motion.press(view, false)
            }
            false
        }
        binding.reload.setOnClickListener {
            binding.reload.animate()
                .rotationBy(360f)
                .setDuration(Motion.SLOW)
                .setInterpolator(Motion.ease)
                .start()
            load()
        }

        askNotifications()
        render()
        load()
    }

    override fun onResume() {
        super.onResume()
        clock.post(tick)
        adapter.refreshAll()
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
        val client = ApiClient()
        say("Загружаю список моделей…")
        thread {
            try {
                val items = client.models()
                runOnUiThread {
                    adapter.selectedId = Prefs.selectedId(this)
                    adapter.submit(items)
                    say(
                        when {
                            items.isEmpty() -> "На сервере нет готовых моделей"
                            items.any { ModelStore.isReady(this, it.id) } -> ""
                            else -> "Нажмите на модель, чтобы скачать её на телефон"
                        }
                    )
                }
            } catch (error: Exception) {
                runOnUiThread { say(error.message ?: "Нет связи с сервером") }
            }
        }
    }

    private fun say(text: String) {
        Motion.retext(binding.status, text)
        binding.status.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun onModel(item: ModelItem) {
        if (adapter.busyId >= 0) return
        if (!ModelStore.isReady(this, item.id)) {
            download(item)
            return
        }
        if (Prefs.selectedId(this) == item.id && Battle.active) {
            // Иконка «стоп» у работающей модели останавливает распознавание.
            stopService(Intent(this, CaptureService::class.java))
            return
        }
        val before = adapter.selectedId
        Prefs.select(this, item.id)
        adapter.selectedId = item.id
        adapter.refresh(item.id)
        if (before != item.id) adapter.refresh(before)
        say("")
    }

    private fun download(item: ModelItem) {
        adapter.busyId = item.id
        adapter.progress = 0
        adapter.refresh(item.id)
        val client = ApiClient()
        thread {
            try {
                client.download(item.id, ModelStore.modelFile(this, item.id)) { percent ->
                    runOnUiThread {
                        adapter.progress = percent
                        adapter.refresh(item.id)
                    }
                }
                ModelStore.saveLabels(this, item.id, item.labels)
                runOnUiThread {
                    adapter.busyId = -1
                    // Скачанную модель сразу включаем: отдельное нажатие не нужно.
                    val before = adapter.selectedId
                    Prefs.select(this, item.id)
                    adapter.selectedId = item.id
                    adapter.refresh(item.id)
                    if (before != item.id) adapter.refresh(before)
                    say("")
                }
            } catch (error: Exception) {
                ModelStore.remove(this, item.id)
                runOnUiThread {
                    adapter.busyId = -1
                    adapter.refresh(item.id)
                    Toast.makeText(this, error.message ?: "Скачивание не удалось", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggle() {
        if (OverlayService.running) {
            stopService(Intent(this, OverlayService::class.java))
            render()
            return
        }
        if (ModelStore.selected(this) == null) {
            Toast.makeText(this, "Сначала скачайте и включите модель", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            Toast.makeText(this, "Разрешите показ поверх других окон", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        render()
    }

    private fun render() {
        val screen = when {
            !OverlayService.running -> Screen.OFF
            Battle.active -> Screen.BATTLE
            else -> Screen.READY
        }
        renderTimer(screen)
        if (screen == shown) return
        shown = screen

        binding.rings.setActive(screen != Screen.OFF)
        val accent = getColor(if (screen == Screen.OFF) R.color.muted else R.color.accent)
        Motion.recolor(binding.powerIcon, iconColor, accent)
        iconColor = accent
        Motion.retext(
            binding.state,
            getString(
                when (screen) {
                    Screen.OFF -> R.string.state_off
                    Screen.READY -> R.string.state_ready
                    Screen.BATTLE -> R.string.state_battle
                }
            )
        )
        binding.state.setTextColor(accent)
        Motion.retext(
            binding.hint,
            getString(
                when (screen) {
                    Screen.OFF -> R.string.hint_off
                    Screen.READY -> R.string.hint_ready
                    Screen.BATTLE -> R.string.hint_battle
                }
            )
        )
        adapter.refreshAll()
    }

    private fun renderTimer(screen: Screen) {
        if (screen == Screen.OFF || OverlayService.startedAt == 0L) {
            binding.timer.text = getString(R.string.zero_time)
            return
        }
        val seconds = ((System.currentTimeMillis() - OverlayService.startedAt) / 1000).toInt()
        binding.timer.text = "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

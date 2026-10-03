package ru.crmod.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var chip: TextView? = null
    private var layer: DetectionView? = null

    private val windows get() = getSystemService(WINDOW_SERVICE) as WindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, note("Помощник запущен"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        startedAt = System.currentTimeMillis()
        running = true
        addChip()
        Battle.onState = { active -> main.post { renderChip(active) } }
        Battle.onBoxes = { boxes, width, height -> layer?.show(boxes, width, height) }
    }

    override fun onDestroy() {
        Battle.onState = null
        Battle.onBoxes = null
        stopService(Intent(this, CaptureService::class.java))
        chip?.let { runCatching { windows.removeView(it) } }
        layer?.let { runCatching { windows.removeView(it) } }
        chip = null
        layer = null
        running = false
        startedAt = 0L
        super.onDestroy()
    }

    private fun onChipClick() {
        if (Battle.active) {
            stopService(Intent(this, CaptureService::class.java))
            Battle.stopped()
            return
        }
        startActivity(
            Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun addChip() {
        val view = TextView(this).apply {
            textSize = 16f
            setPadding(36, 18, 36, 18)
            setOnClickListener { onChipClick() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 48
        windows.addView(view, params)
        chip = view
        renderChip(Battle.active)
    }

    private fun renderChip(active: Boolean) {
        chip?.apply {
            text = if (active) "Бой закончен" else "В бой"
            setTextColor(if (active) 0xFFFF6B6B.toInt() else 0xFF3DDC84.toInt())
            setBackgroundResource(R.drawable.bg_chip)
        }
        if (active) addLayer() else removeLayer()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, note(if (active) "Бой идёт, карты распознаются" else "Готов к бою"))
    }

    private fun addLayer() {
        if (layer != null) return
        val view = DetectionView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        windows.addView(view, params)
        // Слой не должен перехватывать плашку, поэтому возвращаем её наверх.
        chip?.let { windows.removeView(it) }
        chip?.let { addChipBack(it) }
        layer = view
    }

    private fun addChipBack(view: View) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 48
        windows.addView(view, params)
    }

    private fun removeLayer() {
        layer?.let { runCatching { windows.removeView(it) } }
        layer = null
    }

    private fun note(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("overlay", "Помощник", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "overlay")
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("CR")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        @Volatile var running = false
        @Volatile var startedAt = 0L
    }
}

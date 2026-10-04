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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import ru.crmod.app.databinding.ChipBattleBinding

class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var chip: ChipBattleBinding? = null
    private var layer: DetectionView? = null
    private var painted: Boolean? = null

    private val windows get() = getSystemService(WINDOW_SERVICE) as WindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, note("Помощник запущен"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        startedAt = System.currentTimeMillis()
        running = true
        addLayer()
        addChip()
        Battle.onState = { active -> main.post { renderChip(active) } }
        Battle.onBoxes = { boxes, width, height -> layer?.show(boxes, width, height) }
    }

    override fun onDestroy() {
        Battle.onState = null
        Battle.onBoxes = null
        stopService(Intent(this, CaptureService::class.java))
        chip?.root?.let { view -> Motion.disappear(view) { drop(view) } }
        chip = null
        layer?.let { drop(it) }
        layer = null
        running = false
        startedAt = 0L
        super.onDestroy()
    }

    private fun drop(view: View) {
        runCatching { windows.removeView(view) }
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
        val binding = ChipBattleBinding.inflate(LayoutInflater.from(this))
        binding.root.setOnClickListener { onChipClick() }
        binding.root.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> Motion.press(view, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Motion.press(view, false)
            }
            false
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
        params.windowAnimations = 0
        windows.addView(binding.root, params)
        chip = binding
        paint(Battle.active)
        Motion.appear(binding.root)
    }

    private fun renderChip(active: Boolean) {
        val binding = chip ?: return
        if (painted != active) Motion.swap(binding.root) { paint(active) }
        if (active) layer?.start() else layer?.stop()
        getSystemService(NotificationManager::class.java)
            .notify(1, note(if (active) "Бой идёт, карты распознаются" else "Готов к бою"))
    }

    private fun paint(active: Boolean) {
        val binding = chip ?: return
        painted = active
        val color = getColor(if (active) R.color.danger else R.color.accent)
        binding.label.setText(if (active) R.string.battle_stop else R.string.battle_start)
        binding.label.setTextColor(color)
        binding.icon.setImageResource(if (active) R.drawable.ic_stop else R.drawable.ic_bolt)
        binding.icon.setColorFilter(color)
    }

    private fun addLayer() {
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
        params.windowAnimations = 0
        windows.addView(view, params)
        layer = view
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
            .setSmallIcon(R.drawable.ic_bolt)
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

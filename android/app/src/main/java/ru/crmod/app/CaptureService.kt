package ru.crmod.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

/** Снимает экран и прогоняет кадры через модель на самом телефоне. */
class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var detector: Detector? = null
    private var frame: Bitmap? = null
    private var busy = false
    private var lastRun = 0L
    private var frames = 0
    private var spent = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        // Повторный запуск не должен оставить второй поток на той же модели:
        // две сессии ONNX на одних буферах дают битые кадры.
        release()
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        if (code == 0 || data == null) {
            Log.e(TAG, "CAPTURE_MISSING")
            stopSelf()
            return START_NOT_STICKY
        }

        val model = ModelStore.selected(this)
        if (model == null) {
            Toast.makeText(this, "Модель не включена", Toast.LENGTH_LONG).show()
            Log.e(TAG, "CAPTURE_NO_MODEL")
            stopSelf()
            return START_NOT_STICKY
        }
        detector = try {
            Detector(model.file, model.labels)
        } catch (error: Throwable) {
            Log.e(TAG, "DETECTOR_FAILED ${error.message}")
            Toast.makeText(this, "Модель не запускается: ${error.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        Log.i(TAG, "DETECTOR_READY classes=${model.labels.size}")

        val metrics = resources.displayMetrics
        val scale = maxOf(1, minOf(metrics.widthPixels, metrics.heightPixels) / TARGET_SHORT_SIDE)
        val width = metrics.widthPixels / scale
        val height = metrics.heightPixels / scale

        val handlerThread = HandlerThread("capture").also { it.start() }
        worker = handlerThread
        val handler = Handler(handlerThread.looper)

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "CAPTURE_STOP")
                stopSelf()
            }
        }, handler)

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ images -> onFrame(images, height) }, handler)
        display = projection?.createVirtualDisplay(
            "crmod",
            width,
            height,
            metrics.densityDpi / scale,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            handler,
        )
        Battle.started()
        Log.i(TAG, "CAPTURE_STARTED ${width}x$height")
        return START_NOT_STICKY
    }

    private fun onFrame(images: ImageReader, height: Int) {
        val image = images.acquireLatestImage() ?: return
        val now = System.currentTimeMillis()
        if (busy || now - lastRun < MIN_GAP_MS) {
            image.close()
            return
        }
        busy = true
        lastRun = now
        try {
            val plane = image.planes[0]
            val stride = plane.rowStride / plane.pixelStride
            val target = frame?.takeIf { it.width == stride && it.height == height }
                ?: Bitmap.createBitmap(stride, height, Bitmap.Config.ARGB_8888).also { frame = it }
            target.copyPixelsFromBuffer(plane.buffer)
            image.close()

            val boxes = detector?.detect(target).orEmpty()
            frames++
            spent += System.currentTimeMillis() - now
            if (frames == 1 || frames % REPORT_EVERY == 0) {
                Log.i(TAG, "INFERENCE $frames кадров, в среднем ${spent / frames} мс, рамок ${boxes.size}")
            }
            Battle.publish(boxes, stride, height)
        } catch (error: Throwable) {
            Log.e(TAG, "FRAME_FAILED ${error.message}")
            runCatching { image.close() }
        } finally {
            busy = false
        }
    }

    override fun onDestroy() {
        release()
        Battle.stopped()
        super.onDestroy()
    }

    private fun release() {
        display?.release()
        reader?.close()
        projection?.stop()
        // Поток гасим раньше детектора, иначе он может войти в закрытую сессию.
        worker?.quitSafely()
        worker?.join(300)
        detector?.close()
        frame?.recycle()
        display = null
        reader = null
        projection = null
        worker = null
        detector = null
        frame = null
        busy = false
        frames = 0
        spent = 0L
    }

    private fun note(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("capture", "Захват", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, "capture")
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("Бой")
            .setContentText("Карты распознаются на телефоне")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "CRMod"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        /** Короткая сторона кадра: меньше пикселей — быстрее инференс. */
        const val TARGET_SHORT_SIDE = 540
        const val MIN_GAP_MS = 120L
        const val REPORT_EVERY = 10
    }
}

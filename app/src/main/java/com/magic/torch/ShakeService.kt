package com.magic.torch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.sqrt

class ShakeService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var cameraManager: CameraManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val gravity = FloatArray(3)
    private var torchState = false
    private var lastShake = 0L
    private var lastStrongAcceleration = 0L
    private var flashCameraId: String? = null

    override fun onCreate() {
        super.onCreate()

        createNotification()
        acquireWakeLock()

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        cameraManager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager

        flashCameraId = findFlashCameraId()

        val sensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensor == null || flashCameraId == null) {
            stopSelf()
            return
        }

        sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        updateGravity(event.values)

        val linearX = event.values[0] - gravity[0]
        val linearY = event.values[1] - gravity[1]
        val linearZ = event.values[2] - gravity[2]

        val acceleration =
            sqrt(
                (linearX * linearX + linearY * linearY + linearZ * linearZ)
                    .toDouble()
            )

        if (acceleration <= SHAKE_THRESHOLD) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val isConfirmedShake =
            currentTime - lastStrongAcceleration <= SHAKE_CONFIRMATION_WINDOW_MS

        lastStrongAcceleration = currentTime

        if (isConfirmedShake && currentTime - lastShake > SHAKE_COOLDOWN_MS) {
            lastShake = currentTime
            lastStrongAcceleration = 0L
            toggleTorch()
        }
    }

    private fun updateGravity(values: FloatArray) {
        gravity[0] = gravity[0] * GRAVITY_FILTER_ALPHA +
            values[0] * (1 - GRAVITY_FILTER_ALPHA)
        gravity[1] = gravity[1] * GRAVITY_FILTER_ALPHA +
            values[1] * (1 - GRAVITY_FILTER_ALPHA)
        gravity[2] = gravity[2] * GRAVITY_FILTER_ALPHA +
            values[2] * (1 - GRAVITY_FILTER_ALPHA)
    }

    private fun toggleTorch() {
        val cameraId = flashCameraId ?: return

        try {
            torchState = !torchState

            cameraManager.setTorchMode(
                cameraId,
                torchState
            )
        } catch (e: Exception) {
            torchState = false
            e.printStackTrace()
        }
    }

    private fun findFlashCameraId(): String? {
        val backCameraWithFlash = cameraManager.cameraIdList.firstOrNull { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
        }

        return backCameraWithFlash ?: cameraManager.cameraIdList.firstOrNull { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    private fun createNotification() {
        val channelId = NOTIFICATION_CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build()

        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            notification,
            foregroundServiceType()
        )
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:ShakeTorchSensor"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
    }

    override fun onDestroy() {
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }

        if (::cameraManager.isInitialized) {
            flashCameraId?.let { cameraId ->
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "shake_torch"
        private const val GRAVITY_FILTER_ALPHA = 0.8f
        private const val SHAKE_THRESHOLD = 19.5
        private const val SHAKE_CONFIRMATION_WINDOW_MS = 350L
        private const val SHAKE_COOLDOWN_MS = 1500L
    }
}

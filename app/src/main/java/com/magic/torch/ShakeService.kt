package com.magic.torch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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

    private var torchState = false
    private var lastShake = 0L
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

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration =
            sqrt((x * x + y * y + z * z).toDouble())

        if (acceleration > SHAKE_THRESHOLD) {
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastShake > SHAKE_COOLDOWN_MS) {
                lastShake = currentTime
                toggleTorch()
            }
        }
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

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_camera)
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
        private const val SHAKE_THRESHOLD = 15.0
        private const val SHAKE_COOLDOWN_MS = 1000L
    }
}

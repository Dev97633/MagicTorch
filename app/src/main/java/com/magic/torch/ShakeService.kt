```kotlin id="twfzva"
package com.magic.torch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class ShakeService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var cameraManager: CameraManager

    private var torchState = false
    private var lastShake = 0L

    override fun onCreate() {
        super.onCreate()

        createNotification()

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        cameraManager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val sensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {

        event ?: return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration =
            sqrt((x * x + y * y + z * z).toDouble())

        if (acceleration > 15) {

            val currentTime = System.currentTimeMillis()

            if (currentTime - lastShake > 1000) {

                lastShake = currentTime

                toggleTorch()
            }
        }
    }

    private fun toggleTorch() {

        try {

            val cameraId = cameraManager.cameraIdList[0]

            torchState = !torchState

            cameraManager.setTorchMode(
                cameraId,
                torchState
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotification() {

        val channelId = "shake_torch"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                channelId,
                "Shake Torch",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("Shake Torch Running")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()

        startForeground(1, notification)
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
```

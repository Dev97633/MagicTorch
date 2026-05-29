package com.magic.torch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            startShakeTorchWithPermissions()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, ShakeService::class.java))
            statusText.text = getString(R.string.shake_torch_stopped)
        }

        if (!hasCameraPermission()) {
            requestRuntimePermissions()
        } else {
            requestNotificationPermissionIfNeeded()
            statusText.text = getString(R.string.ready_to_start)
        }
    }

    private fun startShakeTorchWithPermissions() {
        if (!hasCameraPermission()) {
            requestRuntimePermissions()
            return
        }

        if (!hasFlash()) {
            statusText.text = getString(R.string.no_flash_available)
            Toast.makeText(
                this,
                R.string.no_flash_available,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        requestNotificationPermissionIfNeeded()
        startTorchService()
    }

    private fun requestRuntimePermissions() {
        ActivityCompat.requestPermissions(
            this,
            requiredRuntimePermissions(),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requiredRuntimePermissions(): Array<String> {
        return arrayOf(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFlash(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasCameraPermission()) {
                startShakeTorchWithPermissions()
            } else {
                statusText.text = getString(R.string.camera_permission_required)
                Toast.makeText(
                    this,
                    R.string.camera_permission_required,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startTorchService() {
        val intent = Intent(this, ShakeService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        statusText.text = getString(R.string.shake_torch_running)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }
}

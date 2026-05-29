package com.magic.torch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var hideIconSwitch: SwitchCompat
    private var updatingHideIconSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        hideIconSwitch = findViewById(R.id.hideIconSwitch)

        configureHideIconSwitch()

        findViewById<Button>(R.id.startButton).setOnClickListener {
            startShakeTorchWithPermissions()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, ShakeService::class.java))
            statusText.text = getString(R.string.shake_torch_stopped)
        }

        if (intent.getBooleanExtra(EXTRA_RESTORED_FROM_SECRET_CODE, false)) {
            statusText.text = getString(R.string.launcher_icon_restored)
        } else if (!hasCameraPermission()) {
            requestRuntimePermissions()
        } else {
            requestNotificationPermissionIfNeeded()
            statusText.text = getString(R.string.ready_to_start)
        }
    }

    private fun configureHideIconSwitch() {
        updateHideIconSwitch(
            LauncherIconController.isLauncherIconHidden(this)
        )

        hideIconSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingHideIconSwitch) {
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                confirmHideLauncherIcon()
            } else {
                setLauncherIconHidden(false)
            }
        }
    }

    private fun confirmHideLauncherIcon() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hide_launcher_icon_title)
            .setMessage(
                getString(
                    R.string.hide_launcher_icon_confirmation,
                    LauncherIconController.RECOVERY_DIAL_CODE
                )
            )
            .setPositiveButton(R.string.hide_launcher_icon_confirm) { _, _ ->
                setLauncherIconHidden(true)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                updateHideIconSwitch(false)
            }
            .setOnCancelListener {
                updateHideIconSwitch(false)
            }
            .show()
    }

    private fun setLauncherIconHidden(hidden: Boolean) {
        LauncherIconController.setLauncherIconHidden(
            this,
            hidden
        )
        updateHideIconSwitch(hidden)

        val message = if (hidden) {
            getString(
                R.string.launcher_icon_hidden,
                LauncherIconController.RECOVERY_DIAL_CODE
            )
        } else {
            getString(R.string.launcher_icon_restored)
        }

        statusText.text = message
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateHideIconSwitch(hidden: Boolean) {
        updatingHideIconSwitch = true
        hideIconSwitch.isChecked = hidden
        updatingHideIconSwitch = false
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
        const val EXTRA_RESTORED_FROM_SECRET_CODE = "restored_from_secret_code"
    }
}

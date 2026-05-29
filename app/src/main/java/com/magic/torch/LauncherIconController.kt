package com.magic.torch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherIconController {

    const val RECOVERY_SECRET_CODE = "62442"
    const val RECOVERY_DIAL_CODE = "*#*#62442#*#*"

    fun isLauncherIconHidden(context: Context): Boolean {
        return context.packageManager.getComponentEnabledSetting(
            launcherComponent(context)
        ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setLauncherIconHidden(
        context: Context,
        hidden: Boolean
    ) {
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        context.packageManager.setComponentEnabledSetting(
            launcherComponent(context),
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun launcherComponent(context: Context): ComponentName {
        return ComponentName(
            context,
            "${context.packageName}.LauncherActivity"
        )
    }
}

package com.magic.torch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != SECRET_CODE_ACTION ||
            intent.data?.host != LauncherIconController.RECOVERY_SECRET_CODE
        ) {
            return
        }

        LauncherIconController.setLauncherIconHidden(
            context,
            hidden = false
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_RESTORED_FROM_SECRET_CODE, true)
        }

        context.startActivity(launchIntent)
    }

    companion object {
        private const val SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE"
    }
}

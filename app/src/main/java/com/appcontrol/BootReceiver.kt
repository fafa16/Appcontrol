package com.appcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance la surveillance au démarrage du téléphone de l'enfant. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.role(context) == Prefs.CHILD && Prefs.code(context) != null) {
            BlockService.start(context)
        }
    }
}

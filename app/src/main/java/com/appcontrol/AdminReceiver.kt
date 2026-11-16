package com.appcontrol

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Administrateur d'appareil (sur le téléphone de l'enfant) : tant qu'il est actif,
 * Android refuse de désinstaller l'appli. Pour la désinstaller, il faut d'abord le
 * désactiver dans les réglages, et le parent en est averti.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Le parent sera prévenu si le contrôle parental est désactivé."

    override fun onDisabled(context: Context, intent: Intent) {
        val code = Prefs.code(context) ?: return
        FirebaseFirestore.getInstance().collection("devices").document(code)
            .set(mapOf("adminActive" to false), SetOptions.merge())
    }
}

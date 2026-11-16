package com.appcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/**
 * Tourne sur le téléphone de l'enfant.
 *
 * - Écoute le document Firestore "devices/<code>" (champ "blocked").
 * - blocked = true  -> ouvre un VPN local qui absorbe TOUT le trafic (rien n'est relayé)
 *                      => plus aucune connexion (Wi-Fi comme données mobiles).
 * - blocked = false -> ferme le VPN, tout redevient normal.
 *
 * Sa propre appli est exclue du VPN : elle peut donc toujours recevoir l'ordre "débloquer".
 */
class BlockService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var listener: ListenerRegistration? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val code = Prefs.code(this)
        if (code == null || Prefs.role(this) != Prefs.CHILD) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Applique tout de suite le dernier état connu (utile au redémarrage, sans réseau).
        apply(Prefs.blocked(this), code, report = false)
        if (listener == null) {
            listener = FirebaseFirestore.getInstance().collection("devices").document(code)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w(TAG, "Firestore: $err")
                        return@addSnapshotListener
                    }
                    apply(snap?.getBoolean("blocked") ?: false, code, report = true)
                }
        }
        return START_STICKY
    }

    @Synchronized
    private fun apply(blocked: Boolean, code: String, report: Boolean) {
        Prefs.setBlocked(this, blocked)
        if (blocked && tunnel == null) {
            tunnel = try {
                Builder()
                    .setSession("AppControl")
                    .addAddress("10.255.0.1", 32)
                    .addRoute("0.0.0.0", 0)
                    .addAddress("fd00:1::1", 128)
                    .addRoute("::", 0)
                    .addDisallowedApplication(packageName) // notre appli reste connectée
                    .setBlocking(true)
                    .establish() // on ne lit jamais le tunnel : les paquets sont jetés
            } catch (e: Exception) {
                Log.e(TAG, "VPN impossible", e)
                null
            }
        } else if (!blocked) {
            closeTunnel()
        }
        if (report) {
            val actual = tunnel != null
            FirebaseFirestore.getInstance().collection("devices").document(code)
                .set(mapOf("applied" to actual), SetOptions.merge())
        }
    }

    private fun closeTunnel() {
        try { tunnel?.close() } catch (_: Exception) {}
        tunnel = null
    }

    /** Un autre VPN a pris la place, ou la permission a été retirée. */
    override fun onRevoke() {
        closeTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        listener?.remove()
        listener = null
        closeTunnel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Contrôle parental", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Contrôle parental actif")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        private const val TAG = "BlockService"
        private const val CHANNEL = "appcontrol"

        fun start(c: Context) {
            c.startForegroundService(Intent(c, BlockService::class.java))
        }
    }
}

package com.appcontrol

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private var parentListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        setContentView(root)
        render()
    }

    override fun onDestroy() {
        parentListener?.remove()
        super.onDestroy()
    }

    private fun render() {
        parentListener?.remove()
        parentListener = null
        root.removeAllViews()
        when (Prefs.role(this)) {
            Prefs.PARENT -> renderParent()
            Prefs.CHILD -> renderChild()
            else -> renderChoice()
        }
    }

    // ---------- Choix du rôle ----------

    private fun renderChoice() {
        root.addView(text("Qui utilise ce téléphone ?", 22f))
        root.addView(button("Je suis le PARENT") {
            Prefs.setRole(this, Prefs.PARENT)
            if (Prefs.code(this) == null) Prefs.setCode(this, Prefs.newCode())
            render()
        })
        root.addView(button("C'est le téléphone de l'ENFANT") {
            Prefs.setRole(this, Prefs.CHILD)
            render()
        })
    }

    // ---------- Parent ----------

    private fun renderParent() {
        val code = Prefs.code(this)!!
        val doc = FirebaseFirestore.getInstance().collection("devices").document(code)

        val status = text("…", 18f)
        val toggle = Button(this).apply { textSize = 22f; setPadding(32, 64, 32, 64) }
        var blocked = false

        parentListener = doc.addSnapshotListener { snap, _ ->
            blocked = snap?.getBoolean("blocked") ?: false
            val applied = snap?.getBoolean("applied")
            toggle.text = if (blocked) "DÉBLOQUER la connexion" else "BLOQUER la connexion"
            toggle.setBackgroundColor(if (blocked) Color.rgb(46, 160, 67) else Color.rgb(207, 34, 46))
            toggle.setTextColor(Color.WHITE)
            status.text = when {
                blocked && applied == true -> "Connexion de l'enfant : BLOQUÉE ✔"
                blocked -> "Ordre envoyé… en attente du téléphone de l'enfant"
                applied == false || applied == null -> "Connexion de l'enfant : normale"
                else -> "Déblocage en cours…"
            }
        }
        toggle.setOnClickListener {
            doc.set(mapOf("blocked" to !blocked), SetOptions.merge())
        }

        root.addView(text("Code à saisir sur le téléphone de l'enfant :", 16f))
        root.addView(text(code, 28f).apply { setTextIsSelectable(true) })
        root.addView(status)
        root.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 48 })
        root.addView(button("Changer de rôle") {
            Prefs.setRole(this, null); Prefs.setCode(this, null); render()
        })
    }

    // ---------- Enfant ----------

    private fun renderChild() {
        val code = Prefs.code(this)
        if (code == null) {
            val input = EditText(this).apply { hint = "Code du parent"; setSingleLine() }
            root.addView(text("Entre le code affiché sur le téléphone du parent", 18f))
            root.addView(input)
            root.addView(button("Associer") {
                val c = input.text.toString().trim().lowercase()
                if (c.length >= 12) { Prefs.setCode(this, c); render() }
            })
            root.addView(button("Retour") { Prefs.setRole(this, null); render() })
            return
        }
        root.addView(text("Contrôle parental actif sur ce téléphone.", 18f))
        askPermissionsThenStart()
    }

    private fun askPermissionsThenStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        val vpn = VpnService.prepare(this)
        if (vpn != null) startActivityForResult(vpn, REQ_VPN) else BlockService.start(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (rc == REQ_NOTIF) askPermissionsThenStart()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, result: Int, data: Intent?) {
        super.onActivityResult(rc, result, data)
        if (rc == REQ_VPN && result == RESULT_OK) BlockService.start(this)
    }

    // ---------- Petits helpers d'UI ----------

    private fun text(s: String, size: Float) = TextView(this).apply {
        text = s; textSize = size; gravity = Gravity.CENTER; setPadding(0, 24, 0, 24)
    }

    private fun button(s: String, onClick: (View) -> Unit) =
        Button(this).apply { text = s; setOnClickListener(onClick) }

    companion object {
        private const val REQ_NOTIF = 1
        private const val REQ_VPN = 2
    }
}

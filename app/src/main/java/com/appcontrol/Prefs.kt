package com.appcontrol

import android.content.Context
import java.security.SecureRandom

/** Réglages locaux : rôle du téléphone, code d'appairage, dernier état connu. */
object Prefs {
    const val PARENT = "parent"
    const val CHILD = "child"

    private fun p(c: Context) = c.getSharedPreferences("appcontrol", Context.MODE_PRIVATE)

    fun role(c: Context): String? = p(c).getString("role", null)
    fun setRole(c: Context, r: String?) = p(c).edit().putString("role", r).apply()

    fun code(c: Context): String? = p(c).getString("code", null)
    fun setCode(c: Context, code: String?) = p(c).edit().putString("code", code).apply()

    fun blocked(c: Context): Boolean = p(c).getBoolean("blocked", false)
    fun setBlocked(c: Context, b: Boolean) = p(c).edit().putBoolean("blocked", b).apply()

    /** Code secret de 12 caractères (le "mot de passe" du couple parent/enfant). */
    fun newCode(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..12).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }
}

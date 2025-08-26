package com.webviewapp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.*

class SessionManager(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_session_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_SESSION_URL = "session_url"
        private const val KEY_SESSION_TIMESTAMP = "session_timestamp"
        private const val KEY_SESSION_TIMEOUT = "session_timeout"
        private const val SESSION_VALIDITY_HOURS = 24L
    }
    
    fun saveSession(url: String) {
        val currentTime = System.currentTimeMillis()
        val expirationTime = currentTime + (SESSION_VALIDITY_HOURS * 60 * 60 * 1000)
        
        encryptedPrefs.edit()
            .putString(KEY_SESSION_URL, url)
            .putLong(KEY_SESSION_TIMESTAMP, currentTime)
            .putLong(KEY_SESSION_TIMEOUT, expirationTime)
            .apply()
    }
    
    fun getSavedUrl(): String? {
        return encryptedPrefs.getString(KEY_SESSION_URL, null)
    }
    
    fun isSessionValid(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expirationTime = encryptedPrefs.getLong(KEY_SESSION_TIMEOUT, 0)
        return currentTime < expirationTime && getSavedUrl() != null
    }
    
    fun clearSession() {
        encryptedPrefs.edit()
            .remove(KEY_SESSION_URL)
            .remove(KEY_SESSION_TIMESTAMP)
            .remove(KEY_SESSION_TIMEOUT)
            .apply()
    }
    
    fun getSessionAge(): Long {
        val sessionTime = encryptedPrefs.getLong(KEY_SESSION_TIMESTAMP, 0)
        return if (sessionTime > 0) {
            System.currentTimeMillis() - sessionTime
        } else {
            0
        }
    }
    
    fun extendSession() {
        val currentTime = System.currentTimeMillis()
        val newExpirationTime = currentTime + (SESSION_VALIDITY_HOURS * 60 * 60 * 1000)
        
        encryptedPrefs.edit()
            .putLong(KEY_SESSION_TIMEOUT, newExpirationTime)
            .apply()
    }
}
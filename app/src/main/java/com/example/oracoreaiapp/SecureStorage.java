package com.example.oracoreaiapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Secure storage helper using Android Keystore for encryption
 */
public class SecureStorage {
    private static final String TAG = "SecureStorage";
    private static final String PREFS_NAME = "secure_prefs";

    private SharedPreferences encryptedPrefs;
    private SharedPreferences fallbackPrefs;
    private Context context;

    public SecureStorage(Context context) {
        this.context = context;
        initializeStorage();
    }

    private void initializeStorage() {
        try {
            // Try to use EncryptedSharedPreferences (requires androidx.security:security-crypto)
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            Log.d(TAG, "Encrypted storage initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize encrypted storage, using fallback", e);
            // Fallback to regular SharedPreferences if encryption fails
            fallbackPrefs = context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE);
        }
    }

    /**
     * Store a string value securely
     */
    public void putString(String key, String value) {
        try {
            if (encryptedPrefs != null) {
                encryptedPrefs.edit().putString(key, value).apply();
            } else if (fallbackPrefs != null) {
                // Simple obfuscation for fallback (not secure, just hiding from casual viewing)
                String encoded = Base64.encodeToString(value.getBytes(), Base64.DEFAULT);
                fallbackPrefs.edit().putString(key, encoded).apply();
            }
            Log.d(TAG, "Stored value for key: " + key);
        } catch (Exception e) {
            Log.e(TAG, "Error storing value", e);
        }
    }

    /**
     * Retrieve a string value
     */
    public String getString(String key, String defaultValue) {
        try {
            if (encryptedPrefs != null) {
                return encryptedPrefs.getString(key, defaultValue);
            } else if (fallbackPrefs != null) {
                String encoded = fallbackPrefs.getString(key, null);
                if (encoded != null) {
                    return new String(Base64.decode(encoded, Base64.DEFAULT));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving value", e);
        }
        return defaultValue;
    }

    /**
     * Remove a value
     */
    public void remove(String key) {
        try {
            if (encryptedPrefs != null) {
                encryptedPrefs.edit().remove(key).apply();
            } else if (fallbackPrefs != null) {
                fallbackPrefs.edit().remove(key).apply();
            }
            Log.d(TAG, "Removed value for key: " + key);
        } catch (Exception e) {
            Log.e(TAG, "Error removing value", e);
        }
    }

    /**
     * Clear all stored values
     */
    public void clear() {
        try {
            if (encryptedPrefs != null) {
                encryptedPrefs.edit().clear().apply();
            } else if (fallbackPrefs != null) {
                fallbackPrefs.edit().clear().apply();
            }
            Log.d(TAG, "Cleared all values");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing values", e);
        }
    }

    /**
     * Check if a key exists
     */
    public boolean contains(String key) {
        try {
            if (encryptedPrefs != null) {
                return encryptedPrefs.contains(key);
            } else if (fallbackPrefs != null) {
                return fallbackPrefs.contains(key);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking key existence", e);
        }
        return false;
    }
}
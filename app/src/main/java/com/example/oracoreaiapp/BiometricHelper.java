package com.example.oracoreaiapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * Helper class for managing biometric authentication and credential storage
 */
public class BiometricHelper {
    private static final String TAG = "BiometricHelper";
    private static final String PREFS_NAME = "OracorePrefs";
    private static final String KEY_USER_LOGGED_IN = "user_logged_in";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_STORED_EMAIL = "stored_email";
    private static final String KEY_STORED_PASSWORD = "stored_password";
    private static final String KEY_SESSION_COOKIES = "session_cookies";
    private static final String KEY_LAST_LOGIN_TIME = "last_login_time";
    private static final long SESSION_TIMEOUT_HOURS = 24 * 7; // Session valid for 7 days

    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final BiometricAuthCallback callback;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    /**
     * Callback interface for biometric authentication events
     */
    public interface BiometricAuthCallback {
        void onBiometricAuthenticationSuccess();
        void onBiometricAuthenticationError(String error);
        void onBiometricAuthenticationFailed();
        void onBiometricAuthenticationCancelled();
    }

    /**
     * Enum for authentication flow decisions
     */
    public enum AuthenticationFlow {
        BIOMETRIC_PROMPT,      // Show biometric prompt
        AUTO_LOGIN_SESSION,    // Auto-login with stored session
        MANUAL_LOGIN          // Manual login required
    }

    /**
     * Constructor
     */
    public BiometricHelper(Context context, BiometricAuthCallback callback) {
        this.context = context;
        this.callback = callback;
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (context instanceof FragmentActivity) {
            setupBiometricPrompt((FragmentActivity) context);
        }
    }

    /**
     * Setup biometric prompt
     */
    private void setupBiometricPrompt(FragmentActivity activity) {
        Executor executor = ContextCompat.getMainExecutor(context);

        biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.e(TAG, "Authentication error: " + errString);

                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            callback.onBiometricAuthenticationCancelled();
                        } else {
                            callback.onBiometricAuthenticationError(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Authentication succeeded");
                        callback.onBiometricAuthenticationSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.w(TAG, "Authentication failed");
                        callback.onBiometricAuthenticationFailed();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Log in using your fingerprint")
                .setNegativeButtonText("Use password")
                .build();
    }

    /**
     * Store user credentials securely
     */
    public boolean storeCredentials(String email, String password) {
        Log.d(TAG, "Storing credentials - Email: " + email + ", Password length: " + password.length());

        try {
            // Use secure storage instead of plain text
            if (context instanceof MainActivity) {
                SecureStorage secureStorage = new SecureStorage(context);
                secureStorage.putString(KEY_STORED_EMAIL, email);
                secureStorage.putString(KEY_STORED_PASSWORD, password);
                
                // Also store in SharedPreferences for non-sensitive flags
                SharedPreferences.Editor editor = sharedPreferences.edit();

                boolean result = editor.commit(); // Use commit() for immediate storage
                Log.d(TAG, "Secure storage commit result: " + result);

                // Verify storage
                String verifyEmail = secureStorage.getString(KEY_STORED_EMAIL, "");
                String verifyPassword = secureStorage.getString(KEY_STORED_PASSWORD, "");
                Log.d(TAG, "Verification - Email stored: " + !verifyEmail.isEmpty() +
                        ", Password stored: " + !verifyPassword.isEmpty());

                return result && !verifyEmail.isEmpty() && !verifyPassword.isEmpty();
            } else {
                Log.e(TAG, "Context is not MainActivity, cannot store credentials securely");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error storing credentials", e);
            return false;
        }
    }

    /**
     * Get stored email
     */
    public String getStoredEmail() {
        try {
            SecureStorage secureStorage = new SecureStorage(context);
            String email = secureStorage.getString(KEY_STORED_EMAIL, "");
            Log.d(TAG, "Retrieved email: " + (email.isEmpty() ? "EMPTY" : email.substring(0, Math.min(3, email.length())) + "***"));
            return email;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving email from secure storage", e);
            return "";
        }
    }

    /**
     * Get stored password
     */
    public String getStoredPassword() {
        try {
            SecureStorage secureStorage = new SecureStorage(context);
            String password = secureStorage.getString(KEY_STORED_PASSWORD, "");
            Log.d(TAG, "Retrieved password length: " + password.length());
            return password;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving password from secure storage", e);
            return "";
        }
    }

    /**
     * Check if credentials are stored
     */
    public boolean hasStoredCredentials() {
        String email = getStoredEmail();
        String password = getStoredPassword();
        boolean hasCredentials = !email.isEmpty() && !password.isEmpty();
        Log.d(TAG, "Has stored credentials: " + hasCredentials);
        return hasCredentials;
    }

    /**
     * Store session cookies
     */
    public void storeSessionCookies(String cookies) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SESSION_COOKIES, cookies);
        editor.putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis());
        editor.apply();
        Log.d(TAG, "Session cookies stored");
    }

    /**
     * Get stored session cookies
     */
    public String getStoredSessionCookies() {
        return sharedPreferences.getString(KEY_SESSION_COOKIES, "");
    }

    /**
     * Clear stored session
     */
    public void clearStoredSession() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_USER_LOGGED_IN, false);
        editor.remove(KEY_SESSION_COOKIES);
        editor.remove(KEY_LAST_LOGIN_TIME);
        editor.apply();
        Log.d(TAG, "Session cleared");
    }

    /**
     * Set user logged in status
     */
    public void setUserLoggedIn(boolean loggedIn) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_USER_LOGGED_IN, loggedIn);
        if (loggedIn) {
            editor.putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis());
        }
        editor.apply();
        Log.d(TAG, "User logged in: " + loggedIn);
    }

    /**
     * Check if user is logged in
     */
    public boolean isUserLoggedIn() {
        return sharedPreferences.getBoolean(KEY_USER_LOGGED_IN, false);
    }

    /**
     * Set biometric enabled status
     */
    public void setBiometricEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_BIOMETRIC_ENABLED, enabled);
        editor.apply();
        Log.d(TAG, "Biometric enabled: " + enabled);
    }

    /**
     * Check if biometric is enabled
     */
    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    /**
     * Check if biometric hardware is available
     */
    public boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(context);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return true;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Log.e(TAG, "No biometric features available on this device");
                return false;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Log.e(TAG, "Biometric features are currently unavailable");
                return false;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Log.e(TAG, "User hasn't enrolled any biometrics");
                return false;
            default:
                return false;
        }
    }

    /**
     * Get biometric status message
     */
    public String getBiometricStatus() {
        BiometricManager biometricManager = BiometricManager.from(context);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return "Biometric authentication is available";
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "No biometric hardware available";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "Biometric hardware unavailable";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "No biometrics enrolled";
            default:
                return "Unknown biometric status";
        }
    }

    /**
     * Check if session is still valid
     */
    public boolean isSessionValid() {
        long lastLoginTime = sharedPreferences.getLong(KEY_LAST_LOGIN_TIME, 0);
        if (lastLoginTime == 0) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long hoursSinceLogin = (currentTime - lastLoginTime) / (1000 * 60 * 60);

        boolean valid = hoursSinceLogin < SESSION_TIMEOUT_HOURS;
        Log.d(TAG, "Session valid: " + valid + " (hours since login: " + hoursSinceLogin + ")");
        return valid;
    }

    /**
     * Get time since last login in hours
     */
    public long getTimeSinceLastLogin() {
        long lastLoginTime = sharedPreferences.getLong(KEY_LAST_LOGIN_TIME, 0);
        if (lastLoginTime == 0) {
            return -1;
        }

        long currentTime = System.currentTimeMillis();
        return (currentTime - lastLoginTime) / (1000 * 60 * 60);
    }

    /**
     * Determine the authentication flow based on current state
     */
    public AuthenticationFlow determineAuthenticationFlow() {
        boolean userLoggedIn = isUserLoggedIn();
        boolean biometricEnabled = isBiometricEnabled();
        boolean biometricAvailable = isBiometricAvailable();
        boolean sessionValid = isSessionValid();
        boolean hasCredentials = hasStoredCredentials();

        Log.d(TAG, "Determining auth flow - LoggedIn: " + userLoggedIn +
                ", BiometricEnabled: " + biometricEnabled +
                ", BiometricAvailable: " + biometricAvailable +
                ", SessionValid: " + sessionValid +
                ", HasCredentials: " + hasCredentials);

        // If biometric is enabled, available, and we have stored credentials
        if (biometricEnabled && biometricAvailable && hasCredentials) {
            return AuthenticationFlow.BIOMETRIC_PROMPT;
        }

        // If user is logged in with valid session
        if (userLoggedIn && sessionValid) {
            return AuthenticationFlow.AUTO_LOGIN_SESSION;
        }

        // Default to manual login
        return AuthenticationFlow.MANUAL_LOGIN;
    }

    /**
     * Trigger biometric authentication
     */
    public void authenticateWithBiometric() {
        if (biometricPrompt != null && promptInfo != null) {
            Log.d(TAG, "Showing biometric prompt");
            biometricPrompt.authenticate(promptInfo);
        } else {
            Log.e(TAG, "Biometric prompt not initialized");
            callback.onBiometricAuthenticationError("Biometric authentication not available");
        }
    }

    /**
     * Reset all biometric settings and clean up insecure credential storage
     */
    public void resetBiometricSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        
        // Also clear any data from secure storage
        try {
            SecureStorage secureStorage = new SecureStorage(context);
            secureStorage.clear();
            Log.d(TAG, "Secure storage cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing secure storage", e);
        }
        
        Log.d(TAG, "All settings reset");
    }

    /**
     * Clean up any existing insecure credential storage from previous versions
     * This method should be called once during app upgrade to remove plaintext credentials
     */
    public void cleanupInsecureCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        // Remove any plaintext credentials that may have been stored
        if (sharedPreferences.contains(KEY_STORED_EMAIL)) {
            editor.remove(KEY_STORED_EMAIL);
            Log.d(TAG, "Removed insecure email storage");
        }
        
        if (sharedPreferences.contains(KEY_STORED_PASSWORD)) {
            editor.remove(KEY_STORED_PASSWORD);
            Log.d(TAG, "Removed insecure password storage");
        }
        
        editor.apply();
    }

    /**
     * Check if secure storage is available and working
     */
    public boolean isSecureStorageAvailable() {
        try {
            SecureStorage secureStorage = new SecureStorage(context);
            // Test storage with a dummy value
            secureStorage.putString("test_key", "test_value");
            String retrieved = secureStorage.getString("test_key", "");
            secureStorage.remove("test_key");
            
            return "test_value".equals(retrieved);
        } catch (Exception e) {
            Log.e(TAG, "Secure storage not available", e);
            return false;
        }
    }

    /**
     * Get debug information
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== BiometricHelper Debug Info ===\n");
        info.append("User logged in: ").append(isUserLoggedIn()).append("\n");
        info.append("Biometric enabled: ").append(isBiometricEnabled()).append("\n");
        info.append("Session valid: ").append(isSessionValid()).append("\n");

        // Check stored credentials
        String email = getStoredEmail();
        String password = getStoredPassword();
        info.append("Has stored credentials: ").append(!email.isEmpty() && !password.isEmpty()).append("\n");
        info.append("Stored email: ").append(email.isEmpty() ? "EMPTY" : email.substring(0, Math.min(3, email.length())) + "***").append("\n");
        info.append("Stored password length: ").append(password.length()).append("\n");

        info.append("Biometric hardware available: ").append(isBiometricAvailable()).append("\n");
        info.append("Biometric status: ").append(getBiometricStatus()).append("\n");
        info.append("Secure storage available: ").append(isSecureStorageAvailable()).append("\n");
        info.append("Time since last login: ").append(getTimeSinceLastLogin()).append(" hours\n");
        info.append("================================");

        return info.toString();
    }
}
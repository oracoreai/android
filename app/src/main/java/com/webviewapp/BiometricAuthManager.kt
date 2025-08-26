package com.webviewapp

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class BiometricAuthManager(private val activity: AppCompatActivity) {
    
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    
    fun authenticateUser(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallback: () -> Unit
    ) {
        when (BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                setupBiometricPrompt(onSuccess, onError)
                showBiometricPrompt()
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                onFallback()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onFallback()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onFallback()
            }
            else -> {
                onFallback()
            }
        }
    }
    
    private fun setupBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        executor = ContextCompat.getMainExecutor(activity)
        biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        onError("Authentication cancelled")
                    }
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        onError("Too many failed attempts. Please try again later.")
                    }
                    else -> {
                        onError("Authentication error: $errString")
                    }
                }
            }
            
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Don't close app on individual failed attempts, only on error
            }
        })
        
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Secure Login")
            .setSubtitle("Use your fingerprint to access the app")
            .setDescription("Touch the fingerprint sensor to authenticate")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    }
    
    private fun showBiometricPrompt() {
        biometricPrompt.authenticate(promptInfo)
    }
    
    fun isBiometricAvailable(): Boolean {
        return BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
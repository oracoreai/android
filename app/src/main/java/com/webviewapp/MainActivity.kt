package com.webviewapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var sessionManager: SessionManager
    private lateinit var audioManager: AudioManager
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    private lateinit var loadingSubText: TextView
    private lateinit var loadingProgressBar: ProgressBar
    
    private val targetUrl = "https://net-core-web20250815190920-gccgc8d4fjh9f4g4.westus3-01.azurewebsites.net/"
    private val MICROPHONE_PERMISSION_REQUEST = 1001
    private val PREFS_NAME = "WebViewAppPrefs"
    private val KEY_FIRST_LAUNCH = "first_launch"
    private var pendingPermissionRequest: PermissionRequest? = null
    private var isInitialLoad = true // Track if this is the first page load
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        biometricManager = BiometricAuthManager(this)
        sessionManager = SessionManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Initialize loading overlay components
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingText = findViewById(R.id.loadingText)
        loadingSubText = findViewById(R.id.loadingSubText)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        
        // Request microphone permission first
        requestMicrophonePermission()
        
        // Enable WebView debugging
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        setupWebView()
        
        // Check if we're restoring from saved state
        if (savedInstanceState != null) {
            Log.d("WebViewApp", "Restoring from saved instance state")
            isInitialLoad = false // Don't show loading overlay when restoring
            hideLoadingOverlay() // Ensure overlay is hidden
            // WebView state will be restored in onRestoreInstanceState
            return
        }
        
        if (isFirstLaunch()) {
            // First launch - proceed directly to manual login
            loadWebsite()
        } else if (sessionManager.isSessionValid()) {
            // Session exists - check biometric auth
            handleBiometricAuthentication()
        } else {
            // No valid session - proceed to manual login
            loadWebsite()
        }
    }
    
    private fun handleBiometricAuthentication() {
        updateLoadingState("Authenticating...", "Please use your fingerprint")
        
        biometricManager.authenticateUser(
            onSuccess = { 
                updateLoadingState("Authentication successful!", "Loading secure session...")
                loadWebsite() 
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                finish()
            },
            onFallback = { loadWebsite() }
        )
    }
    
    private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    private fun setFirstLaunchCompleted() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), 
                MICROPHONE_PERMISSION_REQUEST)
        }
    }
    
    private fun requestMicrophonePermissionForWebView(permissionRequest: PermissionRequest) {
        pendingPermissionRequest = permissionRequest
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), 
                MICROPHONE_PERMISSION_REQUEST)
        } else {
            // Permission already granted, proceed with WebView permission
            permissionRequest.grant(permissionRequest.resources)
            pendingPermissionRequest = null
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MICROPHONE_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted - handle pending WebView request
                    pendingPermissionRequest?.let { request ->
                        Log.d("WebViewApp", "Android permission granted, now granting WebView permission")
                        
                        // Request audio focus for recording
                        requestAudioFocusForRecording()
                        
                        val resourcesToGrant = arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                        request.grant(resourcesToGrant)
                        Toast.makeText(this, "Microphone access granted", Toast.LENGTH_SHORT).show()
                        Log.d("WebViewApp", "WebView audio permission granted successfully")
                        pendingPermissionRequest = null
                    }
                } else {
                    // Permission denied - deny WebView request
                    pendingPermissionRequest?.let { request ->
                        request.deny()
                        pendingPermissionRequest = null
                    }
                    Toast.makeText(this, "Microphone permission is required for audio recording", 
                        Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }
    
    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Allow navigation within the same domain
                return false
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                // Only show loading overlay for initial load
                if (isInitialLoad) {
                    updateLoadingState("Loading website...", "Connecting to OraCore AI")
                    Log.d("WebViewApp", "Initial page loading started: $url")
                } else {
                    Log.d("WebViewApp", "Navigation started (no overlay): $url")
                }
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Inject JavaScript to help debug media access
                view?.evaluateJavascript("""
                    (function() {
                        console.log('WebView: Page loaded, checking media capabilities');
                        
                        // Check if getUserMedia is available
                        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                            console.log('WebView: getUserMedia is available');
                        } else {
                            console.log('WebView: getUserMedia is NOT available');
                        }
                        
                        // Override getUserMedia to add logging
                        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                            const originalGetUserMedia = navigator.mediaDevices.getUserMedia;
                            navigator.mediaDevices.getUserMedia = function(constraints) {
                                console.log('WebView: getUserMedia called with constraints:', JSON.stringify(constraints));
                                return originalGetUserMedia.call(this, constraints)
                                    .then(stream => {
                                        console.log('WebView: getUserMedia success, stream:', stream);
                                        return stream;
                                    })
                                    .catch(error => {
                                        console.log('WebView: getUserMedia error:', error.message);
                                        throw error;
                                    });
                            };
                        }
                    })();
                """, null)
                
                // Only hide loading overlay for initial load
                if (isInitialLoad) {
                    updateLoadingState("Loading complete!", "Welcome to OraCore AI")
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        hideLoadingOverlay()
                        isInitialLoad = false // Mark initial load as complete
                        checkAndSaveSession(url)
                    }, 1500) // Wait 1.5 seconds to show completion message
                } else {
                    // For subsequent navigation, just save session without overlay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checkAndSaveSession(url)
                    }, 2000)
                }
            }
            
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // For production, implement proper SSL certificate validation
                handler?.proceed() // Only for development - implement proper validation for production
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("WebViewApp", "WebView error: $errorCode - $description for URL: $failingUrl")
            }
            
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.e("WebViewApp", "WebView render process gone")
                // Recreate WebView if render process crashes
                try {
                    recreateWebView()
                    return true
                } catch (e: Exception) {
                    Log.e("WebViewApp", "Error recreating WebView: ${e.message}")
                    return false
                }
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                
                // Only update loading progress for initial load
                if (isInitialLoad) {
                    when {
                        newProgress < 20 -> updateLoadingState("Loading website...", "Connecting to server...")
                        newProgress < 50 -> updateLoadingState("Loading content...", "Downloading resources...")
                        newProgress < 80 -> updateLoadingState("Almost ready...", "Preparing interface...")
                        newProgress < 100 -> updateLoadingState("Finalizing...", "Setting up secure session...")
                    }
                    
                    Log.d("WebViewApp", "Initial page loading progress: $newProgress%")
                } else {
                    Log.d("WebViewApp", "Navigation progress (no overlay): $newProgress%")
                }
            }
            
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let { permissionRequest ->
                    Log.d("WebViewApp", "Permission requested from ${permissionRequest.origin}: ${permissionRequest.resources.joinToString()}")
                    runOnUiThread {
                        when {
                            permissionRequest.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) -> {
                                Log.d("WebViewApp", "Audio capture permission requested")
                                if (ContextCompat.checkSelfPermission(this@MainActivity, 
                                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    // Grant microphone permission with all requested resources
                                    Log.d("WebViewApp", "Granting audio permission for origin: ${permissionRequest.origin}")
                                    
                                    // Request audio focus for recording
                                    requestAudioFocusForRecording()
                                    
                                    // Grant specifically the audio capture resource
                                    val resourcesToGrant = arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                    permissionRequest.grant(resourcesToGrant)
                                    
                                    Toast.makeText(this@MainActivity, "Microphone access granted", Toast.LENGTH_SHORT).show()
                                    
                                    // Log success
                                    Log.d("WebViewApp", "Successfully granted audio permissions")
                                } else {
                                    // Request Android microphone permission first
                                    Log.d("WebViewApp", "Requesting Android RECORD_AUDIO permission")
                                    requestMicrophonePermissionForWebView(permissionRequest)
                                }
                            }
                            permissionRequest.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) -> {
                                Log.d("WebViewApp", "Video capture permission requested - granting")
                                val resourcesToGrant = arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                permissionRequest.grant(resourcesToGrant)
                            }
                            else -> {
                                Log.d("WebViewApp", "Other permission requested - granting all")
                                permissionRequest.grant(permissionRequest.resources)
                            }
                        }
                    }
                }
            }
        }
        
        // Configure WebView settings with security
        val webSettings: WebSettings = webView.settings
        webSettings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowContentAccess = false
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            
            // Enable media playback and recording
            mediaPlaybackRequiresUserGesture = false
            
            // Additional settings for media access
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            
            // Settings for better state preservation (app cache is deprecated)
            // Using other caching mechanisms instead
            
            // Set custom user agent
            userAgentString = "WebViewApp/1.0 (Android; Mobile)"
        }
        
        // Enable cookie manager for session persistence
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }
    
    private fun loadWebsite() {
        lifecycleScope.launch {
            val savedUrl = sessionManager.getSavedUrl()
            if (savedUrl != null && sessionManager.isSessionValid()) {
                webView.loadUrl(savedUrl)
            } else {
                webView.loadUrl(targetUrl)
            }
        }
    }
    
    private fun showBiometricSetupPrompt() {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) 
            == BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Great! Biometric authentication is now enabled for future logins", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Login successful! Session will be remembered", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkAndSaveSession(url: String?) {
        url?.let { currentUrl ->
            // Simple approach: save session after any page load that's not the login page
            if (!currentUrl.contains("Login") && !currentUrl.contains("login")) {
                sessionManager.saveSession(currentUrl)
                if (isFirstLaunch()) {
                    setFirstLaunchCompleted()
                    showBiometricSetupPrompt()
                }
            }
            
            // Also check for authentication cookies
            checkForAuthenticationCookies()
        }
    }
    
    private fun checkForAuthenticationCookies() {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(webView.url)
        cookies?.let {
            // If we have authentication cookies, consider user logged in
            if (it.contains("auth") || it.contains("session") || it.contains("token") || 
                it.contains("AspNetCore") || it.contains(".ASPXAUTH")) {
                webView.url?.let { url -> 
                    sessionManager.saveSession(url)
                    if (isFirstLaunch()) {
                        setFirstLaunchCompleted()
                        showBiometricSetupPrompt()
                    }
                }
            }
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            // Save WebView state
            webView.onPause()
            webView.pauseTimers()
            
            // Save current URL and scroll position
            saveWebViewState()
            
            Log.d("WebViewApp", "App paused, WebView state saved")
        } catch (e: Exception) {
            Log.e("WebViewApp", "Error in onPause: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            // Restore WebView state
            webView.onResume()
            webView.resumeTimers()
            
            Log.d("WebViewApp", "App resumed, WebView state restored")
        } catch (e: Exception) {
            Log.e("WebViewApp", "Error in onResume: ${e.message}")
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            // Save WebView state to bundle
            webView.saveState(outState)
            outState.putString("current_url", webView.url)
            Log.d("WebViewApp", "WebView state saved to bundle")
        } catch (e: Exception) {
            Log.e("WebViewApp", "Error saving instance state: ${e.message}")
        }
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        try {
            // Restore WebView state from bundle
            webView.restoreState(savedInstanceState)
            Log.d("WebViewApp", "WebView state restored from bundle")
        } catch (e: Exception) {
            Log.e("WebViewApp", "Error restoring instance state: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        // Release audio focus
        try {
            audioManager.abandonAudioFocus(null)
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e("WebViewApp", "Error releasing audio focus: ${e.message}")
        }
        
        webView.destroy()
        super.onDestroy()
    }
    
    private fun requestAudioFocusForRecording() {
        try {
            Log.d("WebViewApp", "Requesting audio focus for recording")
            
            // Set audio mode to communication for better microphone access
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            // Request audio focus
            val result = audioManager.requestAudioFocus(
                null, // No focus change listener needed for this use case
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            
            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.d("WebViewApp", "Audio focus granted")
                }
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    Log.w("WebViewApp", "Audio focus request failed")
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    Log.d("WebViewApp", "Audio focus request delayed")
                }
            }
            
            // Enable speaker if needed for echo cancellation
            if (!audioManager.isSpeakerphoneOn) {
                audioManager.isSpeakerphoneOn = false // Keep speaker off for recording
            }
            
        } catch (e: Exception) {
            Log.e("WebViewApp", "Error requesting audio focus: ${e.message}")
        }
    }
    
    private fun saveWebViewState() {
        try {
            webView.url?.let { currentUrl ->
                // Save current URL to session manager
                sessionManager.saveSession(currentUrl)
                Log.d("WebViewApp", "WebView state saved: $currentUrl")
            }
        } catch (e: Exception) {
            Log.e("WebViewApp", "Error saving WebView state: ${e.message}")
        }
    }
    
    private fun recreateWebView() {
        try {
            Log.d("WebViewApp", "Recreating WebView after crash")
            
            // Get current URL before destroying
            val currentUrl = webView.url
            
            // Destroy current WebView
            webView.destroy()
            
            // Create new WebView
            webView = WebView(this)
            
            // Re-setup WebView
            setupWebView()
            
            // Reload last URL or fallback to saved session
            val urlToLoad = currentUrl ?: sessionManager.getSavedUrl() ?: targetUrl
            isInitialLoad = false // Don't show loading overlay for crash recovery
            webView.loadUrl(urlToLoad)
            
            Log.d("WebViewApp", "WebView recreated successfully")
            
        } catch (e: Exception) {
            Log.e("WebViewApp", "Failed to recreate WebView: ${e.message}")
        }
    }
    
    private fun updateLoadingState(mainText: String, subText: String) {
        runOnUiThread {
            loadingText.text = mainText
            loadingSubText.text = subText
            
            // Ensure loading overlay is visible
            if (loadingOverlay.visibility != View.VISIBLE) {
                loadingOverlay.visibility = View.VISIBLE
            }
            
            Log.d("WebViewApp", "Loading state: $mainText - $subText")
        }
    }
    
    private fun hideLoadingOverlay() {
        runOnUiThread {
            loadingOverlay.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    loadingOverlay.visibility = View.GONE
                    loadingOverlay.alpha = 1f // Reset for next time
                }
            
            Log.d("WebViewApp", "Loading overlay hidden")
        }
    }
    
    private fun showLoadingOverlay() {
        runOnUiThread {
            loadingOverlay.visibility = View.VISIBLE
            loadingOverlay.alpha = 1f
            
            Log.d("WebViewApp", "Loading overlay shown")
        }
    }
}
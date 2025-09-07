package com.example.oracoreaiapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * MainActivity handles the main WebView interface and coordinates with biometric authentication
 */
public class MainActivity extends AppCompatActivity implements BiometricHelper.BiometricAuthCallback {

    private static final String WEBSITE_URL = "https://net-core-web20250815190920-gccgc8d4fjh9f4g4.westus3-01.azurewebsites.net/ModernLogin/LocalLogin";
    private static final String PREFS_NAME = "OracorePrefs";
    private static final String KEY_USER_LOGGED_IN = "user_logged_in";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private WebView webView;
    private ProgressBar progressBar;
    private BiometricHelper biometricHelper;

    // Background execution tracking
    private boolean isResumingFromBackground = false;
    private long lastPauseTime = 0;
    private boolean suppressBiometric = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Delay service start to prevent focus loss
        // startWebViewService();
        
        // Don't request battery optimization on startup - it causes focus loss
        // requestBatteryOptimizationExemption();
        
        // Initialize components
        initializeViews();
        biometricHelper = new BiometricHelper(this, this);
        
        // Clean up any insecure credentials from previous versions
        biometricHelper.cleanupInsecureCredentials();
        
        // Setup WebView and load website
        setupWebView();
        checkAuthenticationStatus();
    }

    /**
     * Start foreground service to keep app alive
     */
    private void startWebViewService() {
        try {
            Intent serviceIntent = new Intent(this, WebViewService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            android.util.Log.d("MainActivity", "WebView service started successfully");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to start WebView service", e);
            // App continues to work without service, just with reduced background capabilities
        }
    }
    
    /**
     * Request battery optimization exemption for better background performance
     */
    private void requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + packageName));
                
                try {
                    startActivity(intent);
                    android.util.Log.d("MainActivity", "Requesting battery optimization exemption");
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error requesting battery optimization exemption", e);
                }
            } else {
                android.util.Log.d("MainActivity", "Already exempt from battery optimizations");
            }
        }
    }

    // Native microphone maintenance
    private android.media.MediaRecorder nativeMicRecorder;
    private boolean isNativeMicActive = false;
    
    /**
     * Start native microphone recording to maintain permissions
     */
    private void startNativeMicrophoneMaintenance() {
        if (isNativeMicActive) return;
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.e("MainActivity", "Microphone permission not granted");
                    return;
                }
            }
            
            nativeMicRecorder = new android.media.MediaRecorder();
            nativeMicRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            nativeMicRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
            nativeMicRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
            nativeMicRecorder.setOutputFile("/dev/null"); // Discard audio data
            
            nativeMicRecorder.prepare();
            nativeMicRecorder.start();
            
            isNativeMicActive = true;
            android.util.Log.d("MainActivity", "Native microphone maintenance started");
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error starting native microphone maintenance", e);
            isNativeMicActive = false;
        }
    }
    
    /**
     * Stop native microphone maintenance
     */
    private void stopNativeMicrophoneMaintenance() {
        if (!isNativeMicActive) return;
        
        try {
            if (nativeMicRecorder != null) {
                nativeMicRecorder.stop();
                nativeMicRecorder.release();
                nativeMicRecorder = null;
            }
            isNativeMicActive = false;
            android.util.Log.d("MainActivity", "Native microphone maintenance stopped");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error stopping native microphone maintenance", e);
        }
    }

    /**
     * JavaScript interface for direct communication with WebView
     */
    public class WebAppInterface {
        @JavascriptInterface
        public void captureCredentials(String email, String password) {
            runOnUiThread(() -> {
                android.util.Log.d("MainActivity", "=== CREDENTIALS CAPTURED VIA JS INTERFACE ===");
                android.util.Log.d("MainActivity", "Email: " + (email.length() > 3 ? email.substring(0, 3) + "***" : "EMPTY"));
                android.util.Log.d("MainActivity", "Password length: " + password.length());
                android.util.Log.d("MainActivity", "Email empty: " + email.isEmpty());
                android.util.Log.d("MainActivity", "Password empty: " + password.isEmpty());

                if (!email.isEmpty() && !password.isEmpty()) {
                    android.util.Log.d("MainActivity", "Attempting to store credentials...");
                    boolean stored = biometricHelper.storeCredentials(email, password);
                    android.util.Log.d("MainActivity", "Storage result: " + stored);

                    // Immediately verify storage
                    String storedEmail = biometricHelper.getStoredEmail();
                    String storedPassword = biometricHelper.getStoredPassword();
                    android.util.Log.d("MainActivity", "Verification - Stored email: \"" + storedEmail + "\"");
                    android.util.Log.d("MainActivity", "Verification - Stored password length: " + storedPassword.length());

                    if (!storedEmail.isEmpty() && !storedPassword.isEmpty()) {
                        Toast.makeText(MainActivity.this, "✓ Login credentials saved for fingerprint login!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "⚠️ Failed to save credentials", Toast.LENGTH_LONG).show();
                    }
                } else {
                    android.util.Log.w("MainActivity", "Empty credentials received - Email: \"" + email + "\", Password: \"" + password + "\"");
                    Toast.makeText(MainActivity.this, "⚠️ Cannot save empty credentials", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void log(String message) {
            android.util.Log.d("WebView-JS", message);
        }

        @JavascriptInterface
        public String getStoredCredentials() {
            String email = biometricHelper.getStoredEmail();
            String password = biometricHelper.getStoredPassword();

            android.util.Log.d("MainActivity", "JS Interface requested credentials");
            android.util.Log.d("MainActivity", "Raw Email: '" + email + "'");
            android.util.Log.d("MainActivity", "Raw Password: '" + password + "'");
            android.util.Log.d("MainActivity", "Password length: " + password.length());

            if (!email.isEmpty() && !password.isEmpty()) {
                try {
                    // Use StringBuilder to build JSON properly
                    StringBuilder json = new StringBuilder();
                    json.append("{");
                    json.append("\"email\":\"").append(email).append("\",");
                    json.append("\"password\":\"").append(password).append("\"");
                    json.append("}");
                    
                    String jsonString = json.toString();
                    android.util.Log.d("MainActivity", "Final JSON: " + jsonString);
                    
                    // Test if it's valid JSON by parsing it
                    try {
                        org.json.JSONObject testParse = new org.json.JSONObject(jsonString);
                        android.util.Log.d("MainActivity", "JSON validation successful");
                        return jsonString;
                    } catch (org.json.JSONException e) {
                        android.util.Log.e("MainActivity", "JSON validation failed: " + e.getMessage());
                        return "";
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error creating credentials JSON", e);
                }
            }
            android.util.Log.d("MainActivity", "Returning empty - no valid credentials");
            return "";
        }

        @JavascriptInterface
        public boolean hasCredentials() {
            return biometricHelper.hasStoredCredentials();
        }

        @JavascriptInterface
        public String testInterface() {
            android.util.Log.d("MainActivity", "JavaScript interface test called successfully!");
            return "INTERFACE_WORKING";
        }

        @JavascriptInterface
        public String getCredentialsRaw() {
            String email = biometricHelper.getStoredEmail();
            String password = biometricHelper.getStoredPassword();
            return "Email=" + email + "|Password=" + password + "|Length=" + password.length();
        }

        @JavascriptInterface
        public String getEmail() {
            return biometricHelper.getStoredEmail();
        }

        @JavascriptInterface
        public String getPassword() {
            return biometricHelper.getStoredPassword();
        }

        @JavascriptInterface
        public void reportStatus(String status) {
            android.util.Log.d("MainActivity", "JS Status Report: " + status);
            runOnUiThread(() -> {
                // Use dialog for long messages, toast for short ones
                if (status.length() > 100) {
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("Debug Report")
                            .setMessage("JS: " + status)
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    Toast.makeText(MainActivity.this, "JS: " + status, Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void startRecording() {
            android.util.Log.d("MainActivity", "JavaScript requested microphone recording");
            runOnUiThread(() -> {
                if (hasMicrophonePermission()) {
                    startNativeRecording();
                } else {
                    Toast.makeText(MainActivity.this, "Microphone permission required", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void stopRecording() {
            android.util.Log.d("MainActivity", "JavaScript requested stop recording");
            runOnUiThread(() -> {
                stopNativeRecording();
            });
        }

        @JavascriptInterface
        public String getRecordingData() {
            // Return base64 encoded audio data
            return getLastRecordingAsBase64();
        }
        
        @JavascriptInterface
        public void maintainMicrophoneAccess() {
            runOnUiThread(() -> {
                android.util.Log.d("MainActivity", "JavaScript requesting microphone access maintenance");
                // Start native microphone recording to maintain permission
                startNativeMicrophoneMaintenance();
            });
        }
        
        @JavascriptInterface
        public void notifyMicrophoneActive(boolean active) {
            runOnUiThread(() -> {
                android.util.Log.d("MainActivity", "Microphone active state: " + active);
                if (active) {
                    startNativeMicrophoneMaintenance();
                }
            });
        }
    }

    /**
     * Initialize UI components
     */
    private void initializeViews() {
        android.util.Log.d("MainActivity", "Initializing views...");
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        
        if (webView == null) {
            android.util.Log.e("MainActivity", "ERROR: WebView is null!");
        } else {
            android.util.Log.d("MainActivity", "WebView found successfully");
        }

        // Add long-press gesture for debugging
        if (webView != null) {
            webView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showDebugMenu();
                    return true;
                }
            });
        }
    }

    /**
     * Show debug menu
     */
    private void showDebugMenu() {
        String storedEmail = biometricHelper.getStoredEmail();
        String storedPassword = biometricHelper.getStoredPassword();
        boolean hasCredentials = !storedEmail.isEmpty() && !storedPassword.isEmpty();

        String message = "Stored Credentials: " + (hasCredentials ? "YES" : "NO");
        if (hasCredentials) {
            message += "\nEmail: " + (storedEmail.length() > 3 ? storedEmail.substring(0, 3) + "***" : storedEmail);
            message += "\nPassword: " + storedPassword.length() + " characters";
        }
        message += "\nBiometric Enabled: " + biometricHelper.isBiometricEnabled();

        // First show the status
        new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Debug Status")
                .setMessage(message)
                .setPositiveButton("Show Menu", (dialog, which) -> showActualMenu())
                .setNegativeButton("Close", null)
                .show();
    }

    private void showActualMenu() {
        new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Debug Actions")
                .setItems(new String[]{
                        "1. Test JavaScript Interface",
                        "2. Manual Capture NOW",
                        "3. Test Stored Credentials", 
                        "4. Clear Credentials",
                        "5. Reset All Settings",
                        "6. Debug Form Fields",
                        "7. Simple Page Test",
                        "8. Test Form Detection Only",
                        "9. Show All Field Names",
                        "10. Check Field Values",
                        "11. Force Direct Capture",
                        "12. Test Auto-Fill Only",
                        "13. Debug Credential Retrieval",
                        "14. Test Raw Credentials",
                        "15. Test Direct Auto-Fill",
                        "16. Basic JS Test",
                        "17. Step by Step Fill",
                        "18. Auto-Fill via Reporting",
                        "19. Test Reporting Function",
                        "20. Simple Auto-Fill",
                        "21. Install Aggressive Capture",
                        "22. Request Battery Optimization Exemption",
                        "22. Install Blur Capture",
                        "23. Install Multi-Event Capture",
                        "24. Debug Event Detection",
                        "25. Deep Field Inspection",
                        "26. Test Value Extraction Methods",
                        "27. Test Password Field Security",
                        "28. Install Working Capture",
                        "29. Test Simple Events",
                        "30. Diagnose DOM Issues",
                        "31. Test Basic JavaScript",
                        "32. Test Field Selectors & Events",
                        "33. Debug Event Attachment",
                        "34. Install Simplified Capture",
                        "35. Test Storage Function",
                        "36. Test Blur Event Details",
                        "37. Install Automatic Capture",
                        "38. Install Enhanced Auto Capture",
                        "39. Install Simple Enter Capture",
                        "40. Test Add Visible Button",
                        "41. Add Capture Button",
                        "42. Test Microphone Access",
                        "43. Test Native Microphone",
                        "44. Install Native Mic Bridge",
                        "45. Test Simple Native Mic",
                        "46. Generate Client-Side Script"
                }, (dialog, which) -> {
                    switch(which) {
                        case 0: // Test Interface
                            testJavaScriptInterface();
                            break;
                        case 1: // Manual Capture
                            manuallyTriggerCapture();
                            break;
                        case 2: // Test Credentials
                            testStoredCredentials();
                            break;
                        case 3: // Clear Credentials
                            biometricHelper.storeCredentials("", "");
                            Toast.makeText(MainActivity.this, "Credentials cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 4: // Reset All
                            resetBiometricSettings();
                            break;
                        case 5: // Debug Form Fields
                            debugFormFields();
                            break;
                        case 6: // Simple Page Test
                            simplePageTest();
                            break;
                        case 7: // Test Form Detection Only
                            testFormDetectionOnly();
                            break;
                        case 8: // Show All Field Names
                            showAllFieldNames();
                            break;
                        case 9: // Check Field Values
                            checkFieldValues();
                            break;
                        case 10: // Force Direct Capture
                            forceDirectCapture();
                            break;
                        case 11: // Test Auto-Fill Only
                            testAutoFillOnly();
                            break;
                        case 12: // Debug Credential Retrieval
                            debugCredentialRetrieval();
                            break;
                        case 13: // Test Raw Credentials
                            testRawCredentials();
                            break;
                        case 14: // Test Direct Auto-Fill
                            testDirectAutoFill();
                            break;
                        case 15: // Basic JS Test
                            basicJSTest();
                            break;
                        case 16: // Step by Step Fill
                            stepByStepFill();
                            break;
                        case 17: // Auto-Fill via Reporting
                            autoFillViaReporting();
                            break;
                        case 18: // Test Reporting Function
                            testReporting();
                            break;
                        case 19: // Simple Auto-Fill
                            simpleAutoFill();
                            break;
                        case 20: // Install Aggressive Capture
                            installAggressiveCapture();
                            break;
                        case 21: // Install Aggressive Capture
                            installAggressiveCapture();
                            break;
                        case 22: // Request Battery Optimization Exemption (disabled)
                            Toast.makeText(MainActivity.this, "Battery optimization disabled for now", Toast.LENGTH_SHORT).show();
                            break;
                        case 23: // Install Multi-Event Capture
                            installMultiEventCapture();
                            break;
                        case 24: // Debug Event Detection
                            debugEventDetection();
                            break;
                        case 25: // Deep Field Inspection
                            debugFieldInspection();
                            break;
                        case 26: // Test Value Extraction Methods
                            testAlternativeValueExtraction();
                            break;
                        case 27: // Test Password Field Security
                            testPasswordFieldSecurity();
                            break;
                        case 28: // Install Working Capture
                            installWorkingCapture();
                            break;
                        case 29: // Test Simple Events
                            testSimpleEvents();
                            break;
                        case 30: // Diagnose DOM Issues
                            diagnoseDOMIssues();
                            break;
                        case 301: // Test Basic JavaScript
                            testBasicJavaScript();
                            break;
                        case 31: // Test Field Selectors & Events
                            testFieldSelectorsAndEvents();
                            break;
                        case 32: // Debug Event Attachment
                            debugEventAttachment();
                            break;
                        case 33: // Install Simplified Capture
                            installSimplifiedCapture();
                            break;
                        case 34: // Test Storage Function
                            testStorageFunction();
                            break;
                        case 35: // Test Blur Event Details
                            testBlurEventDetails();
                            break;
                        case 36: // Install Automatic Capture
                            installAutomaticCapture();
                            break;
                        case 37: // Install Enhanced Auto Capture
                            installEnhancedAutomaticCapture();
                            break;
                        case 38: // Install Simple Enter Capture
                            installSimpleEnterCapture();
                            break;
                        case 39: // Test Add Visible Button
                            testAddVisibleButton();
                            break;
                        case 40: // Add Capture Button
                            addCaptureButton();
                            break;
                        case 41: // Test Microphone Access
                            testMicrophoneAccess();
                            break;
                        case 42: // Test Native Microphone
                            testNativeMicrophoneAccess();
                            break;
                        case 43: // Install Native Mic Bridge
                            testNativeMicrophoneBridge();
                            break;
                        case 44: // Test Simple Native Mic
                            testSimpleNativeMicrophone();
                            break;
                        case 45: // Generate Client-Side Script
                            generateClientSideScript();
                            break;
                    }
                })
                .show();
    }

    /**
     * Manually trigger credential capture
     */
    private void manuallyTriggerCapture() {
        android.util.Log.d("MainActivity", "=== MANUAL CAPTURE TRIGGERED ===");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  console.log('Manual capture requested');" +
                    "  " +
                    "  // Try to capture using global function if available" +
                    "  if (window.captureLoginCredentials) {" +
                    "    var result = window.captureLoginCredentials();" +
                    "    //console.log('Capture result: ' + result);" +
                    "    return result;" +
                    "  }" +
                    "  " +
                    "  // Otherwise try direct capture" +
                    "  var emailField = document.getElementById('Email') || " +
                    "                  document.querySelector('input[type=\"email\"]') || " +
                    "                  document.querySelector('input[name=\"Email\"]');" +
                    "  " +
                    "  var passwordField = document.getElementById('Password') || " +
                    "                     document.querySelector('input[type=\"password\"]') || " +
                    "                     document.querySelector('input[name=\"Password\"]');" +
                    "  " +
                    "  if (!emailField || !passwordField) {" +
                    "    // Try more generic selectors" +
                    "    var inputs = document.querySelectorAll('input');" +
                    "    for (var i = 0; i < inputs.length; i++) {" +
                    "      var input = inputs[i];" +
                    "      var type = input.type.toLowerCase();" +
                    "      var name = (input.name || '').toLowerCase();" +
                    "      var id = (input.id || '').toLowerCase();" +
                    "      " +
                    "      if (!emailField && (type === 'email' || type === 'text')) {" +
                    "        if (name.includes('email') || name.includes('user') || " +
                    "            id.includes('email') || id.includes('user')) {" +
                    "          emailField = input;" +
                    "          console.log('Found email field: ' + input.id + ' / ' + input.name);" +
                    "        }" +
                    "      }" +
                    "      " +
                    "      if (!passwordField && type === 'password') {" +
                    "        passwordField = input;" +
                    "        console.log('Found password field: ' + input.id + ' / ' + input.name);" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  " +
                    "  console.log('Fields found - Email: ' + !!emailField + ', Password: ' + !!passwordField);" +
                    "  " +
                    "  if (emailField && passwordField) {" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    console.log('Values - Email: ' + email + ', Password length: ' + password.length);" +
                    "    " +
                    "    if (email && password) {" +
                    "      if (typeof Android !== 'undefined') {" +
                    "        Android.captureCredentials(email, password);" +
                    "        return 'CAPTURED: ' + email;" +
                    "      } else {" +
                    "        // Try to add interface and retry" +
                    "        console.error('Android interface not found');" +
                    "        return 'NO_INTERFACE';" +
                    "      }" +
                    "    } else {" +
                    "      return 'EMPTY_VALUES - Email: ' + (email ? 'filled' : 'empty') + ', Password: ' + (password ? 'filled' : 'empty');" +
                    "    }" +
                    "  } else {" +
                    "    return 'FIELDS_NOT_FOUND';" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                android.util.Log.d("MainActivity", "Manual capture result: " + result);
                //Toast.makeText(this, "Capture result: " + result, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Test stored credentials
     */
    private void testStoredCredentials() {
        String email = biometricHelper.getStoredEmail();
        String password = biometricHelper.getStoredPassword();

        String message = "Email: " + (email.isEmpty() ? "EMPTY" : email) + "\n";
        message += "Password: " + (password.isEmpty() ? "EMPTY" : password.length() + " characters");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Stored Credentials")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Test JavaScript interface
     */
    private void testJavaScriptInterface() {
        android.util.Log.d("MainActivity", "=== TESTING JAVASCRIPT INTERFACE ===");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  console.log('Testing JavaScript interface...');" +
                    "  if (typeof Android !== 'undefined') {" +
                    "    var result = Android.testInterface();" +
                    "    console.log('Interface test result: ' + result);" +
                    "    Android.log('Interface test successful!');" +
                    "    return result;" +
                    "  } else {" +
                    "    console.error('Android interface not found!');" +
                    "    return 'NO_INTERFACE';" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                android.util.Log.d("MainActivity", "JavaScript interface test result: " + result);
                String message = "Interface test result: " + result;
                if (result != null && result.contains("INTERFACE_WORKING")) {
                    message = "✓ JavaScript interface is working correctly!";
                } else {
                    message = "✗ JavaScript interface not working. Result: " + result;
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Simple page test to verify JavaScript execution
     */
    private void simplePageTest() {
        android.util.Log.d("MainActivity", "=== SIMPLE PAGE TEST ===");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    var info = [];" +
                    "    info.push('URL: ' + (window.location ? window.location.href : 'NO_URL'));" +
                    "    info.push('Title: ' + (document.title || 'NO_TITLE'));" +
                    "    info.push('Ready: ' + document.readyState);" +
                    "    info.push('Inputs: ' + document.querySelectorAll('input').length);" +
                    "    return info.join(' | ');" +
                    "  } catch(e) {" +
                    "    return 'ERROR: ' + e.message;" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                android.util.Log.d("MainActivity", "Simple page test result: " + result);
                String cleanResult = result != null ? result.replace("\"", "") : "null";
                
                // Show in a dialog for better visibility
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Page Analysis")
                        .setMessage(cleanResult)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Test form detection only - simple version
     */
    private void testFormDetectionOnly() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  var inputs = document.querySelectorAll('input');" +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  return 'Inputs: ' + inputs.length + ' | Email: ' + !!emailField + ' | Pass: ' + !!passwordField;" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                
                // Show in dialog instead of toast for full visibility
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Form Detection Results")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Debug what events are actually firing
     */
    private void debugEventDetection() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "console.log('Installing event debug...');" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "if (emailField && passwordField) {" +
                    "  console.log('Found fields for debugging');" +
                    "  " +
                    "  passwordField.addEventListener('blur', function() {" +
                    "    Android.reportStatus('Password blur fired! Email: ' + emailField.value + ', Pass: ' + passwordField.value.length);" +
                    "  });" +
                    "  " +
                    "  passwordField.addEventListener('keydown', function(e) {" +
                    "    Android.reportStatus('Key in password: ' + e.key + ' (code: ' + e.keyCode + ')');" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) {" +
                    "      Android.reportStatus('ENTER detected! Email: ' + emailField.value + ', Pass: ' + passwordField.value.length);" +
                    "    }" +
                    "  });" +
                    "  " +
                    "  emailField.addEventListener('blur', function() {" +
                    "    Android.reportStatus('Email blur fired! Email: ' + emailField.value + ', Pass: ' + passwordField.value.length);" +
                    "  });" +
                    "  " +
                    "  emailField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) {" +
                    "      Android.reportStatus('ENTER in email! Email: ' + emailField.value + ', Pass: ' + passwordField.value.length);" +
                    "    }" +
                    "  });" +
                    "  " +
                    "  Android.reportStatus('Event debug installed - interact with fields');" +
                    "} else {" +
                    "  Android.reportStatus('Fields not found for debugging');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Event debugging installed - watch for toast messages", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Deep field inspection during events
     */
    private void debugFieldInspection() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "function inspectField(field, name) {" +
                    "  return name + ': {' +" +
                    "    'value: \"' + (field.value || 'EMPTY') + '\", ' +" +
                    "    'type: ' + field.type + ', ' +" +
                    "    'name: ' + (field.name || 'none') + ', ' +" +
                    "    'id: ' + (field.id || 'none') + ', ' +" +
                    "    'display: ' + getComputedStyle(field).display + ', ' +" +
                    "    'visibility: ' + getComputedStyle(field).visibility + ', ' +" +
                    "    'disabled: ' + field.disabled + ', ' +" +
                    "    'readOnly: ' + field.readOnly +" +
                    "  '}';" +
                    "}" +
                    "" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "" +
                    "if (emailField && passwordField) {" +
                    "  function deepInspect(eventType) {" +
                    "    setTimeout(function() {" +
                    "      var report = 'DEEP INSPECT (' + eventType + '): ';" +
                    "      report += inspectField(emailField, 'EMAIL');" +
                    "      report += ' | ';" +
                    "      report += inspectField(passwordField, 'PASS');" +
                    "      Android.reportStatus(report);" +
                    "    }, 10);" +
                    "  }" +
                    "  " +
                    "  passwordField.addEventListener('blur', function() { deepInspect('PASS_BLUR'); });" +
                    "  emailField.addEventListener('blur', function() { deepInspect('EMAIL_BLUR'); });" +
                    "  " +
                    "  passwordField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) { deepInspect('PASS_ENTER'); }" +
                    "  });" +
                    "  " +
                    "  emailField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) { deepInspect('EMAIL_ENTER'); }" +
                    "  });" +
                    "  " +
                    "  Android.reportStatus('Deep field inspection installed');" +
                    "} else {" +
                    "  Android.reportStatus('Fields not found for deep inspection');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Deep field inspection installed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Test alternative field value extraction methods
     */
    private void testAlternativeValueExtraction() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "" +
                    "if (emailField && passwordField) {" +
                    "  function tryAllMethods(field, name) {" +
                    "    var methods = [];" +
                    "    methods.push(name + '.value: \"' + (field.value || 'EMPTY') + '\"');" +
                    "    methods.push(name + '.getAttribute(\"value\"): \"' + (field.getAttribute('value') || 'EMPTY') + '\"');" +
                    "    methods.push(name + '.defaultValue: \"' + (field.defaultValue || 'EMPTY') + '\"');" +
                    "    try { methods.push(name + '.textContent: \"' + (field.textContent || 'EMPTY') + '\"'); } catch(e) {}" +
                    "    try { methods.push(name + '.innerText: \"' + (field.innerText || 'EMPTY') + '\"'); } catch(e) {}" +
                    "    try { methods.push(name + '.innerHTML: \"' + (field.innerHTML || 'EMPTY') + '\"'); } catch(e) {}" +
                    "    return methods.join(' | ');" +
                    "  }" +
                    "  " +
                    "  function testExtraction(eventType) {" +
                    "    setTimeout(function() {" +
                    "      var report = 'VALUE METHODS (' + eventType + '): ';" +
                    "      report += tryAllMethods(emailField, 'EMAIL');" +
                    "      report += ' || ';" +
                    "      report += tryAllMethods(passwordField, 'PASS');" +
                    "      Android.reportStatus(report);" +
                    "    }, 20);" +
                    "  }" +
                    "  " +
                    "  passwordField.addEventListener('blur', function() { testExtraction('PASS_BLUR'); });" +
                    "  emailField.addEventListener('blur', function() { testExtraction('EMAIL_BLUR'); });" +
                    "  " +
                    "  Android.reportStatus('Alternative value extraction methods installed');" +
                    "} else {" +
                    "  Android.reportStatus('Fields not found for value extraction test');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Alternative value extraction methods installed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Test password field security and access patterns
     */
    private void testPasswordFieldSecurity() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "" +
                    "if (emailField && passwordField) {" +
                    "  function testPasswordSecurity() {" +
                    "    var report = 'PASSWORD SECURITY TEST: ';" +
                    "    " +
                    "    // Test basic properties" +
                    "    report += 'autocomplete=' + passwordField.autocomplete + ', ';" +
                    "    report += 'readOnly=' + passwordField.readOnly + ', ';" +
                    "    report += 'disabled=' + passwordField.disabled + ', ';" +
                    "    " +
                    "    // Test value accessibility during input event" +
                    "    report += 'hasInputEvent=' + (passwordField.oninput !== null) + ', ';" +
                    "    " +
                    "    // Test if we can access length" +
                    "    try {" +
                    "      report += 'valueLength=' + passwordField.value.length + ', ';" +
                    "    } catch(e) {" +
                    "      report += 'valueLength=ERROR(' + e.message + '), ';" +
                    "    }" +
                    "    " +
                    "    // Test if field is masked" +
                    "    report += 'type=' + passwordField.type + ', ';" +
                    "    " +
                    "    // Check for shadow DOM or special handling" +
                    "    report += 'shadowRoot=' + !!passwordField.shadowRoot + ', ';" +
                    "    " +
                    "    // Test descriptor" +
                    "    try {" +
                    "      var descriptor = Object.getOwnPropertyDescriptor(passwordField, 'value');" +
                    "      report += 'hasValueDescriptor=' + !!descriptor + ', ';" +
                    "    } catch(e) {" +
                    "      report += 'descriptorError, ';" +
                    "    }" +
                    "    " +
                    "    Android.reportStatus(report);" +
                    "  }" +
                    "  " +
                    "  // Test immediately and during events" +
                    "  testPasswordSecurity();" +
                    "  " +
                    "  passwordField.addEventListener('input', function() {" +
                    "    setTimeout(function() {" +
                    "      Android.reportStatus('DURING INPUT: value=\"' + passwordField.value + '\" length=' + passwordField.value.length);" +
                    "    }, 10);" +
                    "  });" +
                    "  " +
                    "  passwordField.addEventListener('keydown', function(e) {" +
                    "    setTimeout(function() {" +
                    "      Android.reportStatus('DURING KEYDOWN: value=\"' + passwordField.value + '\" length=' + passwordField.value.length);" +
                    "    }, 10);" +
                    "  });" +
                    "  " +
                    "} else {" +
                    "  Android.reportStatus('Fields not found for password security test');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Password security test installed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Working credential capture based on successful Deep Field Inspection timing
     */
    private void installWorkingCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "var captured = false;" +
                    "" +
                    "if (emailField && passwordField) {" +
                    "  function attemptCapture(eventType) {" +
                    "    if (captured) return;" +
                    "    " +
                    "    // Use same timing as successful Deep Field Inspection" +
                    "    setTimeout(function() {" +
                    "      var email = emailField.value;" +
                    "      var password = passwordField.value;" +
                    "      " +
                    "      Android.reportStatus(eventType + ': Email=\"' + email + '\" Pass=\"' + password + '\"');" +
                    "      " +
                    "      // Only capture if both fields have valid data" +
                    "      if (email && password && email.includes('@') && password.length > 1) {" +
                    "        try {" +
                    "          Android.captureCredentials(email, password);" +
                    "          Android.reportStatus('SUCCESS: Captured via ' + eventType + ' - ' + email);" +
                    "          captured = true;" +
                    "        } catch(e) {" +
                    "          Android.reportStatus('CAPTURE ERROR: ' + e.message);" +
                    "        }" +
                    "      } else {" +
                    "        Android.reportStatus('NOT READY: Email=' + (email || 'empty') + ' Pass=' + (password ? password.length + ' chars' : 'empty'));" +
                    "      }" +
                    "    }, 10);" + // Same 10ms delay as Deep Field Inspection
                    "  }" +
                    "  " +
                    "  // Install on both blur events" +
                    "  passwordField.addEventListener('blur', function() { attemptCapture('PASS_BLUR'); });" +
                    "  emailField.addEventListener('blur', function() { attemptCapture('EMAIL_BLUR'); });" +
                    "  " +
                    "  // Also try on Enter key" +
                    "  passwordField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) { attemptCapture('PASS_ENTER'); }" +
                    "  });" +
                    "  " +
                    "  emailField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) { attemptCapture('EMAIL_ENTER'); }" +
                    "  });" +
                    "  " +
                    "  Android.reportStatus('Working capture installed - using same timing as successful inspection');" +
                    "} else {" +
                    "  Android.reportStatus('Fields not found for working capture');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Working capture installed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Simple event test to check if events are working
     */
    private void testSimpleEvents() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "" +
                    "if (emailField && passwordField) {" +
                    "  // Remove any existing listeners by cloning the elements" +
                    "  var newEmailField = emailField.cloneNode(true);" +
                    "  var newPasswordField = passwordField.cloneNode(true);" +
                    "  emailField.parentNode.replaceChild(newEmailField, emailField);" +
                    "  passwordField.parentNode.replaceChild(newPasswordField, passwordField);" +
                    "  " +
                    "  // Get fresh references" +
                    "  emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "  passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  // Add simple event listeners" +
                    "  emailField.addEventListener('blur', function() {" +
                    "    Android.reportStatus('SIMPLE EMAIL BLUR FIRED');" +
                    "  });" +
                    "  " +
                    "  passwordField.addEventListener('blur', function() {" +
                    "    Android.reportStatus('SIMPLE PASSWORD BLUR FIRED');" +
                    "  });" +
                    "  " +
                    "  emailField.addEventListener('focus', function() {" +
                    "    Android.reportStatus('EMAIL FOCUS');" +
                    "  });" +
                    "  " +
                    "  passwordField.addEventListener('focus', function() {" +
                    "    Android.reportStatus('PASSWORD FOCUS');" +
                    "  });" +
                    "  " +
                    "  Android.reportStatus('Simple event test installed - clean slate');" +
                    "} else {" +
                    "  Android.reportStatus('Fields not found for simple event test');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Simple event test installed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Diagnose DOM and field detection issues
     */
    private void diagnoseDOMIssues() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    var report = 'DOM DIAGNOSIS: ';" +
                    "    " +
                    "    // Check if document is ready" +
                    "    report += 'readyState=' + document.readyState + ', ';" +
                    "    " +
                    "    // Count all inputs" +
                    "    var allInputs = document.querySelectorAll('input');" +
                    "    report += 'totalInputs=' + allInputs.length + ', ';" +
                    "    " +
                    "    // Check specific selectors" +
                    "    var emailByType = document.querySelector('input[type=\"email\"]');" +
                    "    var emailByName = document.querySelector('input[name*=\"mail\" i]');" +
                    "    var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "    " +
                    "    report += 'emailByType=' + !!emailByType + ', ';" +
                    "    report += 'emailByName=' + !!emailByName + ', ';" +
                    "    report += 'passwordField=' + !!passwordField + ', ';" +
                    "    " +
                    "    // If we found fields, test their properties" +
                    "    if (emailByType || emailByName) {" +
                    "      var emailField = emailByType || emailByName;" +
                    "      report += 'emailId=' + (emailField.id || 'none') + ', ';" +
                    "      report += 'emailName=' + (emailField.name || 'none') + ', ';" +
                    "      report += 'emailType=' + emailField.type;" +
                    "    }" +
                    "    " +
                    "    return report;" +
                    "    " +
                    "  } catch(e) {" +
                    "    return 'DOM DIAGNOSIS ERROR: ' + e.message;" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result returned";
                
                // Show in dialog
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("DOM Diagnosis")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Test basic JavaScript execution
     */
    private void testBasicJavaScript() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Test 1: Simple return
            webView.evaluateJavascript("javascript:'TEST1_SUCCESS'", result -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Test 1 - Simple Return")
                        .setMessage("Result: " + result)
                        .setPositiveButton("OK", null)
                        .show();
            });
            
            // Test 2: Document check
            webView.evaluateJavascript("javascript:document ? 'DOCUMENT_EXISTS' : 'NO_DOCUMENT'", result -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Test 2 - Document Check")
                        .setMessage("Result: " + result)
                        .setPositiveButton("OK", null)
                        .show();
            });
            
            // Test 3: Input count
            webView.evaluateJavascript("javascript:document.querySelectorAll('input').length", result -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Test 3 - Input Count")
                        .setMessage("Result: " + result)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Test specific field selectors and event attachment
     */
    private void testFieldSelectorsAndEvents() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Test field selection
            webView.evaluateJavascript("javascript:(function() {" +
                    "var email1 = document.querySelector('input[type=\"email\"]');" +
                    "var email2 = document.querySelector('input[name*=\"mail\" i]');" +
                    "var password = document.querySelector('input[type=\"password\"]');" +
                    "return 'email1:' + !!email1 + ',email2:' + !!email2 + ',pass:' + !!password;" +
                    "})()", result -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Field Selection Test")
                        .setMessage("Result: " + result)
                        .setPositiveButton("Next", (dialog, which) -> {
                            // Test event attachment
                            testEventAttachment();
                        })
                        .show();
            });
        }
    }
    
    /**
     * Test if we can attach events to the found fields
     */
    private void testEventAttachment() {
        webView.evaluateJavascript("javascript:(function() {" +
                "try {" +
                "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                "  " +
                "  if (!emailField || !passwordField) return 'FIELDS_NOT_FOUND';" +
                "  " +
                "  var testResult = 'EVENTS_TEST:';" +
                "  " +
                "  // Try to add a simple click event" +
                "  emailField.addEventListener('click', function() {" +
                "    window.testEmailClicked = true;" +
                "  });" +
                "  testResult += 'emailClick=OK,';" +
                "  " +
                "  passwordField.addEventListener('click', function() {" +
                "    window.testPasswordClicked = true;" +
                "  });" +
                "  testResult += 'passwordClick=OK,';" +
                "  " +
                "  // Try to add blur events" +
                "  emailField.addEventListener('blur', function() {" +
                "    window.testEmailBlur = true;" +
                "  });" +
                "  testResult += 'emailBlur=OK,';" +
                "  " +
                "  passwordField.addEventListener('blur', function() {" +
                "    window.testPasswordBlur = true;" +
                "  });" +
                "  testResult += 'passwordBlur=OK';" +
                "  " +
                "  return testResult;" +
                "} catch(e) {" +
                "  return 'ERROR:' + e.message;" +
                "}" +
                "})()", result -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Event Attachment Test")
                    .setMessage("Result: " + result)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    /**
     * Debug event attachment step by step
     */
    private void debugEventAttachment() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Test 1: Can we get the fields?
            webView.evaluateJavascript("javascript:document.querySelector('input[type=\"email\"]') ? 'EMAIL_FOUND' : 'EMAIL_NOT_FOUND'", result -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Step 1 - Find Email Field")
                        .setMessage("Result: " + result)
                        .setPositiveButton("Next", (dialog, which) -> {
                            // Test 2: Can we get addEventListener function?
                            testAddEventListenerFunction();
                        })
                        .show();
            });
        }
    }
    
    private void testAddEventListenerFunction() {
        webView.evaluateJavascript("javascript:(function() {" +
                "var field = document.querySelector('input[type=\"email\"]');" +
                "return field && typeof field.addEventListener === 'function' ? 'ADDEVENT_EXISTS' : 'ADDEVENT_MISSING';" +
                "})()", result -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Step 2 - Check addEventListener")
                    .setMessage("Result: " + result)
                    .setPositiveButton("Next", (dialog, which) -> {
                        // Test 3: Try simple event attachment
                        testSimpleEventAttachment();
                    })
                    .show();
        });
    }
    
    private void testSimpleEventAttachment() {
        webView.evaluateJavascript("javascript:(function() {" +
                "try {" +
                "  var field = document.querySelector('input[type=\"email\"]');" +
                "  if (!field) return 'NO_FIELD';" +
                "  " +
                "  var simpleFunc = function() { return true; };" +
                "  field.addEventListener('click', simpleFunc);" +
                "  " +
                "  return 'SUCCESS';" +
                "} catch(e) {" +
                "  return 'ERROR_' + e.name + '_' + e.message;" +
                "}" +
                "})()", result -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Step 3 - Simple Event Attachment")
                    .setMessage("Result: " + result)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    /**
     * Install simplified working capture based on successful tests
     */
    private void installSimplifiedCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Step 1: Install email field blur listener
            webView.evaluateJavascript("javascript:(function() {" +
                    "var emailField = document.querySelector('input[type=\"email\"]');" +
                    "if (emailField) {" +
                    "  emailField.addEventListener('blur', function() {" +
                    "    setTimeout(function() {" +
                    "      var email = emailField.value;" +
                    "      var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "      var password = passwordField ? passwordField.value : '';" +
                    "      if (email && password && email.includes('@')) {" +
                    "        Android.captureCredentials(email, password);" +
                    "        Android.reportStatus('CAPTURED: ' + email);" +
                    "      } else {" +
                    "        Android.reportStatus('NOT READY - Email: ' + email + ', Pass: ' + (password ? 'present' : 'empty'));" +
                    "      }" +
                    "    }, 10);" +
                    "  });" +
                    "  return 'EMAIL_BLUR_INSTALLED';" +
                    "} else {" +
                    "  return 'EMAIL_FIELD_NOT_FOUND';" +
                    "}" +
                    "})()", result -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Email Blur Listener")
                        .setMessage("Result: " + result)
                        .setPositiveButton("Next", (dialog, which) -> {
                            // Step 2: Install password field blur listener
                            installPasswordBlurListener();
                        })
                        .show();
            });
        }
    }
    
    private void installPasswordBlurListener() {
        webView.evaluateJavascript("javascript:(function() {" +
                "var passwordField = document.querySelector('input[type=\"password\"]');" +
                "if (passwordField) {" +
                "  passwordField.addEventListener('blur', function() {" +
                "    setTimeout(function() {" +
                "      var password = passwordField.value;" +
                "      var emailField = document.querySelector('input[type=\"email\"]');" +
                "      var email = emailField ? emailField.value : '';" +
                "      if (email && password && email.includes('@')) {" +
                "        Android.captureCredentials(email, password);" +
                "        Android.reportStatus('CAPTURED: ' + email);" +
                "      } else {" +
                "        Android.reportStatus('NOT READY - Email: ' + email + ', Pass: ' + (password ? 'present' : 'empty'));" +
                "      }" +
                "    }, 10);" +
                "  });" +
                "  return 'PASSWORD_BLUR_INSTALLED';" +
                "} else {" +
                "  return 'PASSWORD_FIELD_NOT_FOUND';" +
                "}" +
                "})()", result -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Password Blur Listener")
                    .setMessage("Result: " + result + " - Now test by filling form and clicking away!")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    /**
     * Test storage functionality and permissions
     */
    private void testStorageFunction() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Test direct storage with known values
            String testEmail = "test@example.com";
            String testPassword = "testpass123";
            
            android.util.Log.d("MainActivity", "=== STORAGE TEST STARTING ===");
            android.util.Log.d("MainActivity", "Attempting to store: " + testEmail + " / " + testPassword);
            
            // Try to store test credentials
            boolean stored = biometricHelper.storeCredentials(testEmail, testPassword);
            android.util.Log.d("MainActivity", "Storage result: " + stored);
            
            // Immediately try to retrieve them
            String retrievedEmail = biometricHelper.getStoredEmail();
            String retrievedPassword = biometricHelper.getStoredPassword();
            
            android.util.Log.d("MainActivity", "Retrieved email: " + retrievedEmail);
            android.util.Log.d("MainActivity", "Retrieved password: " + retrievedPassword);
            
            // Show results
            String report = "STORAGE TEST RESULTS:\n" +
                    "Store operation: " + (stored ? "SUCCESS" : "FAILED") + "\n" +
                    "Retrieved email: " + (retrievedEmail.isEmpty() ? "EMPTY" : retrievedEmail) + "\n" +
                    "Retrieved password: " + (retrievedPassword.isEmpty() ? "EMPTY" : "length=" + retrievedPassword.length()) + "\n" +
                    "Has credentials: " + biometricHelper.hasStoredCredentials();
            
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Storage Test")
                    .setMessage(report)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    /**
     * Test what happens during blur events with detailed reporting
     */
    private void testBlurEventDetails() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript("javascript:(function() {" +
                    "var emailField = document.querySelector('input[type=\"email\"]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "if (emailField && passwordField) {" +
                    "  passwordField.addEventListener('blur', function() {" +
                    "    setTimeout(function() {" +
                    "      var email = emailField.value;" +
                    "      var password = passwordField.value;" +
                    "      " +
                    "      Android.reportStatus('BLUR EVENT: Email=\"' + email + '\" Pass=\"' + password + '\" Valid=' + (email && password && email.includes('@')));" +
                    "      " +
                    "      if (email && password && email.includes('@')) {" +
                    "        Android.reportStatus('ABOUT TO CALL captureCredentials...');" +
                    "        try {" +
                    "          Android.captureCredentials(email, password);" +
                    "          Android.reportStatus('captureCredentials CALLED SUCCESSFULLY');" +
                    "        } catch(e) {" +
                    "          Android.reportStatus('captureCredentials ERROR: ' + e.message);" +
                    "        }" +
                    "      } else {" +
                    "        Android.reportStatus('CONDITIONS NOT MET for capture');" +
                    "      }" +
                    "    }, 10);" +
                    "  });" +
                    "  return 'DETAILED_BLUR_TEST_INSTALLED';" +
                    "} else {" +
                    "  return 'FIELDS_NOT_FOUND';" +
                    "}" +
                    "})()", result -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Detailed Blur Test")
                        .setMessage("Result: " + result + " - Now fill form and blur password field")
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Install automatic credential capture based on working Force Direct Capture
     */
    private void installAutomaticCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "console.log('Installing automatic capture based on Force Direct Capture...');" +
                    "var captureAttempted = false;" +
                    "" +
                    "function attemptDirectCapture() {" +
                    "  if (captureAttempted) return;" +
                    "  " +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  if (emailField && passwordField) {" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    " +
                    "    if (email && password && email.includes('@') && password.length > 3) {" +
                    "      captureAttempted = true;" +
                    "      try {" +
                    "        Android.captureCredentials(email, password);" +
                    "        Android.reportStatus('AUTO-CAPTURED: ' + email);" +
                    "      } catch(e) {" +
                    "        Android.reportStatus('AUTO-CAPTURE ERROR: ' + e.message);" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "}" +
                    "" +
                    "// Try capture on form submission (most reliable for first-time login)" +
                    "var forms = document.querySelectorAll('form');" +
                    "for (var i = 0; i < forms.length; i++) {" +
                    "  forms[i].addEventListener('submit', function(e) {" +
                    "    attemptDirectCapture();" +
                    "  });" +
                    "}" +
                    "" +
                    "// Try capture when login button is clicked" +
                    "var buttons = document.querySelectorAll('button, input[type=\"submit\"], input[type=\"button\"]');" +
                    "for (var i = 0; i < buttons.length; i++) {" +
                    "  if (buttons[i].textContent.toLowerCase().includes('login') || buttons[i].value.toLowerCase().includes('login')) {" +
                    "    buttons[i].addEventListener('click', function(e) {" +
                    "      setTimeout(attemptDirectCapture, 100);" +
                    "    });" +
                    "  }" +
                    "}" +
                    "" +
                    "// Backup: Monitor for field changes and try after a delay" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "if (emailField && passwordField) {" +
                    "  passwordField.addEventListener('input', function() {" +
                    "    setTimeout(attemptDirectCapture, 2000);" +
                    "  });" +
                    "}" +
                    "" +
                    "Android.reportStatus('Automatic capture installed - monitors form submit, login clicks, and field changes');";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Automatic capture installed based on working Force Direct Capture", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Install enhanced automatic capture with Enter key detection
     */
    private void installEnhancedAutomaticCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "console.log('Installing enhanced automatic capture with Enter key...');" +
                    "var captureAttempted = false;" +
                    "" +
                    "function attemptDirectCapture() {" +
                    "  if (captureAttempted) return;" +
                    "  " +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  if (emailField && passwordField) {" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    " +
                    "    Android.reportStatus('CHECKING: Email=\"' + email + '\" Pass=' + (password ? password.length + ' chars' : 'empty'));" +
                    "    " +
                    "    if (email && password && email.includes('@') && password.length > 1) {" +
                    "      captureAttempted = true;" +
                    "      try {" +
                    "        Android.captureCredentials(email, password);" +
                    "        Android.reportStatus('AUTO-CAPTURED: ' + email);" +
                    "      } catch(e) {" +
                    "        Android.reportStatus('AUTO-CAPTURE ERROR: ' + e.message);" +
                    "      }" +
                    "    } else {" +
                    "      Android.reportStatus('CONDITIONS NOT MET: email=' + !!email + ', password=' + !!password + ', valid=' + (email && email.includes('@')));" +
                    "    }" +
                    "  } else {" +
                    "    Android.reportStatus('FIELDS NOT FOUND');" +
                    "  }" +
                    "}" +
                    "" +
                    "// ENTER key detection on both fields" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "" +
                    "if (emailField) {" +
                    "  emailField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) {" +
                    "      Android.reportStatus('ENTER in email field detected');" +
                    "      setTimeout(attemptDirectCapture, 100);" +
                    "    }" +
                    "  });" +
                    "}" +
                    "" +
                    "if (passwordField) {" +
                    "  passwordField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) {" +
                    "      Android.reportStatus('ENTER in password field detected');" +
                    "      setTimeout(attemptDirectCapture, 100);" +
                    "    }" +
                    "  });" +
                    "}" +
                    "" +
                    "// Form submission detection" +
                    "var forms = document.querySelectorAll('form');" +
                    "for (var i = 0; i < forms.length; i++) {" +
                    "  forms[i].addEventListener('submit', function(e) {" +
                    "    Android.reportStatus('FORM SUBMIT detected');" +
                    "    attemptDirectCapture();" +
                    "  });" +
                    "}" +
                    "" +
                    "Android.reportStatus('Enhanced automatic capture installed - Enter key detection added');";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Enhanced automatic capture with Enter key detection installed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Install simple Enter key capture using exact Force Direct Capture code
     */
    private void installSimpleEnterCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "" +
                    "if (emailField && passwordField) {" +
                    "  passwordField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) {" +
                    "      // Use exact same code as Force Direct Capture" +
                    "      var email = emailField.value;" +
                    "      var password = passwordField.value;" +
                    "      if (email && password && typeof Android !== 'undefined') {" +
                    "        Android.captureCredentials(email, password);" +
                    "        Android.reportStatus('ENTER CAPTURE SUCCESS: ' + email);" +
                    "      } else {" +
                    "        Android.reportStatus('ENTER CAPTURE FAILED: email=' + !!email + ', pass=' + !!password);" +
                    "      }" +
                    "    }" +
                    "  });" +
                    "  Android.reportStatus('Simple Enter capture installed');" +
                    "} else {" +
                    "  Android.reportStatus('Fields not found for Enter capture');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Simple Enter key capture installed", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Test if we can add a visible button to the page
     */
    private void testAddVisibleButton() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "var btn = document.createElement('button');" +
                    "btn.innerHTML = 'TEST BUTTON - CLICK ME';" +
                    "btn.style.cssText = 'position: fixed; top: 50px; right: 50px; z-index: 9999; padding: 20px; background: red; color: white; border: none; font-size: 16px; cursor: pointer;';" +
                    "btn.onclick = function() { alert('Button works!'); };" +
                    "document.body.appendChild(btn);" +
                    "console.log('Test button added');" +
                    "'Button added to page';";

            webView.evaluateJavascript(script, result -> {
                Toast.makeText(this, "Script result: " + result, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Add capture credentials button to current page
     */
    private void addCaptureButton() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "if (document.getElementById('claude-capture-btn')) {" +
                    "  document.getElementById('claude-capture-btn').remove();" +
                    "}" +
                    "" +
                    "var btn = document.createElement('button');" +
                    "btn.id = 'claude-capture-btn';" +
                    "btn.innerHTML = '🔐 Save Credentials for Fingerprint Login';" +
                    "btn.type = 'button';" +
                    "btn.style.cssText = 'position: fixed; top: 10px; right: 10px; z-index: 9999; padding: 12px; background: #007bff; color: white; border: none; border-radius: 8px; font-size: 14px; cursor: pointer; box-shadow: 0 4px 8px rgba(0,0,0,0.3); font-family: Arial, sans-serif;';" +
                    "" +
                    "btn.onclick = function() {" +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  if (emailField && passwordField) {" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    " +
                    "    if (email && password && email.includes('@')) {" +
                    "      try {" +
                    "        Android.captureCredentials(email, password);" +
                    "        btn.innerHTML = '✅ Credentials Saved!';" +
                    "        btn.style.background = '#28a745';" +
                    "        setTimeout(function() { btn.style.display = 'none'; }, 3000);" +
                    "      } catch(e) {" +
                    "        btn.innerHTML = '❌ Error: ' + e.message;" +
                    "        btn.style.background = '#dc3545';" +
                    "      }" +
                    "    } else {" +
                    "      btn.innerHTML = '❌ Please fill email and password first';" +
                    "      btn.style.background = '#dc3545';" +
                    "      setTimeout(function() {" +
                    "        btn.innerHTML = '🔐 Save Credentials for Fingerprint Login';" +
                    "        btn.style.background = '#007bff';" +
                    "      }, 3000);" +
                    "    }" +
                    "  } else {" +
                    "    btn.innerHTML = '❌ Login form not found';" +
                    "    btn.style.background = '#dc3545';" +
                    "  }" +
                    "};" +
                    "" +
                    "document.body.appendChild(btn);" +
                    "'Capture button added';";

            webView.evaluateJavascript(script, result -> {
                Toast.makeText(this, "Capture button added to page", Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * Test microphone permissions and access
     */
    private void testMicrophoneAccess() {
        StringBuilder report = new StringBuilder();
        report.append("MICROPHONE ACCESS TEST:\n\n");
        
        // Check Android permission
        boolean hasPermission = hasMicrophonePermission();
        report.append("Android Permission: ").append(hasPermission ? "✅ GRANTED" : "❌ DENIED").append("\n");
        
        if (!hasPermission) {
            report.append("\nRequesting permission...\n");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        }
        
        // Check WebView settings
        WebSettings settings = webView.getSettings();
        report.append("JavaScript Enabled: ").append(settings.getJavaScriptEnabled() ? "✅ YES" : "❌ NO").append("\n");
        report.append("Media Playback: ").append(settings.getMediaPlaybackRequiresUserGesture() ? "❌ Requires Gesture" : "✅ Automatic").append("\n");
        
        // Test basic WebView microphone access
        report.append("\nTesting WebView microphone access...\n");
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Microphone Access Test")
                .setMessage(report.toString())
                .setPositiveButton("Test Web Access", (dialog, which) -> {
                    testWebMicrophoneAccess();
                })
                .setNegativeButton("OK", null)
                .show();
    }
    
    /**
     * Test microphone access from web page
     */
    private void testWebMicrophoneAccess() {
        String script = "javascript:" +
                "console.log('Testing microphone access...');" +
                "Android.reportStatus('Testing microphone access...');" +
                "" +
                "// Check if getUserMedia is available" +
                "if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {" +
                "  Android.reportStatus('❌ getUserMedia not supported');" +
                "} else {" +
                "  Android.reportStatus('✅ getUserMedia available, requesting access...');" +
                "  " +
                "  navigator.mediaDevices.getUserMedia({ audio: true })" +
                "  .then(function(stream) {" +
                "    Android.reportStatus('✅ Microphone access SUCCESS! Stream active: ' + stream.active);" +
                "    var tracks = stream.getAudioTracks();" +
                "    Android.reportStatus('Audio tracks: ' + tracks.length);" +
                "    tracks.forEach(track => {" +
                "      Android.reportStatus('Track: ' + track.label + ', enabled: ' + track.enabled);" +
                "      track.stop();" +
                "    });" +
                "  })" +
                "  .catch(function(err) {" +
                "    Android.reportStatus('❌ Microphone FAILED: ' + err.name + ' - ' + err.message);" +
                "    console.error('Microphone error:', err);" +
                "  });" +
                "}";
        
        webView.evaluateJavascript(script, null);
    }

    /**
     * Alternative microphone access test with native Android recording
     */
    private void testNativeMicrophoneAccess() {
        try {
            // Test if we can create a MediaRecorder (native Android way)
            android.media.MediaRecorder recorder = new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
            
            // Create a temporary file
            java.io.File tempFile = new java.io.File(getCacheDir(), "mic_test.3gp");
            recorder.setOutputFile(tempFile.getAbsolutePath());
            
            recorder.prepare();
            recorder.start();
            
            // Record for 1 second then stop
            new android.os.Handler().postDelayed(() -> {
                try {
                    recorder.stop();
                    recorder.release();
                    tempFile.delete();
                    
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Native Microphone Test")
                            .setMessage("✅ Native Android microphone access WORKS!\n\nThe issue is WebView-specific. This is a known limitation where WebView cannot access hardware microphone on some Android devices/versions.")
                            .setPositiveButton("OK", null)
                            .show();
                } catch (Exception e) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Native Microphone Test")
                            .setMessage("❌ Native microphone also failed: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                }
            }, 1000);
            
        } catch (Exception e) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Native Microphone Test")
                    .setMessage("❌ Cannot access microphone at system level: " + e.getMessage() + 
                               "\n\nTry:\n1. Close other apps using microphone\n2. Restart the app\n3. Check Android Settings > Apps > [App Name] > Permissions")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    // Native recording variables
    private android.media.MediaRecorder mediaRecorder;
    private String currentRecordingFile;
    private boolean isRecording = false;

    /**
     * Start native Android recording (bypasses WebView limitations)
     */
    private void startNativeRecording() {
        try {
            if (isRecording) {
                Toast.makeText(this, "Already recording", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create recording file
            currentRecordingFile = getCacheDir().getAbsolutePath() + "/webview_recording_" + System.currentTimeMillis() + ".m4a";
            
            mediaRecorder = new android.media.MediaRecorder();
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentRecordingFile);
            
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            
            Toast.makeText(this, "🎤 Recording started (native)", Toast.LENGTH_SHORT).show();
            
            // Notify JavaScript that recording started
            webView.evaluateJavascript("javascript:if(window.onNativeRecordingStart) window.onNativeRecordingStart();", null);
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to start native recording", e);
            Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Stop native recording
     */
    private void stopNativeRecording() {
        try {
            if (!isRecording || mediaRecorder == null) {
                Toast.makeText(this, "Not recording", Toast.LENGTH_SHORT).show();
                return;
            }

            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            
            Toast.makeText(this, "🛑 Recording stopped", Toast.LENGTH_SHORT).show();
            
            // Notify JavaScript that recording stopped
            webView.evaluateJavascript("javascript:if(window.onNativeRecordingStopped) window.onNativeRecordingStopped();", null);
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to stop native recording", e);
            Toast.makeText(this, "Stop recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Get last recording as base64 for JavaScript
     */
    private String getLastRecordingAsBase64() {
        try {
            if (currentRecordingFile == null) return "";
            
            java.io.File file = new java.io.File(currentRecordingFile);
            if (!file.exists()) return "";
            
            byte[] audioData = java.nio.file.Files.readAllBytes(file.toPath());
            return android.util.Base64.encodeToString(audioData, android.util.Base64.DEFAULT);
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to read recording file", e);
            return "";
        }
    }

    /**
     * Test the native microphone bridge for websites
     */
    private void testNativeMicrophoneBridge() {
        String script = "javascript:" +
                "// Add native microphone interface to website" +
                "window.nativeMicrophone = {" +
                "  start: function() {" +
                "    console.log('Starting native recording...');" +
                "    Android.startRecording();" +
                "  }," +
                "  stop: function() {" +
                "    console.log('Stopping native recording...');" +
                "    Android.stopRecording();" +
                "  }," +
                "  getData: function() {" +
                "    return Android.getRecordingData();" +
                "  }" +
                "};" +
                "" +
                "// Add callback handlers" +
                "window.onNativeRecordingStart = function() {" +
                "  console.log('Native recording started');" +
                "  Android.reportStatus('🎤 Native recording active');" +
                "};" +
                "" +
                "window.onNativeRecordingStopped = function() {" +
                "  console.log('Native recording stopped');" +
                "  Android.reportStatus('🛑 Recording stopped - data available');" +
                "};" +
                "" +
                "// Test the interface" +
                "Android.reportStatus('Native microphone bridge installed. Test: window.nativeMicrophone.start()');" +
                "" +
                "// Auto-create test buttons" +
                "var testDiv = document.createElement('div');" +
                "testDiv.style.cssText = 'position: fixed; top: 100px; right: 10px; z-index: 9999; background: white; padding: 10px; border: 2px solid blue; border-radius: 5px;';" +
                "testDiv.innerHTML = '<h4>Native Mic Test</h4><button onclick=\"window.nativeMicrophone.start()\" style=\"padding: 5px; margin: 2px; background: green; color: white; border: none;\">🎤 Start</button><br><button onclick=\"window.nativeMicrophone.stop()\" style=\"padding: 5px; margin: 2px; background: red; color: white; border: none;\">🛑 Stop</button>';" +
                "document.body.appendChild(testDiv);";

        webView.evaluateJavascript(script, result -> {
            Toast.makeText(this, "Native microphone bridge installed with test buttons", Toast.LENGTH_LONG).show();
        });
    }

    /**
     * Simple test of native microphone bridge
     */
    private void testSimpleNativeMicrophone() {
        String script = "javascript:" +
                "// Test if we can call the native microphone directly" +
                "try {" +
                "  Android.reportStatus('Testing direct native microphone call...');" +
                "  Android.startRecording();" +
                "  " +
                "  setTimeout(function() {" +
                "    Android.stopRecording();" +
                "    Android.reportStatus('Native microphone test completed');" +
                "  }, 2000);" +
                "  " +
                "} catch(e) {" +
                "  Android.reportStatus('Native microphone test failed: ' + e.message);" +
                "}";

        webView.evaluateJavascript(script, result -> {
            Toast.makeText(this, "Testing native microphone for 2 seconds...", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Generate client-side JavaScript code for automatic credential capture
     */
    private void generateClientSideScript() {
        String clientScript = 
                "// OraCoreAI App - Automatic Credential Capture Script\n" +
                "// Add this script to your website's login page\n" +
                "\n" +
                "(function() {\n" +
                "    // Check if running in OraCoreAI app\n" +
                "    if (typeof Android === 'undefined' || typeof Android.captureCredentials !== 'function') {\n" +
                "        return; // Not in OraCoreAI app\n" +
                "    }\n" +
                "    \n" +
                "    let captured = false;\n" +
                "    \n" +
                "    function captureCredentials() {\n" +
                "        if (captured) return;\n" +
                "        \n" +
                "        const emailField = document.querySelector('input[type=\"email\"]') || \n" +
                "                          document.querySelector('input[name*=\"mail\" i]') || \n" +
                "                          document.querySelector('input[name*=\"user\" i]');\n" +
                "        const passwordField = document.querySelector('input[type=\"password\"]');\n" +
                "        \n" +
                "        if (emailField && passwordField) {\n" +
                "            const email = emailField.value;\n" +
                "            const password = passwordField.value;\n" +
                "            \n" +
                "            if (email && password && email.includes('@') && password.length > 1) {\n" +
                "                try {\n" +
                "                    Android.captureCredentials(email, password);\n" +
                "                    captured = true;\n" +
                "                    console.log('Credentials auto-captured for OraCoreAI');\n" +
                "                } catch (e) {\n" +
                "                    console.error('Failed to capture credentials:', e);\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "    \n" +
                "    // Method 1: Form submission\n" +
                "    document.addEventListener('submit', function(e) {\n" +
                "        if (e.target.tagName === 'FORM') {\n" +
                "            captureCredentials();\n" +
                "        }\n" +
                "    }, true);\n" +
                "    \n" +
                "    // Method 2: Login button clicks\n" +
                "    function attachButtonListeners() {\n" +
                "        const buttons = document.querySelectorAll('button, input[type=\"submit\"]');\n" +
                "        buttons.forEach(button => {\n" +
                "            const text = (button.textContent || button.value || '').toLowerCase();\n" +
                "            if (text.includes('login') || text.includes('sign in') || text.includes('submit')) {\n" +
                "                button.addEventListener('click', function() {\n" +
                "                    setTimeout(captureCredentials, 100);\n" +
                "                }, true);\n" +
                "            }\n" +
                "        });\n" +
                "    }\n" +
                "    \n" +
                "    // Method 3: Enter key in password field\n" +
                "    document.addEventListener('keydown', function(e) {\n" +
                "        if ((e.key === 'Enter' || e.keyCode === 13) && e.target.type === 'password') {\n" +
                "            setTimeout(captureCredentials, 100);\n" +
                "        }\n" +
                "    }, true);\n" +
                "    \n" +
                "    // Initialize\n" +
                "    attachButtonListeners();\n" +
                "    console.log('OraCoreAI auto-capture initialized');\n" +
                "})();";

        // Show the script in a dialog for copying
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Client-Side Auto-Capture Script")
                .setMessage("This JavaScript will auto-capture credentials when users login. Add it to your website's login page.\n\nClick 'Copy' to copy the script to clipboard.")
                .setPositiveButton("Copy Script", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("OraCoreAI Auto-Capture Script", clientScript);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "✅ Script copied to clipboard!", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("View in Logs", (dialog, which) -> {
                    android.util.Log.d("ClientScript", "=== ORACOREAI AUTO-CAPTURE SCRIPT ===");
                    android.util.Log.d("ClientScript", clientScript);
                    android.util.Log.d("ClientScript", "=== END SCRIPT ===");
                    Toast.makeText(this, "Script printed to Android logs", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /**
     * Setup keep-alive behavior to maintain WebView when app is minimized
     */
    private void setupKeepAliveBehavior() {
        android.util.Log.d("MainActivity", "Setting up keep-alive behavior");
        
        // Method 1: Acquire wake lock to prevent CPU sleep
        android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            android.os.PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK, 
                    "OraCoreAI::KeepAliveWakeLock"
            );
            // Keep wake lock for long duration (will be released when app is destroyed)
            wakeLock.acquire(24 * 60 * 60 * 1000L); // 24 hours max
            android.util.Log.d("MainActivity", "Wake lock acquired");
        }
        
        // Method 2: Prevent activity from being destroyed
        try {
            // Keep activity in memory
            android.content.ComponentCallbacks2 memoryCallback = new android.content.ComponentCallbacks2() {
                @Override
                public void onConfigurationChanged(android.content.res.Configuration newConfig) {}
                
                @Override
                public void onLowMemory() {
                    android.util.Log.d("MainActivity", "Low memory warning - trying to preserve WebView");
                }
                
                @Override
                public void onTrimMemory(int level) {
                    android.util.Log.d("MainActivity", "Trim memory level: " + level);
                    // Resist memory trimming for important levels
                    if (level >= TRIM_MEMORY_UI_HIDDEN) {
                        android.util.Log.d("MainActivity", "App went to background - maintaining WebView");
                    }
                }
            };
            registerComponentCallbacks(memoryCallback);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to setup memory callbacks", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumingFromBackground = true;
        lastPauseTime = System.currentTimeMillis();
        
        if (webView != null) {
            // Keep JavaScript and audio running
            webView.setKeepScreenOn(true);
            injectAggressiveKeepAliveScript();
            startNativeMicrophoneMaintenance();
            preserveSessionData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Start service on first resume
        if (lastPauseTime == 0) {
            startWebViewService();
        }
        
        if (webView != null) {
            stopNativeMicrophoneMaintenance();
            
            // Handle session restoration for recent resumes
            long timeSincePause = System.currentTimeMillis() - lastPauseTime;
            boolean isRecentResume = timeSincePause < 1800000; // 30 minutes
            boolean userLoggedIn = biometricHelper.isUserLoggedIn();
            boolean sessionValid = biometricHelper.isSessionValid();
            
            if (isRecentResume && userLoggedIn && sessionValid) {
                suppressBiometric = true;
                webView.postDelayed(() -> {
                    suppressBiometric = false;
                    isResumingFromBackground = false;
                }, 10000);
                
                // Restore session if needed
                webView.postDelayed(() -> {
                    String url = webView.getUrl();
                    if (url != null && url.toLowerCase().contains("login")) {
                        restoreSessionCookies();
                        String lastUrl = getSharedPreferences("WebViewState", MODE_PRIVATE)
                                .getString("lastUrl", null);
                        if (lastUrl != null && !lastUrl.toLowerCase().contains("login")) {
                            webView.loadUrl(lastUrl);
                        }
                    }
                }, 500);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        if (webView != null) {
            webView.setKeepScreenOn(true);
            injectAggressiveKeepAliveScript();
        }
    }

    /**
     * Inject aggressive keep-alive JavaScript with audio context
     */
    private void injectAggressiveKeepAliveScript() {
        String script = "javascript:(function() {" +
                "  console.log('=== INJECTING AGGRESSIVE KEEP-ALIVE SCRIPT ===');" +
                
                // Keep audio context and microphone access alive
                "  if (!window.keepAliveAudioContext) {" +
                "    try {" +
                "      window.keepAliveAudioContext = new (window.AudioContext || window.webkitAudioContext)();" +
                "      const oscillator = window.keepAliveAudioContext.createOscillator();" +
                "      const gainNode = window.keepAliveAudioContext.createGain();" +
                "      gainNode.gain.value = 0;" +  // Silent
                "      oscillator.connect(gainNode);" +
                "      gainNode.connect(window.keepAliveAudioContext.destination);" +
                "      oscillator.start();" +
                "      console.log('Audio context keep-alive started');" +
                "    } catch(e) { console.log('Audio context error:', e); }" +
                "  }" +
                
                // Maintain microphone access
                "  if (!window.keepAliveMicStream && navigator.mediaDevices) {" +
                "    navigator.mediaDevices.getUserMedia({audio: true}).then(stream => {" +
                "      window.keepAliveMicStream = stream;" +
                "      console.log('Microphone keep-alive stream established');" +
                "      // Keep the stream active but muted" +
                "      stream.getAudioTracks().forEach(track => {" +
                "        track.enabled = true;" +
                "      });" +
                "    }).catch(e => console.log('Microphone access error:', e));" +
                "  }" +
                
                // High-frequency timers
                "  if (!window.keepAliveInterval) {" +
                "    window.keepAliveInterval = setInterval(function() {" +
                "      console.log('Keep-alive tick:', new Date().toISOString());" +
                "      // Force CPU activity" +
                "      const start = performance.now();" +
                "      while (performance.now() - start < 1) {}" +
                "    }, 1000);" +  // Every second
                "  }" +
                
                // Prevent page visibility API from pausing microphone
                "  Object.defineProperty(document, 'hidden', { value: false, writable: false });" +
                "  Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: false });" +
                "  Object.defineProperty(document, 'hasFocus', { value: () => true, writable: false });" +
                
                // Override ALL focus and visibility events that could pause microphone
                "  const events = ['visibilitychange', 'blur', 'focus', 'focusin', 'focusout', 'beforeunload', 'unload', 'pagehide', 'pageshow'];" +
                "  events.forEach(event => {" +
                "    document.addEventListener(event, function(e) {" +
                "      console.log('Blocked event:', event);" +
                "      e.stopImmediatePropagation();" +
                "      e.preventDefault();" +
                "    }, true);" +
                "    window.addEventListener(event, function(e) {" +
                "      console.log('Blocked window event:', event);" +
                "      e.stopImmediatePropagation();" +
                "      e.preventDefault();" +
                "    }, true);" +
                "  });" +
                
                "  console.log('Aggressive keep-alive script installed');" +
                "})();";

        if (webView != null) {
            webView.evaluateJavascript(script, result -> 
                android.util.Log.d("MainActivity", "Aggressive keep-alive script result: " + result)
            );
        }
    }
    
    /**
     * Inject JavaScript to keep the page alive in background
     */
    private void injectKeepAliveScript() {
        if (webView != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String keepAliveScript = "javascript:" +
                    "console.log('OraCoreAI: App paused - starting keep-alive');" +
                    "" +
                    "// Method 1: Prevent page from being suspended" +
                    "if (!window.oraCoreKeepAlive) {" +
                    "  window.oraCoreKeepAlive = setInterval(function() {" +
                    "    // Tiny background activity to prevent suspension" +
                    "    var dummy = Date.now();" +
                    "  }, 30000); // Every 30 seconds" +
                    "  " +
                    "  console.log('Keep-alive timer started');" +
                    "}" +
                    "" +
                    "// Method 2: Use Page Visibility API to detect background" +
                    "document.addEventListener('visibilitychange', function() {" +
                    "  if (document.hidden) {" +
                    "    console.log('Page hidden - maintaining activity');" +
                    "  } else {" +
                    "    console.log('Page visible again');" +
                    "  }" +
                    "});" +
                    "" +
                    "// Method 3: Audio context keep-alive (silent)" +
                    "if (typeof AudioContext !== 'undefined' || typeof webkitAudioContext !== 'undefined') {" +
                    "  if (!window.oraCoreAudioContext) {" +
                    "    try {" +
                    "      window.oraCoreAudioContext = new (AudioContext || webkitAudioContext)();" +
                    "      var oscillator = window.oraCoreAudioContext.createOscillator();" +
                    "      var gainNode = window.oraCoreAudioContext.createGain();" +
                    "      gainNode.gain.value = 0; // Silent" +
                    "      oscillator.connect(gainNode);" +
                    "      gainNode.connect(window.oraCoreAudioContext.destination);" +
                    "      oscillator.start();" +
                    "      console.log('Audio context keep-alive started');" +
                    "    } catch(e) {" +
                    "      console.log('Audio keep-alive failed:', e);" +
                    "    }" +
                    "  }" +
                    "}";

            webView.evaluateJavascript(keepAliveScript, null);
        }
    }

    /**
     * Inject script for background maintenance
     */
    private void injectBackgroundMaintenanceScript() {
        if (webView != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String backgroundScript = "javascript:" +
                    "console.log('OraCoreAI: Background maintenance active');" +
                    "" +
                    "// Keep session active" +
                    "if (!window.oraCoreBgMaintenance) {" +
                    "  window.oraCoreBgMaintenance = setInterval(function() {" +
                    "    // Ping to keep session alive" +
                    "    var xhr = new XMLHttpRequest();" +
                    "    xhr.open('GET', window.location.href, true);" +
                    "    xhr.timeout = 5000;" +
                    "    xhr.onload = function() {" +
                    "      console.log('Background session ping successful');" +
                    "    };" +
                    "    xhr.onerror = function() {" +
                    "      console.log('Background session ping failed');" +
                    "    };" +
                    "    xhr.send();" +
                    "  }, 120000); // Every 2 minutes" +
                    "}";

            webView.evaluateJavascript(backgroundScript, null);
        }
    }

    /**
     * Install multi-event capture (blur, enter, click, etc.)
     */
    private void installMultiEventCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "console.log('Installing comprehensive capture...');" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "var captured = false;" +
                    "if (emailField && passwordField) {" +
                    "  console.log('Found fields, installing multiple event listeners');" +
                    "  " +
                    "  function tryCapture(eventType) {" +
                    "    if (captured) return;" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    console.log(eventType + ' - Email: ' + (email || 'empty') + ', Password: ' + (password ? password.length + ' chars' : 'empty'));" +
                    "    if (email && password && email.includes('@') && password.length > 1) {" +
                    "      console.log('Capturing via ' + eventType);" +
                    "      try {" +
                    "        Android.captureCredentials(email, password);" +
                    "        Android.reportStatus(eventType + ' captured: ' + email + ' (pass: ' + password.length + ' chars)');" +
                    "        captured = true;" +
                    "      } catch(e) {" +
                    "        console.log('Capture error: ' + e.message);" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  " +
                    "  // Blur events" +
                    "  passwordField.addEventListener('blur', function() { tryCapture('Password Blur'); });" +
                    "  emailField.addEventListener('blur', function() { tryCapture('Email Blur'); });" +
                    "  " +
                    "  // Enter key events" +
                    "  passwordField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) {" +
                    "      console.log('Enter key in password field');" +
                    "      setTimeout(function() { tryCapture('Enter Key'); }, 100);" +
                    "    }" +
                    "  });" +
                    "  emailField.addEventListener('keydown', function(e) {" +
                    "    if (e.key === 'Enter' || e.keyCode === 13) {" +
                    "      console.log('Enter key in email field');" +
                    "      setTimeout(function() { tryCapture('Email Enter'); }, 100);" +
                    "    }" +
                    "  });" +
                    "  " +
                    "  // Click anywhere on page" +
                    "  document.addEventListener('click', function(e) {" +
                    "    if (e.target !== emailField && e.target !== passwordField) {" +
                    "      setTimeout(function() { tryCapture('Page Click'); }, 100);" +
                    "    }" +
                    "  });" +
                    "  " +
                    "  console.log('All event listeners installed');" +
                    "} else {" +
                    "  console.log('Fields not found');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Multi-event capture installed - try blur, enter, or clicking", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Install blur-based credential capture (much cleaner approach)
     */
    private void installBlurCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "console.log('Installing blur capture...');" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "if (emailField && passwordField) {" +
                    "  console.log('Found email and password fields');" +
                    "  " +
                    "  passwordField.addEventListener('blur', function() {" +
                    "    console.log('Password field lost focus - capturing now');" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    if (email && password) {" +
                    "      console.log('Both fields have values: email=' + email + ', password length=' + password.length);" +
                    "      try {" +
                    "        Android.captureCredentials(email, password);" +
                    "        Android.reportStatus('Blur captured: ' + email + ' (pass: ' + password.length + ' chars)');" +
                    "      } catch(e) {" +
                    "        console.log('Capture error: ' + e.message);" +
                    "      }" +
                    "    } else {" +
                    "      console.log('Fields not ready: email=' + (email || 'empty') + ', password=' + (password || 'empty'));" +
                    "    }" +
                    "  });" +
                    "  " +
                    "  emailField.addEventListener('blur', function() {" +
                    "    console.log('Email field lost focus');" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    if (email && password) {" +
                    "      console.log('Both fields have values after email blur');" +
                    "      try {" +
                    "        Android.captureCredentials(email, password);" +
                    "        Android.reportStatus('Email blur captured: ' + email);" +
                    "      } catch(e) {" +
                    "        console.log('Capture error: ' + e.message);" +
                    "      }" +
                    "    }" +
                    "  });" +
                    "  console.log('Blur listeners installed');" +
                    "} else {" +
                    "  console.log('Could not find email or password fields');" +
                    "}";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Blur capture installed - fill form and click elsewhere", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Install very aggressive credential capture that monitors continuously
     */
    private void installAggressiveCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "window.lastEmailValue = '';" +
                    "window.lastPasswordValue = '';" +
                    "window.captureDelay = null;" +
                    "window.aggressiveCapture = setInterval(function() {" +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  if (emailField && passwordField && emailField.value && passwordField.value) {" +
                    "    var currentEmail = emailField.value;" +
                    "    var currentPassword = passwordField.value;" +
                    "    " +
                    "    // Check if values have changed (user still typing)" +
                    "    if (currentEmail !== window.lastEmailValue || currentPassword !== window.lastPasswordValue) {" +
                    "      window.lastEmailValue = currentEmail;" +
                    "      window.lastPasswordValue = currentPassword;" +
                    "      console.log('Values changed - Email: ' + currentEmail + ', Password length: ' + currentPassword.length);" +
                    "      " +
                    "      // Clear previous delay and set new one" +
                    "      clearTimeout(window.captureDelay);" +
                    "      window.captureDelay = setTimeout(function() {" +
                    "        console.log('Capturing after user stopped typing...');" +
                    "        try {" +
                    "          Android.captureCredentials(currentEmail, currentPassword);" +
                    "          Android.reportStatus('Captured: ' + currentEmail + ' (pass: ' + currentPassword.length + ' chars)');" +
                    "          clearInterval(window.aggressiveCapture);" +
                    "        } catch(e) {" +
                    "          console.log('Capture error: ' + e.message);" +
                    "        }" +
                    "      }, 1500);" +
                    "    }" +
                    "  }" +
                    "}, 300);";

            webView.evaluateJavascript(script, null);
            Toast.makeText(this, "Smart capture installed - fill form completely then wait", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Simple auto-fill that just does the filling
     */
    private void simpleAutoFill() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "if (emailField && passwordField) {" +
                    "  emailField.value = Android.getEmail();" +
                    "  passwordField.value = Android.getPassword();" +
                    "  emailField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "  passwordField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "}";

            webView.evaluateJavascript(script, null);
            
            // Show confirmation
            Toast.makeText(this, "Auto-fill attempted", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Test if the reporting function works
     */
    private void testReporting() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    Android.reportStatus('Test message 1');" +
                    "    setTimeout(function() { Android.reportStatus('Test message 2'); }, 500);" +
                    "  } catch(e) {" +
                    "    console.log('Error: ' + e.message);" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                // Should see toast messages if reporting works
            });
        }
    }

    /**
     * Auto-fill using reporting instead of return values
     */
    private void autoFillViaReporting() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    Android.reportStatus('Starting auto-fill');" +
                    "    " +
                    "    // Get credentials" +
                    "    var email = Android.getEmail();" +
                    "    var password = Android.getPassword();" +
                    "    Android.reportStatus('Got email: ' + (email || 'NONE'));" +
                    "    " +
                    "    if (!email || !password) {" +
                    "      Android.reportStatus('ERROR: Missing credentials');" +
                    "      return;" +
                    "    }" +
                    "    " +
                    "    // Find fields" +
                    "    var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "    var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "    " +
                    "    if (!emailField || !passwordField) {" +
                    "      Android.reportStatus('ERROR: Fields not found');" +
                    "      return;" +
                    "    }" +
                    "    " +
                    "    // Fill fields" +
                    "    emailField.value = email;" +
                    "    passwordField.value = password;" +
                    "    " +
                    "    // Trigger events" +
                    "    emailField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "    passwordField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "    " +
                    "    Android.reportStatus('SUCCESS: Fields filled!');" +
                    "  } catch(e) {" +
                    "    Android.reportStatus('ERROR: ' + e.message);" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                // Result will probably be null, but that's ok - we're using reportStatus instead
            });
        }
    }

    /**
     * Step by step auto-fill debug
     */
    private void stepByStepFill() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  var results = [];" +
                    "  " +
                    "  // Step 1: Check Android interface" +
                    "  results.push('Android: ' + (typeof Android !== 'undefined'));" +
                    "  " +
                    "  // Step 2: Get credentials" +
                    "  try {" +
                    "    var email = Android.getEmail();" +
                    "    var password = Android.getPassword();" +
                    "    results.push('Email: ' + (email || 'EMPTY'));" +
                    "    results.push('Password: ' + (password || 'EMPTY'));" +
                    "  } catch(e) {" +
                    "    results.push('Creds Error: ' + e.message);" +
                    "  }" +
                    "  " +
                    "  // Step 3: Find fields" +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  results.push('Email Field: ' + !!emailField);" +
                    "  results.push('Password Field: ' + !!passwordField);" +
                    "  " +
                    "  return results.join(' | ');" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Step by Step Debug")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Basic JavaScript test - simplest possible
     */
    private void basicJSTest() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Try different approaches
            String script1 = "javascript:'test123'";
            String script2 = "javascript:(function(){ return 'test456'; })()";
            String script3 = "javascript:document.title || 'no-title'";

            webView.evaluateJavascript(script1, result1 -> {
                webView.evaluateJavascript(script2, result2 -> {
                    webView.evaluateJavascript(script3, result3 -> {
                        String message = "JS1: " + result1 + " | JS2: " + result2 + " | JS3: " + result3;
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("JavaScript Test Results")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                });
            });
        }
    }

    /**
     * Test direct auto-fill without JSON
     */
    private void testDirectAutoFill() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    if (typeof Android !== 'undefined') {" +
                    "      var email = Android.getEmail();" +
                    "      var password = Android.getPassword();" +
                    "      " +
                    "      if (!email || !password) return 'ERROR: No credentials';" +
                    "      " +
                    "      // Find fields using our working selectors" +
                    "      var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "      var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "      " +
                    "      if (!emailField || !passwordField) return 'ERROR: Fields not found';" +
                    "      " +
                    "      // Fill the fields" +
                    "      emailField.value = email;" +
                    "      passwordField.value = password;" +
                    "      " +
                    "      // Trigger events" +
                    "      emailField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "      emailField.dispatchEvent(new Event('change', {bubbles: true}));" +
                    "      passwordField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "      passwordField.dispatchEvent(new Event('change', {bubbles: true}));" +
                    "      " +
                    "      return 'SUCCESS: Filled ' + email;" +
                    "    } else {" +
                    "      return 'ERROR: Android interface not available';" +
                    "    }" +
                    "  } catch(e) {" +
                    "    return 'ERROR: ' + e.message;" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Test raw credential retrieval
     */
    private void testRawCredentials() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    if (typeof Android !== 'undefined') {" +
                    "      var rawCreds = Android.getCredentialsRaw();" +
                    "      return rawCreds;" +
                    "    } else {" +
                    "      return 'ERROR: Android interface not available';" +
                    "    }" +
                    "  } catch(e) {" +
                    "    return 'ERROR: ' + e.message;" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Raw Credentials Test")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Debug credential retrieval from JavaScript
     */
    private void debugCredentialRetrieval() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    if (typeof Android !== 'undefined') {" +
                    "      var credsJson = Android.getStoredCredentials();" +
                    "      return 'Credentials JSON: \"' + credsJson + '\" | Length: ' + credsJson.length + ' | Type: ' + typeof credsJson;" +
                    "    } else {" +
                    "      return 'ERROR: Android interface not available';" +
                    "    }" +
                    "  } catch(e) {" +
                    "    return 'ERROR: ' + e.message;" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Credential Retrieval Debug")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Test auto-fill functionality using stored credentials
     */
    private void testAutoFillOnly() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  try {" +
                    "    // Get credentials from Android" +
                    "    var credsJson = Android.getStoredCredentials();" +
                    "    if (!credsJson || credsJson.length === 0) return 'ERROR: No stored credentials';" +
                    "    " +
                    "    // Parse JSON - handle potential escaping issues" +
                    "    var creds;" +
                    "    try {" +
                    "      creds = JSON.parse(credsJson);" +
                    "    } catch(parseError) {" +
                    "      return 'ERROR: JSON parse failed - ' + parseError.message;" +
                    "    }" +
                    "    " +
                    "    if (!creds.email || !creds.password) {" +
                    "      return 'ERROR: Invalid credential format';" +
                    "    }" +
                    "    " +
                    "    // Use the working selectors from our successful test" +
                    "    var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "    var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "    " +
                    "    if (!emailField || !passwordField) {" +
                    "      return 'ERROR: Fields not found';" +
                    "    }" +
                    "    " +
                    "    // Fill the fields" +
                    "    emailField.value = creds.email;" +
                    "    passwordField.value = creds.password;" +
                    "    " +
                    "    // Trigger events to notify the page" +
                    "    emailField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "    emailField.dispatchEvent(new Event('change', {bubbles: true}));" +
                    "    passwordField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "    passwordField.dispatchEvent(new Event('change', {bubbles: true}));" +
                    "    " +
                    "    return 'SUCCESS: Auto-filled ' + creds.email;" +
                    "  } catch(error) {" +
                    "    return 'ERROR: ' + error.message;" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Force direct capture using the working selectors
     */
    private void forceDirectCapture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  if (emailField && passwordField) {" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    " +
                    "    if (email && password) {" +
                    "      if (typeof Android !== 'undefined') {" +
                    "        Android.captureCredentials(email, password);" +
                    "        return 'SUCCESS: Captured ' + email;" +
                    "      } else {" +
                    "        return 'ERROR: No Android interface';" +
                    "      }" +
                    "    } else {" +
                    "      return 'ERROR: Empty values - Email: ' + (email || 'EMPTY') + ', Password: ' + (password ? 'FILLED' : 'EMPTY');" +
                    "    }" +
                    "  } else {" +
                    "    return 'ERROR: Fields not found';" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Check if fields have values when we try to capture
     */
    private void checkFieldValues() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  var emailValue = emailField ? emailField.value : 'NO_FIELD';" +
                    "  var passwordValue = passwordField ? passwordField.value : 'NO_FIELD';" +
                    "  " +
                    "  return 'Email: \"' + emailValue + '\" | Password: \"' + passwordValue + '\"';" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Current Field Values")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Show all field names to identify correct selectors
     */
    private void showAllFieldNames() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  var inputs = document.querySelectorAll('input');" +
                    "  var fields = [];" +
                    "  for (var i = 0; i < inputs.length; i++) {" +
                    "    var input = inputs[i];" +
                    "    fields.push(input.type + ' (id:' + input.id + ', name:' + input.name + ')');" +
                    "  }" +
                    "  return fields.join(' | ');" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("All Input Fields")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
    }

    /**
     * Debug form fields to see what's actually on the page
     */
    private void debugFormFields() {
        android.util.Log.d("MainActivity", "=== DEBUGGING FORM FIELDS ===");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  var result = {};" +
                    "  result.url = window.location.href;" +
                    "  " +
                    "  // Count all inputs" +
                    "  var allInputs = document.querySelectorAll('input');" +
                    "  result.totalInputs = allInputs.length;" +
                    "  " +
                    "  // Find input details" +
                    "  result.inputs = [];" +
                    "  for (var i = 0; i < allInputs.length; i++) {" +
                    "    var input = allInputs[i];" +
                    "    result.inputs.push({" +
                    "      type: input.type," +
                    "      id: input.id," +
                    "      name: input.name," +
                    "      placeholder: input.placeholder," +
                    "      value: input.value" +
                    "    });" +
                    "  }" +
                    "  " +
                    "  // Find forms" +
                    "  var forms = document.querySelectorAll('form');" +
                    "  result.formCount = forms.length;" +
                    "  " +
                    "  // Test our selectors" +
                    "  result.emailFound = !!(document.getElementById('Email') || " +
                    "                        document.querySelector('input[type=\"email\"]') || " +
                    "                        document.querySelector('input[name=\"Email\"]') || " +
                    "                        document.querySelector('input[name=\"email\"]') || " +
                    "                        document.querySelector('input[name=\"username\"]'));" +
                    "  " +
                    "  result.passwordFound = !!document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  Android.log('Form debug: ' + JSON.stringify(result, null, 2));" +
                    "  return JSON.stringify(result);" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                android.util.Log.d("MainActivity", "Form debug result: " + result);
                
                try {
                    if (result != null && !result.equals("null")) {
                        // Remove quotes from result
                        String cleanResult = result.replace("\\\"", "\"");
                        if (cleanResult.startsWith("\"")) cleanResult = cleanResult.substring(1);
                        if (cleanResult.endsWith("\"")) cleanResult = cleanResult.substring(0, cleanResult.length() - 1);
                        
                        org.json.JSONObject json = new org.json.JSONObject(cleanResult);
                        
                        String message = "Page Analysis:\n";
                        message += "URL: " + json.optString("url") + "\n";
                        message += "Total Inputs: " + json.optInt("totalInputs") + "\n";
                        message += "Forms: " + json.optInt("formCount") + "\n";
                        message += "Email Field Found: " + json.optBoolean("emailFound") + "\n";
                        message += "Password Field Found: " + json.optBoolean("passwordFound");
                        
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Form Debug Results")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Debug result: " + result, Toast.LENGTH_LONG).show();
                }
            });
        }
    }


    /**
     * Request necessary runtime permissions
     */
    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        java.util.List<String> permissionsToRequest = new java.util.ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.RECORD_AUDIO)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Microphone permission denied - voice features won't work",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    /**
     * Check if microphone permission is granted
     */
    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Configure WebView settings for optimal performance and security
     */
    private void setupWebView() {
        // Enable WebView debugging for Chrome DevTools
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings webSettings = webView.getSettings();

        // Enable JavaScript
        webSettings.setJavaScriptEnabled(true);

        // CRITICAL: Add JavaScript interface BEFORE loading any pages
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        android.util.Log.d("MainActivity", "JavaScript interface 'Android' added to WebView");

        // Enable DOM storage
        webSettings.setDomStorageEnabled(true);

        // Enable database storage
        webSettings.setDatabaseEnabled(true);

        // Enable cookies with persistent storage
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        
        // Enhanced session persistence settings
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT); // Use cache when available

        // Enable zoom controls
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // Set mixed content mode for HTTPS
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Enable media playback and microphone access
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Additional settings for microphone/media access
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // Enable all media features
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Additional WebView settings for audio support
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        
        // Hardware acceleration settings that may help with microphone
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        
        // Enable geolocation
        webSettings.setGeolocationEnabled(true);

        // Enable file access
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        // Enable hardware acceleration for better scrolling
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Enable smooth scrolling
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        // Set cache mode for better performance
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Set user agent to desktop version to avoid mobile restrictions
        String userAgent = webSettings.getUserAgentString();
        webSettings.setUserAgentString(userAgent.replace("Mobile", "eliboM").replace("Android", "diordnA"));

        // Setup WebViewClient
        setupNormalWebViewClient();

        // Set WebChromeClient to handle progress updates and permissions
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    String[] requestedResources = request.getResources();

                    android.util.Log.d("MainActivity", "WebView requesting permissions: " + java.util.Arrays.toString(requestedResources));

                    java.util.List<String> permissionsToGrant = new java.util.ArrayList<>();
                    for (String resource : requestedResources) {
                        switch (resource) {
                            case android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE:
                                android.util.Log.d("MainActivity", "Website requesting microphone access");
                                if (hasMicrophonePermission()) {
                                    permissionsToGrant.add(resource);
                                    //Toast.makeText(MainActivity.this, "✅ Microphone access granted to website", Toast.LENGTH_SHORT).show();
                                    android.util.Log.d("MainActivity", "Microphone access granted");
                                } else {
                                    android.util.Log.e("MainActivity", "Microphone permission not granted to app");
                                    Toast.makeText(MainActivity.this, "❌ Microphone permission denied - check app settings", Toast.LENGTH_LONG).show();
                                }
                                break;
                            case android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE:
                                android.util.Log.d("MainActivity", "Website requesting camera access");
                                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                                        == PackageManager.PERMISSION_GRANTED) {
                                    permissionsToGrant.add(resource);
                                    Toast.makeText(MainActivity.this, "✅ Camera access granted to website", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "❌ Camera permission denied", Toast.LENGTH_SHORT).show();
                                }
                                break;
                            default:
                                android.util.Log.d("MainActivity", "Website requesting permission: " + resource);
                                permissionsToGrant.add(resource);
                                break;
                        }
                    }

                    if (!permissionsToGrant.isEmpty()) {
                        request.grant(permissionsToGrant.toArray(new String[0]));
                    } else {
                        request.deny();
                    }
                } else {
                    super.onPermissionRequest(request);
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
    }

    /**
     * Setup normal WebViewClient behavior
     */
    private void setupNormalWebViewClient() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);

                // Always inject capture script on login pages
                if (url.contains("/Login") || url.contains("/login")) {
                    android.util.Log.d("MainActivity", "Login page detected, injecting capture script");
                    injectCaptureScript();
                }

                detectLogout(url);
                checkLoginStatus(url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Toast.makeText(MainActivity.this, "Error loading page: " + description, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Inject comprehensive credential capture script
     */
    private void injectCaptureScript() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // First, test if interface is available
            String testScript = "javascript:(function() {" +
                    "  var result = (typeof Android !== 'undefined');" +
                    "  console.log('Android interface available: ' + result);" +
                    "  return result;" +
                    "})();";

            webView.evaluateJavascript(testScript, testResult -> {
                android.util.Log.d("MainActivity", "Android interface test: " + testResult);

                if (!"true".equals(testResult)) {
                    android.util.Log.e("MainActivity", "WARNING: Android interface not available! Re-adding...");
                    webView.addJavascriptInterface(new WebAppInterface(), "Android");
                }
            });

            // Now inject the capture script
            String script = "javascript:(function() {" +
                    "  console.log('=== INJECTING CAPTURE SCRIPT ===');" +
                    "  " +
                    "  // Make capture function globally available using working selectors" +
                    "  window.captureLoginCredentials = function() {" +
                    "    console.log('Manual capture triggered');" +
                    "    var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "    var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "    " +
                    "    console.log('Email field found: ' + !!emailField + (emailField ? ' (id: ' + emailField.id + ', name: ' + emailField.name + ')' : ''));" +
                    "    console.log('Password field found: ' + !!passwordField + (passwordField ? ' (id: ' + passwordField.id + ', name: ' + passwordField.name + ')' : ''));" +
                    "    " +
                    "    if (emailField && passwordField) {" +
                    "      var email = emailField.value;" +
                    "      var password = passwordField.value;" +
                    "      console.log('Email value: \"' + email + '\"');" +
                    "      console.log('Password length: ' + password.length);" +
                    "      " +
                    "      if (email && password) {" +
                    "        if (typeof Android !== 'undefined') {" +
                    "          console.log('Sending to Android interface...');" +
                    "          Android.captureCredentials(email, password);" +
                    "          return 'CAPTURED';" +
                    "        } else {" +
                    "          console.error('Android interface not available!');" +
                    "          return 'NO_INTERFACE';" +
                    "        }" +
                    "      } else {" +
                    "        return 'EMPTY_VALUES';" +
                    "      }" +
                    "    } else {" +
                    "      return 'FIELDS_NOT_FOUND';" +
                    "    }" +
                    "  };" +
                    "  " +
                    "  // Auto-capture on various events" +
                    "  function setupAutoCapture() {" +
                    "    console.log('Setting up auto-capture...');" +
                    "    " +
                    "    var form = document.querySelector('form');" +
                    "    var submitButtons = document.querySelectorAll('button[type=\"submit\"], input[type=\"submit\"], button[id*=\"login\" i], button[id*=\"submit\" i]');" +
                    "    " +
                    "    // Intercept form submission with immediate capture" +
                    "    if (form) {" +
                    "      form.addEventListener('submit', function(e) {" +
                    "        console.log('Form submit intercepted - capturing NOW!');" +
                    "        // Capture immediately before form submits" +
                    "        window.captureLoginCredentials();" +
                    "      }, true);" +
                    "      console.log('Form listener attached');" +
                    "    }" +
                    "    " +
                    "    // Intercept all submit buttons with immediate capture" +
                    "    submitButtons.forEach(function(btn) {" +
                    "      btn.addEventListener('click', function(e) {" +
                    "        console.log('Submit button clicked - capturing NOW!');" +
                    "        // Capture immediately when button is clicked" +
                    "        window.captureLoginCredentials();" +
                    "        // Also capture after a tiny delay in case form changes" +
                    "        setTimeout(window.captureLoginCredentials, 50);" +
                    "      }, true);" +
                    "    });" +
                    "    console.log('Button listeners attached: ' + submitButtons.length);" +
                    "    " +
                    "    // Also capture when password field loses focus" +
                    "    var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "    if (passwordField) {" +
                    "      passwordField.addEventListener('blur', function() {" +
                    "        console.log('Password field blur');" +
                    "        setTimeout(window.captureLoginCredentials, 100);" +
                    "      });" +
                    "    }" +
                    "    " +
                    "    // Aggressive capture: Monitor password field changes" +
                    "    document.addEventListener('input', function(e) {" +
                    "      if (e.target.type === 'password') {" +
                    "        console.log('Password input detected - will capture when ready');" +
                    "        // Capture when user stops typing in password field" +
                    "        clearTimeout(window.captureTimeout);" +
                    "        window.captureTimeout = setTimeout(function() {" +
                    "          var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "          if (emailField && emailField.value && e.target.value) {" +
                    "            console.log('Both fields have values - capturing preventively');" +
                    "            window.captureLoginCredentials();" +
                    "          }" +
                    "        }, 1000);" +
                    "      }" +
                    "    });" +
                    "  }" +
                    "  " +
                    "  setupAutoCapture();" +
                    "  " +
                    "  // Add visible capture button to page" +
                    "  function addCaptureButton() {" +
                    "    if (document.getElementById('claude-capture-btn')) return;" +
                    "    " +
                    "    var btn = document.createElement('button');" +
                    "    btn.id = 'claude-capture-btn';" +
                    "    btn.innerHTML = '🔐 Save Credentials for Fingerprint Login';" +
                    "    btn.type = 'button';" +
                    "    btn.style.cssText = 'position: fixed; top: 10px; right: 10px; z-index: 9999; padding: 10px; background: #007bff; color: white; border: none; border-radius: 5px; font-size: 14px; cursor: pointer; box-shadow: 0 2px 5px rgba(0,0,0,0.3);';" +
                    "    " +
                    "    btn.onclick = function() {" +
                    "      var result = window.captureLoginCredentials();" +
                    "      if (result === 'CAPTURED') {" +
                    "        btn.innerHTML = '✅ Credentials Saved!';" +
                    "        btn.style.background = '#28a745';" +
                    "        setTimeout(function() { btn.style.display = 'none'; }, 3000);" +
                    "      } else {" +
                    "        btn.innerHTML = '❌ Please fill form first';" +
                    "        btn.style.background = '#dc3545';" +
                    "        setTimeout(function() {" +
                    "          btn.innerHTML = '🔐 Save Credentials for Fingerprint Login';" +
                    "          btn.style.background = '#007bff';" +
                    "        }, 3000);" +
                    "      }" +
                    "    };" +
                    "    " +
                    "    document.body.appendChild(btn);" +
                    "    console.log('Capture button added to page');" +
                    "  }" +
                    "  " +
                    "  // Add button immediately and after delays" +
                    "  addCaptureButton();" +
                    "  setTimeout(addCaptureButton, 1000);" +
                    "  setTimeout(addCaptureButton, 2000);" +
                    "  " +
                    "  // Retry setup after delays" +
                    "  setTimeout(setupAutoCapture, 1000);" +
                    "  setTimeout(setupAutoCapture, 2000);" +
                    "  " +
                    "  // Test immediate capture if fields are already filled" +
                    "  setTimeout(function() {" +
                    "    var result = window.captureLoginCredentials();" +
                    "    console.log('Immediate capture attempt: ' + result);" +
                    "  }, 500);" +
                    "  " +
                    "  // Add polling mechanism as backup" +
                    "  var pollCount = 0;" +
                    "  var captured = false;" +
                    "  function pollForCredentials() {" +
                    "    if (captured || pollCount >= 30) return;" +
                    "    pollCount++;" +
                    "    " +
                    "    var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "    var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "    " +
                    "    if (emailField && passwordField && emailField.value && passwordField.value) {" +
                    "      var email = emailField.value;" +
                    "      var password = passwordField.value;" +
                    "      if (email.includes('@') && password.length > 1) {" +
                    "        captured = true;" +
                    "        console.log('POLLING CAPTURE SUCCESS after ' + pollCount + ' attempts');" +
                    "        if (typeof Android !== 'undefined') {" +
                    "          Android.captureCredentials(email, password);" +
                    "        }" +
                    "      }" +
                    "    }" +
                    "    " +
                    "    if (!captured) {" +
                    "      setTimeout(pollForCredentials, 2000);" +
                    "    }" +
                    "  }" +
                    "  setTimeout(pollForCredentials, 3000);" +
                    "  " +
                    "  return 'SCRIPT_INJECTED';" +
                    "})();";

            webView.evaluateJavascript(script, value -> {
                android.util.Log.d("MainActivity", "Capture script injection result: " + value);
            });
        }
    }

    /**
     * Check current authentication status and decide the flow
     */
    private void checkAuthenticationStatus() {
        // If biometric is suppressed, just load the page without authentication
        if (suppressBiometric) {
            android.util.Log.d("MainActivity", "Biometric suppressed - loading without authentication");
            loadLoginPage();
            return;
        }
        
        BiometricHelper.AuthenticationFlow flow = biometricHelper.determineAuthenticationFlow();

        android.util.Log.d("MainActivity", "=== Authentication Status Check ===");
        android.util.Log.d("MainActivity", "Authentication flow: " + flow);
        android.util.Log.d("MainActivity", "IsResumingFromBackground: " + isResumingFromBackground);
        android.util.Log.d("MainActivity", "SuppressBiometric: " + suppressBiometric);
        android.util.Log.d("MainActivity", biometricHelper.getDebugInfo());

        switch (flow) {
            case BIOMETRIC_PROMPT:
                // Skip biometric prompt if resuming from background with valid session
                if (isResumingFromBackground && biometricHelper.isUserLoggedIn() && biometricHelper.isSessionValid()) {
                    android.util.Log.d("MainActivity", "✓ Skipping biometric prompt - resuming with valid session");
                    isResumingFromBackground = false;
                    
                    // Load last URL or login page without biometric prompt
                    android.content.SharedPreferences prefs = getSharedPreferences("WebViewState", MODE_PRIVATE);
                    String lastUrl = prefs.getString("lastUrl", null);
                    if (lastUrl != null && !lastUrl.toLowerCase().contains("login")) {
                        webView.loadUrl(lastUrl);
                    } else {
                        loadLoginPage();
                    }
                    break;
                }
                
                android.util.Log.d("MainActivity", "✓ Showing biometric prompt for authentication");
                Toast.makeText(this, "Use your fingerprint to login", Toast.LENGTH_LONG).show();

                webView.postDelayed(() -> {
                    if (!suppressBiometric) {
                        biometricHelper.authenticateWithBiometric();
                    } else {
                        android.util.Log.d("MainActivity", "Biometric suppressed - skipping authentication");
                    }
                }, 500);
                break;

            case AUTO_LOGIN_SESSION:
                android.util.Log.d("MainActivity", "✓ Auto-login with stored session and credentials");
                isResumingFromBackground = false; // Reset flag
                Toast.makeText(this, "Restoring previous session...", Toast.LENGTH_SHORT).show();
                restoreCookiesForSession();

                // Check if we have credentials to auto-fill
                if (biometricHelper.hasStoredCredentials()) {
                    loadLoginPageAndAutoFill();
                } else {
                    loadLoginPage();
                }
                break;

            case MANUAL_LOGIN:
                android.util.Log.d("MainActivity", "✓ Loading login page for manual login");
                isResumingFromBackground = false; // Reset flag
                loadLoginPage();
                break;
        }
    }

    /**
     * Load the login page
     */
    private void loadLoginPage() {
        progressBar.setVisibility(View.VISIBLE);
        webView.loadUrl(WEBSITE_URL);
    }

    /**
     * Check if user has successfully logged in based on URL patterns
     */
    private void checkLoginStatus(String url) {
        android.util.Log.d("MainActivity", "Checking login status for URL: " + url);


        if (!url.contains("/Login") && !url.contains("/login") &&
                !url.contains("/Auth") && !url.contains("/auth") &&
                !url.equals(WEBSITE_URL)) {

            android.util.Log.d("MainActivity", "User appears to be logged in");
            onLoginSuccess();
        } else if (url.contains("/Login") || url.contains("/login")) {
            android.util.Log.d("MainActivity", "User is on login page");
            onLoginPage();
        }
    }

    /**
     * Handle successful login
     */
    private void onLoginSuccess() {
        android.util.Log.d("MainActivity", "onLoginSuccess called");


        // CRITICAL: Try to capture credentials after successful login
        android.util.Log.d("MainActivity", "Attempting post-login credential capture...");
        webView.postDelayed(() -> {
            manuallyTriggerCapture();
        }, 1000);

        biometricHelper.setUserLoggedIn(true);
        storeCookiesForSession();

        if (!biometricHelper.isBiometricEnabled() && biometricHelper.isBiometricAvailable()) {
            webView.postDelayed(() -> promptForBiometricSetup(), 3000);
        }
    }

    /**
     * Auto-fill and submit login form using JavaScript interface
     */
    private void autoFillAndSubmitLogin() {
        android.util.Log.d("MainActivity", "=== AUTO-FILL ATTEMPT ===");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Use the working auto-fill approach that we tested
            String script = "javascript:" +
                    "var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]');" +
                    "var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "var submitButton = document.querySelector('button[type=\"submit\"]') || document.querySelector('input[type=\"submit\"]');" +
                    "if (emailField && passwordField) {" +
                    "  emailField.value = Android.getEmail();" +
                    "  passwordField.value = Android.getPassword();" +
                    "  emailField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "  passwordField.dispatchEvent(new Event('input', {bubbles: true}));" +
                    "  emailField.dispatchEvent(new Event('change', {bubbles: true}));" +
                    "  passwordField.dispatchEvent(new Event('change', {bubbles: true}));" +
                    "  setTimeout(function() {" +
                    "    if (submitButton) {" +
                    "      submitButton.click();" +
                    "    } else {" +
                    "      var form = emailField.closest('form');" +
                    "      if (form) form.submit();" +
                    "    }" +
                    "  }, 500);" +
                    "  'SUCCESS';" +
                    "} else {" +
                    "  'FIELDS_NOT_FOUND';" +
                    "}";

            webView.evaluateJavascript(script, result -> {
                android.util.Log.d("MainActivity", "Auto-fill result: " + result);
                
                // Check if fields were found and filled
                if (result != null && (result.contains("SUCCESS") || result.equals("\"SUCCESS\""))) {
                    android.util.Log.d("MainActivity", "✓ Auto-fill completed successfully");
                                            // Hide progress bar after submission
                    webView.postDelayed(() -> progressBar.setVisibility(View.GONE), 3000);
                } else {
                    android.util.Log.w("MainActivity", "Auto-fill failed: " + result);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Auto-login failed. Please login manually.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * Test auto-fill functionality
     */
    private void testAutoFill() {
        android.util.Log.d("MainActivity", "=== TESTING AUTO-FILL ===");

        // First check stored credentials
        String storedEmail = biometricHelper.getStoredEmail();
        String storedPassword = biometricHelper.getStoredPassword();

        android.util.Log.d("MainActivity", "Stored email: " +
                (storedEmail.length() > 3 ? storedEmail.substring(0, 3) + "***" : "EMPTY"));
        android.util.Log.d("MainActivity", "Stored password length: " + storedPassword.length());

        if (storedEmail.isEmpty() || storedPassword.isEmpty()) {
            Toast.makeText(this, "No stored credentials to test with", Toast.LENGTH_LONG).show();
            return;
        }

        // Now test the form
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            String script = "javascript:(function() {" +
                    "  var result = {" +
                    "    url: window.location.href," +
                    "    hasAndroidInterface: typeof Android !== 'undefined'," +
                    "    emailField: !!document.getElementById('Email')," +
                    "    passwordField: !!document.getElementById('Password')," +
                    "    submitButton: !!document.getElementById('login-submit')," +
                    "    forms: document.querySelectorAll('form').length" +
                    "  };" +
                    "  if (typeof Android !== 'undefined') {" +
                    "    Android.log('Test result: ' + JSON.stringify(result));" +
                    "  }" +
                    "  return JSON.stringify(result);" +
                    "})();";

            webView.evaluateJavascript(script, value -> {
                android.util.Log.d("MainActivity", "Form test result: " + value);

                try {
                    if (value != null && !value.equals("null")) {
                        value = value.replace("\\\"", "\"");
                        if (value.startsWith("\"")) value = value.substring(1);
                        if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);

                        org.json.JSONObject result = new org.json.JSONObject(value);
                        boolean hasInterface = result.optBoolean("hasAndroidInterface", false);
                        boolean hasEmail = result.optBoolean("emailField", false);
                        boolean hasPassword = result.optBoolean("passwordField", false);

                        String message = "Test Results:\n";
                        message += "Android Interface: " + (hasInterface ? "✓" : "✗") + "\n";
                        message += "Email Field: " + (hasEmail ? "✓" : "✗") + "\n";
                        message += "Password Field: " + (hasPassword ? "✓" : "✗") + "\n";
                        message += "Forms: " + result.optInt("forms", 0);

                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                        // If on login page, try auto-fill
                        if (value.contains("Login") && hasEmail && hasPassword) {
                            autoFillAndSubmitLogin();
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error parsing test result", e);
                }
            });
        }
    }

    /**
     * Store cookies for session persistence
     */
    private void storeCookiesForSession() {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(WEBSITE_URL);
        if (cookies != null && !cookies.isEmpty()) {
            biometricHelper.storeSessionCookies(cookies);
            android.util.Log.d("MainActivity", "Cookies stored for session");
        }
    }

    /**
     * Restore cookies for automatic login
     */
    private void restoreCookiesForSession() {
        String cookies = biometricHelper.getStoredSessionCookies();
        if (cookies != null && !cookies.isEmpty()) {
            CookieManager cookieManager = CookieManager.getInstance();
            String[] cookieArray = cookies.split(";");
            for (String cookie : cookieArray) {
                cookieManager.setCookie(WEBSITE_URL, cookie.trim());
            }
            cookieManager.flush();
            android.util.Log.d("MainActivity", "Restored cookies for session");
        }
    }

    /**
     * Handle being on login page
     */
    private void onLoginPage() {
        android.util.Log.d("MainActivity", "User is on login page");

        boolean wasLoggedIn = biometricHelper.isUserLoggedIn();
        boolean sessionValid = biometricHelper.isSessionValid();
        
        // Only clear session if user was logged in but session is expired
        // Don't clear if session is still valid (user just minimized app)
        if (wasLoggedIn && !sessionValid) {
            android.util.Log.d("MainActivity", "Session expired, clearing session");
            biometricHelper.clearStoredSession();
        } else if (wasLoggedIn && sessionValid) {
            android.util.Log.d("MainActivity", "Session still valid, keeping login state");
        }
    }

    /**
     * Detect logout
     */
    private void detectLogout(String url) {
        if (url.contains("/Logout") || url.contains("/logout") ||
                url.contains("/SignOut") || url.contains("/signout")) {
            android.util.Log.d("MainActivity", "Logout detected");
            biometricHelper.clearStoredSession();
            Toast.makeText(this, "You have been logged out.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Prompt user to enable biometric authentication
     */
    private void promptForBiometricSetup() {
        android.util.Log.d("MainActivity", "Prompting for biometric setup");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enable Fingerprint Login")
                .setMessage("Would you like to enable fingerprint authentication for faster login next time?")
                .setPositiveButton("Enable", (dialog, which) -> {
                    biometricHelper.setBiometricEnabled(true);
                    Toast.makeText(this, "Fingerprint login enabled! Your credentials have been saved securely.", Toast.LENGTH_LONG).show();

                    // Verify credentials were saved
                    String email = biometricHelper.getStoredEmail();
                    String password = biometricHelper.getStoredPassword();
                    if (email.isEmpty() || password.isEmpty()) {
                        android.util.Log.w("MainActivity", "WARNING: Biometric enabled but no credentials stored!");
                        Toast.makeText(this, "Warning: No credentials saved. Please login again to save them.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    android.util.Log.d("MainActivity", "User declined biometric setup");
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Reset biometric settings (for testing)
     */
    private void resetBiometricSettings() {
        biometricHelper.resetBiometricSettings();
        Toast.makeText(this, "All settings reset. Please login again.", Toast.LENGTH_LONG).show();
        loadLoginPage();
    }

    /**
     * Load login page and automatically fill credentials
     */
    private void loadLoginPageAndAutoFill() {
        android.util.Log.d("MainActivity", "=== LOADING LOGIN PAGE FOR AUTO-FILL ===");

        // First verify we have credentials
        String email = biometricHelper.getStoredEmail();
        String password = biometricHelper.getStoredPassword();

        android.util.Log.d("MainActivity", "Credentials check - Email: " +
                (email.length() > 3 ? email.substring(0, 3) + "***" : "EMPTY"));
        android.util.Log.d("MainActivity", "Credentials check - Password length: " + password.length());

        if (email.isEmpty() || password.isEmpty()) {
            android.util.Log.e("MainActivity", "NO CREDENTIALS STORED - Cannot auto-fill!");
            Toast.makeText(this, "No saved credentials found. Please login manually.", Toast.LENGTH_LONG).show();
            loadLoginPage();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        webView.setWebViewClient(new WebViewClient() {
            private boolean autoFillTriggered = false;

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);

                android.util.Log.d("MainActivity", "Page loaded for auto-fill: " + url);

                // Only auto-fill on login page and if not already triggered
                if (!autoFillTriggered && (url.contains("/Login") || url.contains("/login") || url.equals(WEBSITE_URL))) {
                    autoFillTriggered = true;
                    android.util.Log.d("MainActivity", "Triggering auto-fill on login page");

                    // Inject capture script first, then auto-fill
                    injectCaptureScript();

                    // Wait for page to fully render and script to be injected
                    webView.postDelayed(() -> {
                        android.util.Log.d("MainActivity", "Starting auto-fill after delay");
                        autoFillAndSubmitLogin();
                    }, 2000);
                } else if (!url.contains("/Login") && !url.contains("/login")) {
                    // We've navigated away from login page, assume success
                    android.util.Log.d("MainActivity", "Navigated away from login, assuming success");
                                setupNormalWebViewClient();
                    checkLoginStatus(url);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Toast.makeText(MainActivity.this, "Error: " + description, Toast.LENGTH_SHORT).show();
                        setupNormalWebViewClient();
            }
        });

        android.util.Log.d("MainActivity", "Loading login page: " + WEBSITE_URL);
        webView.loadUrl(WEBSITE_URL);
    }

    // BiometricAuthCallback implementation
    @Override
    public void onBiometricAuthenticationSuccess() {
        android.util.Log.d("MainActivity", "=== BIOMETRIC AUTHENTICATION SUCCESS ===");

        // Check stored credentials
        String email = biometricHelper.getStoredEmail();
        String password = biometricHelper.getStoredPassword();
        boolean hasCredentials = !email.isEmpty() && !password.isEmpty();

        android.util.Log.d("MainActivity", "Stored email: " +
                (email.length() > 3 ? email.substring(0, 3) + "***" : "EMPTY"));
        android.util.Log.d("MainActivity", "Stored password length: " + password.length());
        android.util.Log.d("MainActivity", "Has credentials: " + hasCredentials);

        Toast.makeText(this, hasCredentials ?
                        "Authentication successful! Logging you in..." :
                        "Authentication successful! No saved credentials found.",
                Toast.LENGTH_LONG).show();

        if (hasCredentials) {
            loadLoginPageAndAutoFill();
        } else {
            // No stored credentials - try Force Direct Capture first, then load login page
            Toast.makeText(this, "Attempting to capture current credentials...", Toast.LENGTH_SHORT).show();
            tryForceDirectCaptureAndProceed();
        }
    }

    /**
     * Try Force Direct Capture when fingerprint authentication succeeds but no credentials stored
     */
    private void tryForceDirectCaptureAndProceed() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Use exact same script as Force Direct Capture
            String script = "javascript:(function() {" +
                    "  var emailField = document.querySelector('input[type=\"email\"]') || document.querySelector('input[name*=\"mail\" i]') || document.querySelector('input[name*=\"user\" i]');" +
                    "  var passwordField = document.querySelector('input[type=\"password\"]');" +
                    "  " +
                    "  if (emailField && passwordField) {" +
                    "    var email = emailField.value;" +
                    "    var password = passwordField.value;" +
                    "    " +
                    "    if (email && password) {" +
                    "      if (typeof Android !== 'undefined') {" +
                    "        Android.captureCredentials(email, password);" +
                    "        return 'SUCCESS: Captured ' + email;" +
                    "      } else {" +
                    "        return 'ERROR: No Android interface';" +
                    "      }" +
                    "    } else {" +
                    "      return 'ERROR: Empty values - Email: ' + (email || 'EMPTY') + ', Password: ' + (password ? 'FILLED' : 'EMPTY');" +
                    "    }" +
                    "  } else {" +
                    "    return 'ERROR: Fields not found';" +
                    "  }" +
                    "})();";

            webView.evaluateJavascript(script, result -> {
                String message = result != null ? result.replace("\"", "") : "No result";
                android.util.Log.d("MainActivity", "Force Direct Capture result: " + message);
                
                if (message.startsWith("SUCCESS")) {
                    Toast.makeText(this, "Credentials captured! Now auto-filling...", Toast.LENGTH_SHORT).show();
                    // Wait a moment for storage to complete, then auto-fill
                    webView.postDelayed(() -> {
                        loadLoginPageAndAutoFill();
                    }, 1000);
                } else {
                    Toast.makeText(this, "No credentials found in current page. Loading login...", Toast.LENGTH_SHORT).show();
                    loadLoginPage();
                }
            });
        } else {
            loadLoginPage();
        }
    }

    @Override
    public void onBiometricAuthenticationError(String error) {
        android.util.Log.e("MainActivity", "Biometric authentication error: " + error);
        Toast.makeText(this, "Authentication error: " + error, Toast.LENGTH_LONG).show();
        biometricHelper.setBiometricEnabled(false);
        loadLoginPage();
    }

    @Override
    public void onBiometricAuthenticationFailed() {
        android.util.Log.w("MainActivity", "Biometric authentication failed");
        Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show();

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Authentication Failed")
                .setMessage("Would you like to try again or login manually?")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    if (!suppressBiometric) {
                        biometricHelper.authenticateWithBiometric();
                    } else {
                        android.util.Log.d("MainActivity", "Biometric suppressed - cannot retry");
                        Toast.makeText(this, "Please wait and try again", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Manual Login", (dialog, which) -> {
                    loadLoginPage();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onBiometricAuthenticationCancelled() {
        android.util.Log.d("MainActivity", "Biometric authentication cancelled");
        Toast.makeText(this, "Authentication cancelled. Please login manually.", Toast.LENGTH_SHORT).show();
        loadLoginPage();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Restore session cookies when app is resumed
     */
    private void restoreSessionCookies() {
        try {
            if (biometricHelper == null) {
                android.util.Log.e("MainActivity", "BiometricHelper is null, cannot restore cookies");
                return;
            }
            
            // Get stored session cookies from BiometricHelper
            String storedCookies = biometricHelper.getStoredSessionCookies();
            
            if (storedCookies != null && !storedCookies.isEmpty()) {
                android.util.Log.d("MainActivity", "Restoring session cookies: " + storedCookies.length() + " chars");
                
                CookieManager cookieManager = CookieManager.getInstance();
                if (cookieManager != null) {
                    String[] cookies = storedCookies.split(";");
                    
                    // Set each cookie
                    for (String cookie : cookies) {
                        if (cookie != null && !cookie.trim().isEmpty()) {
                            cookieManager.setCookie("https://oracoreai.com", cookie.trim());
                        }
                    }
                    
                    // Force sync
                    cookieManager.flush();
                    android.util.Log.d("MainActivity", "Session cookies restored");
                }
            } else {
                android.util.Log.d("MainActivity", "No stored session cookies to restore");
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error restoring session cookies", e);
        }
    }

    /**
     * Preserve session data when app is minimized
     */
    private void preserveSessionData() {
        try {
            if (biometricHelper == null) {
                android.util.Log.e("MainActivity", "BiometricHelper is null, cannot preserve session");
                return;
            }
            
            // Capture and store current cookies
            CookieManager cookieManager = CookieManager.getInstance();
            if (cookieManager != null) {
                String currentCookies = cookieManager.getCookie("https://oracoreai.com");
                
                if (currentCookies != null && !currentCookies.isEmpty()) {
                    biometricHelper.storeSessionCookies(currentCookies);
                    android.util.Log.d("MainActivity", "Session cookies captured and stored: " + currentCookies.length() + " chars");
                }
                
                // Flush cookies to persistent storage
                cookieManager.flush();
            }
            
            // Store current URL to restore later
            if (webView != null) {
                String currentUrl = webView.getUrl();
                if (currentUrl != null && !currentUrl.toLowerCase().contains("login")) {
                    // Save non-login URLs to return to them
                    android.content.SharedPreferences prefs = getSharedPreferences("WebViewState", MODE_PRIVATE);
                    if (prefs != null) {
                        prefs.edit().putString("lastUrl", currentUrl).apply();
                        android.util.Log.d("MainActivity", "Saved URL for restoration: " + currentUrl);
                    }
                }
            }
            
            android.util.Log.d("MainActivity", "Session data preserved");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error preserving session data", e);
        }
    }

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            android.os.Bundle webViewBundle = new android.os.Bundle();
            webView.saveState(webViewBundle);
            outState.putBundle("webViewState", webViewBundle);
            android.util.Log.d("MainActivity", "WebView state saved to bundle");
        }
        outState.putBoolean("isResumingFromBackground", isResumingFromBackground);
        android.util.Log.d("MainActivity", "Saved isResumingFromBackground: " + isResumingFromBackground);
    }
    
    @Override
    protected void onRestoreInstanceState(android.os.Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        isResumingFromBackground = savedInstanceState.getBoolean("isResumingFromBackground", false);
        android.util.Log.d("MainActivity", "Restored isResumingFromBackground: " + isResumingFromBackground);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop native microphone maintenance
        stopNativeMicrophoneMaintenance();
        
        // Stop the foreground service
        try {
            Intent serviceIntent = new Intent(this, WebViewService.class);
            stopService(serviceIntent);
            android.util.Log.d("MainActivity", "WebView service stopped");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error stopping WebView service", e);
        }
        
        if (webView != null) {
            webView.destroy();
        }
    }
}
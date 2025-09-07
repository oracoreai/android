# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
OraCore AI App is an Android application that wraps a web-based authentication system in a secure WebView with biometric login capabilities. The app captures and stores login credentials securely, enabling automatic authentication via fingerprint on subsequent app launches.

## Common Development Commands

### Build and Testing
```bash
# Build the project (from project root)
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests on connected device
./gradlew connectedAndroidTest

# Clean build artifacts
./gradlew clean

# Check for lint issues
./gradlew lint

# Generate lint report
./gradlew lintDebug
```

### Installation and Debugging
```bash
# Install debug APK to connected device
./gradlew installDebug

# Install and run on device
./gradlew installDebug && adb shell am start -n com.example.oracoreaiapp/.MainActivity

# View relevant logs (filter by tags)
adb logcat -s MainActivity BiometricHelper SecureStorage

# View WebView JavaScript logs
adb logcat -s WebView-JS
```

## Architecture Overview

### Core Components
- **MainActivity**: Primary activity hosting a WebView for authentication flow
- **BiometricHelper**: Manages biometric authentication and credential storage
- **SecureStorage**: Handles encrypted storage using Android Keystore
- **WebAppInterface**: JavaScript bridge between WebView and native Android code

### Authentication Flow
The app implements a three-tier authentication system that determines the appropriate flow based on stored state:

1. **Biometric Authentication**: If biometric is enabled, available, and credentials are stored
2. **Session Restoration**: If user is logged in with a valid session (within 24 hours)
3. **Manual Login**: Default fallback requiring user interaction

The flow is determined by `BiometricHelper.determineAuthenticationFlow()` which evaluates stored preferences and device capabilities.

### Key Features
- **Secure Credential Storage**: Uses EncryptedSharedPreferences with Android Keystore
- **Session Management**: Tracks login status and session validity (24-hour timeout)
- **Auto-fill Capability**: JavaScript injection for automatic form filling
- **Credential Capture**: Automatically captures login credentials for future use
- **Debug Interface**: Long-press WebView to access debug menu

### Security Considerations
- Credentials stored using EncryptedSharedPreferences with AES256_GCM encryption
- Biometric authentication required for credential access
- Session timeout mechanism prevents indefinite access
- WebView debugging enabled (should be disabled in production)
- Cleartext traffic allowed (required for HTTP connections)

## File Structure
```
app/src/main/java/com/example/oracoreaiapp/
├── MainActivity.java           # Main activity with WebView and auth logic
├── BiometricHelper.java       # Biometric authentication management
├── SecureStorage.java         # Encrypted storage utilities
└── ui/theme/                  # Kotlin theme files (Color.kt, Theme.kt, Type.kt)
```

## Gradle Configuration
- **AGP Version**: 8.12.1 (defined in libs.versions.toml, but project uses traditional build.gradle)
- **Target SDK**: 34
- **Min SDK**: 23
- **Java Version**: 1.8 (sourceCompatibility and targetCompatibility)
- **Build Features**: ViewBinding enabled
- **Dependencies**: Direct dependencies in app/build.gradle (not using version catalog)

## Key Dependencies
- AndroidX AppCompat, Material Design, ConstraintLayout
- WebKit for enhanced WebView functionality
- Biometric authentication library
- Security-crypto for encrypted storage
- JSON parsing for credential handling

## Testing Strategy
- Unit tests: `app/src/test/` (ExampleUnitTest.kt)
- Instrumented tests: `app/src/androidTest/` (ExampleInstrumentedTest.kt)
- Test runner: AndroidJUnitRunner

## Development Notes
- WebView debugging is enabled for development purposes
- The app connects to a specific Azure-hosted login endpoint
- JavaScript interface "Android" is injected into WebView for native communication
- Credential capture happens automatically on form submission and can be manually triggered
- Debug menu available via long-press on WebView (shows stored credentials, test auto-fill, reset settings)
- JavaScript injection scripts are comprehensively logged for debugging form field detection

## Critical Implementation Details

### JavaScript Bridge Interface
The WebView exposes an "Android" interface with these methods:
- `captureCredentials(email, password)`: Store credentials from JavaScript
- `getStoredCredentials()`: Return JSON with stored credentials  
- `hasCredentials()`: Check if credentials exist
- `log(message)`: Send debug messages to Android logs

### Authentication State Management
Key SharedPreferences keys used by BiometricHelper:
- `user_logged_in`: Boolean session state
- `biometric_enabled`: User preference for biometric auth
- `stored_email/stored_password`: Encrypted credential storage
- `session_cookies`: Stored web session data
- `last_login_time`: Timestamp for session timeout calculation

### Constants
- Login URL: `https://net-core-web20250815190920-gccgc8d4fjh9f4g4.westus3-01.azurewebsites.net/Login/LocalLogin`
- Session timeout: 24 hours
- Max auto-fill attempts: 3

## Permissions Required
- INTERNET and ACCESS_NETWORK_STATE for web connectivity
- USE_BIOMETRIC for fingerprint authentication
- RECORD_AUDIO and READ_EXTERNAL_STORAGE (optional, requested at runtime)
# Android WebView App with Biometric Authentication

A native Android application that wraps a website with biometric authentication capabilities.

## Features

- 🔐 **Biometric Authentication**: Fingerprint authentication for secure access
- 🌐 **WebView Integration**: Full-screen WebView with the target website
- 💾 **Session Persistence**: Maintains login state across app launches
- 🎤 **Microphone Access**: Full support for web-based audio recording
- 🔄 **Background State Management**: Keeps session active when app is minimized
- 🛡️ **Security**: SSL certificate pinning and secure storage

## Target Website

The app loads: `https://net-core-web20250815190920-gccgc8d4fjh9f4g4.westus3-01.azurewebsites.net/ModernLogin/LocalLogin`

## Setup Instructions

### Prerequisites

1. **Android Studio** (latest version recommended)
2. **Android SDK** with API level 34
3. **Java JDK** 11 or higher
4. **Android device** with API level 23+ for testing

### Installation

1. **Clone or download the project**
   ```bash
   cd C:\git\android-app
   ```

2. **Configure Android SDK path**
   - Open `local.properties` file
   - Uncomment and set the correct SDK path for your system:
   ```properties
   # For Windows:
   sdk.dir=C\\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   
   # For macOS:
   sdk.dir=/Users/yourusername/Library/Android/sdk
   
   # For Linux:
   sdk.dir=/home/yourusername/Android/Sdk
   ```

3. **Open in Android Studio**
   - Launch Android Studio
   - Click "Open an existing Android Studio project"
   - Navigate to the `C:\git\android-app` folder
   - Click "OK"

4. **Sync Project**
   - Android Studio should automatically prompt to sync
   - If not, click "Sync Now" in the notification bar
   - Or go to File → Sync Project with Gradle Files

### Building the App

#### Using Android Studio
1. Connect your Android device or start an emulator
2. Click the "Run" button (green play icon)
3. Select your target device
4. The app will build and install automatically

#### Using Command Line
```bash
# Windows
gradlew.bat assembleDebug

# macOS/Linux
./gradlew assembleDebug
```

## App Flow

### First Launch
1. **Splash Screen**: Shows app logo and loading indicator
2. **Manual Login**: User logs in through the website's login form
3. **Biometric Setup**: After successful login, biometric authentication is enabled for future use

### Subsequent Launches
1. **Splash Screen**: Brief loading screen
2. **Biometric Authentication**: User authenticates with fingerprint
3. **Website Loading**: Loads the website with preserved session

### Background Behavior
- When minimized, the app starts a foreground service to maintain session
- JavaScript continues running for audio recording functionality
- Session state is preserved using secure encrypted storage

## Configuration

### Changing Target Website
Edit the `targetUrl` variable in `MainActivity.kt`:
```kotlin
private val targetUrl = "YOUR_WEBSITE_URL_HERE"
```

### Session Timeout
Modify the session validity period in `SessionManager.kt`:
```kotlin
private const val SESSION_VALIDITY_HOURS = 24L // Change this value
```

### Security Settings
Update SSL certificate pins in `network_security_config.xml` for production use.

## Permissions

The app requests the following permissions:
- `INTERNET` - For web access
- `RECORD_AUDIO` - For microphone access in WebView
- `USE_BIOMETRIC` / `USE_FINGERPRINT` - For biometric authentication
- `FOREGROUND_SERVICE` - For background session maintenance
- `WAKE_LOCK` - For keeping device awake during important operations
- `ACCESS_NETWORK_STATE` - For monitoring network connectivity

## Architecture

- **MVVM Pattern**: Separation of concerns with proper architecture
- **Encrypted Storage**: Uses AndroidX Security library for secure data storage
- **Background Services**: Foreground service for maintaining app state
- **Modern Android Components**: Jetpack libraries for stability and compatibility

## Security Features

1. **Certificate Pinning**: SSL certificate validation for the target domain
2. **Secure Storage**: Encrypted SharedPreferences for sensitive data
3. **WebView Security**: Restricted file access and mixed content handling
4. **Biometric Security**: Hardware-backed biometric authentication
5. **Session Management**: Secure session tokens and timeout handling

## Troubleshooting

### Common Issues

1. **Build Errors**
   - Ensure Android SDK path is correctly set in `local.properties`
   - Check that API level 34 is installed in SDK Manager

2. **Biometric Authentication Not Working**
   - Verify device has fingerprint hardware
   - Ensure at least one fingerprint is enrolled
   - Check device security settings

3. **Audio Recording Issues**
   - Grant microphone permission when prompted
   - Ensure device has microphone hardware
   - Check WebView audio settings

### Logs and Debugging
Enable debugging in Android Studio to view detailed logs:
- View → Tool Windows → Logcat
- Filter by package name: `com.webviewapp`

## Production Deployment

Before releasing to production:

1. **Update Certificate Pins**: Add actual SSL certificate pins to `network_security_config.xml`
2. **Enable ProGuard**: Ensure obfuscation is enabled for release builds
3. **Test Thoroughly**: Test on various devices and Android versions
4. **Update App Signing**: Configure proper app signing for Play Store
5. **Performance Testing**: Monitor memory usage and battery impact

## Support

For issues or questions about this application, please check the implementation details in the source code or consult Android development documentation.
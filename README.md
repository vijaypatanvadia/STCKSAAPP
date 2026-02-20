# STCKSA Android App — Build & Publish Guide

## 📦 Project Info
- **App Name:** STCKSA
- **Package:** com.stcksa.app
- **URL:** http://wap.yallanelaab.com/STCKSA/Campaign/Promo.aspx?ctg=APP
- **Min Android:** 5.0 (API 21)
- **Target Android:** 14 (API 34)

---

## ✅ Features
- Full WebView loading your campaign URL
- Loading progress bar (blue)
- Back navigation support (press Back to go to previous web page)
- No internet connection screen with Retry button
- Fullscreen (no action bar)

---

## 🛠️ Step 1 — Setup Android Studio

1. Download and install **Android Studio** from:
   https://developer.android.com/studio

2. During setup, install:
   - Android SDK (API 34)
   - Android Emulator (optional, for testing)

---

## 📂 Step 2 — Open the Project

1. Extract the STCKSA project folder
2. Open **Android Studio**
3. Click **"Open"** → select the `STCKSA` folder
4. Wait for Gradle sync to finish (may take a few minutes first time)

---

## 🎨 Step 3 — Add App Icon (Important for Play Store)

You need to replace the default launcher icon:

1. In Android Studio, right-click `app/src/main/res`
2. Select **New → Image Asset**
3. Under "Source Asset", choose your logo image file
4. Click **Next → Finish**

This auto-generates icons for all screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).

---

## 📱 Step 4 — Test on Device or Emulator

**On Physical Device:**
1. Enable **Developer Options** on your Android phone:
   - Go to Settings → About Phone → tap "Build Number" 7 times
2. Enable **USB Debugging** in Developer Options
3. Connect phone via USB
4. In Android Studio, select your device from the dropdown
5. Click the ▶️ **Run** button

**On Emulator:**
1. In Android Studio, click **Device Manager** (right panel)
2. Click **Create Device** → choose a phone → select API 34 → Finish
3. Click ▶️ to start the emulator, then Run the app

---

## 🔑 Step 5 — Generate a Signed APK / AAB (for Play Store)

Google Play requires a **signed** release build.

1. In Android Studio: **Build → Generate Signed Bundle / APK**
2. Choose **Android App Bundle (.aab)** ← recommended for Play Store
3. Click **Next**
4. Click **Create new...** to create a keystore:
   - **Key store path:** Choose a safe location (e.g., Desktop/stcksa-key.jks)
   - **Password:** Create a strong password (SAVE IT — you'll need it forever)
   - **Key alias:** `stcksa`
   - **Key password:** Same or different password
   - **Validity:** 25 years
   - Fill in your name/organization
5. Click **OK → Next**
6. Choose **release** build variant
7. Click **Finish**

⚠️ **IMPORTANT:** Keep your keystore file and passwords safe. If lost, you cannot update your app on Play Store.

The signed `.aab` file will be in:
`app/release/app-release.aab`

---

## 🌐 Step 6 — Create Google Play Developer Account

1. Go to: https://play.google.com/console
2. Sign in with your Google account
3. Pay the **one-time $25 registration fee**
4. Complete identity verification

---

## 🚀 Step 7 — Publish on Google Play

1. In Play Console, click **"Create app"**
2. Fill in:
   - App name: **STCKSA**
   - Default language: Choose your language
   - App or Game: **App**
   - Free or Paid: Choose one
3. Click **Create app**

### Fill in Store Listing:
- **Short description** (up to 80 chars): e.g., "STCKSA Campaign & Promotions"
- **Full description** (up to 4000 chars): describe your app
- **Screenshots:** At least 2 phone screenshots (take them from your emulator/device)
- **App icon:** 512x512 PNG
- **Feature graphic:** 1024x500 PNG (banner image)

### Upload your AAB:
1. Go to **Production → Releases → Create new release**
2. Upload your `app-release.aab` file
3. Add release notes (what's new)
4. Click **Review release → Start rollout**

### Complete Content Rating:
- Go to **Policy → App content → Content rating**
- Fill out the questionnaire honestly

### Set up Pricing & Distribution:
- Select countries where your app will be available

### Submit for Review:
- Google review typically takes **1–3 business days**

---

## 🔧 Troubleshooting

| Problem | Solution |
|---------|----------|
| Gradle sync fails | Check internet connection; File → Invalidate Caches |
| App shows blank screen | Check URL is correct and server is running |
| App crashes on launch | Check logcat in Android Studio for errors |
| Play Store rejects app | Ensure you have a privacy policy URL |

### Privacy Policy (Required by Google Play)
Google requires a privacy policy if your app loads web content.
You can create a free one at: https://www.privacypolicygenerator.info/

---

## 📋 Project File Structure

```
STCKSA/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/stcksa/app/
│   │   │   └── MainActivity.java      ← Main app logic
│   │   └── res/
│   │       ├── layout/
│   │       │   └── activity_main.xml  ← UI layout
│   │       └── values/
│   │           ├── strings.xml        ← App name
│   │           └── themes.xml         ← App theme/colors
│   ├── build.gradle                   ← App dependencies
│   └── proguard-rules.pro
├── build.gradle                       ← Project config
├── settings.gradle                    ← Module settings
└── gradle/wrapper/
    └── gradle-wrapper.properties      ← Gradle version
```

---

## 🆘 Need Help?

- Android Studio Help: https://developer.android.com/studio/intro
- Google Play Console Help: https://support.google.com/googleplay/android-developer

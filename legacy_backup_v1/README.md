# Bixby Voice Assistant — One UI 9 Style

A Bixby-inspired Android voice assistant for the Samsung Galaxy A16 5G, featuring a modern One UI-inspired floating interface, Gemini-powered conversational AI, speech recognition, and text-to-speech.

> This project uses an original Bixby-inspired visual implementation. It does not include Samsung proprietary source code or proprietary assets.

## ✨ Features

- 🎙️ **Voice STT** — tap the microphone and speak; partial and final speech results are shown in the floating bar.
- 🤖 **Gemini AI** — sends recognized text to a Gemini model asynchronously and displays the generated response.
- 🔊 **TTS** — reads AI responses aloud.
- 🌌 **Floating Assistant UI** — modern bottom floating bar with a layered glowing orb.
- 🪟 **Glassmorphism** — translucent dark floating surface with subtle borders/glow.
- 📱 **One UI-inspired Response Sheet** — rounded bottom response surface with typing effect.
- 💫 **Orb animation** — pulses while the assistant is actively speaking.
- 🔐 **Local API-key configuration** — the Gemini key is supplied through `local.properties` and is never hardcoded in Kotlin source.

## 🧰 Tech Stack

- Kotlin
- Android SDK / AppCompat
- Kotlin Coroutines
- Google Gen AI Kotlin SDK
- Android `SpeechRecognizer`
- Android `TextToSpeech`
- XML-based UI and animations

## 🚀 Setup

### 1. Clone the repository

```bash
git clone https://github.com/hichandu810chandupathak-beep/BIXBY-VOICE-ASSISTANT-FINAL-VERSION.git
cd BIXBY-VOICE-ASSISTANT-FINAL-VERSION
```

Open the project in Android Studio and allow Gradle to sync.

### 2. Create a Gemini API key

Open **Google AI Studio** and create a Gemini API key:

https://aistudio.google.com/

Keep the key private. Do not commit it to GitHub.

### 3. Add the key to `local.properties`

In the project root, open (or create) `local.properties` and add:

```properties
GEMINI_API_KEY=your_key_here
```

For example:

```properties
GEMINI_API_KEY=AIza...your-real-key...
```

**Do not put the real key in `MainActivity.kt`, `AssistantAiHandler.kt`, or any other source file.**

`local.properties` is intended to remain local and should not be committed.

### 4. Build and run

In Android Studio:

1. Sync the project with Gradle.
2. Connect/unlock your Android phone and enable USB debugging, or use an Android emulator.
3. Select the **app** run configuration.
4. Build and run the application.
5. Grant microphone permission when requested.
6. Tap the microphone and speak.

The assistant should show the recognized speech, switch to **Thinking...**, request a Gemini response, display it in the response sheet, and read it aloud with TTS.

## 🔑 API-key note

The app reads the key from Gradle/`BuildConfig` rather than hardcoding it in Kotlin. This prevents accidental source-code commits of the key, but an API key embedded in a distributed Android APK can still potentially be extracted. For a production/public release, use a secure backend or an appropriate managed AI integration rather than shipping a long-lived unrestricted key in the APK.

## 📂 Main Components

- `MainActivity.kt` — coordinates STT, AI requests, response-sheet updates, typing effect, and TTS.
- `AssistantAiHandler.kt` — isolated Gemini AI boundary.
- `app/src/main/res/layout/activity_main.xml` — floating bar and response-sheet UI.
- `app/src/main/res/anim/orb_pulse.xml` — orb pulse animation.
- `app/build.gradle` — Android and Gemini dependencies plus API-key BuildConfig configuration.

## ⚠️ Requirements

- Android Studio with a compatible JDK/Android SDK.
- Internet access for Gemini requests.
- A valid Gemini API key.
- Microphone permission for voice input.
- The project is primarily designed and tested for the **Samsung Galaxy A16 5G**.

## 📄 License

This repository is a personal project. Review and add an explicit open-source license before redistributing it publicly.

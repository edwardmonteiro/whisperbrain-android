# WhisperBrain — personal Android prototype

A native Android starting point for live context and brief private advice.
Built for a workflow where your Android phone is your only computer.

**Version 0.1.1-alpha addresses the first phone report: speech produced no visible response.**
The exact cause on the phone is not yet confirmed. The code now avoids an indefinite VAD wait for manual analysis,
checks long detected speech every 20 seconds, and displays input/response progress. See `VALIDATION.json` for build status.
No paid AI requests were made during development. No API key is included.

## Build from your phone

1. Open this repository's **Actions** tab from Chrome on your phone.
2. Open the latest successful **Build WhisperBrain APK** run.
3. Download **WhisperBrain-Android-APK**, extract the ZIP, and open `app-debug.apk`.
4. For another build, choose **Build WhisperBrain APK → Run workflow**.

Source commits also start a build. No laptop or AI API key is needed to compile the app.
See [PHONE-SETUP.md](PHONE-SETUP.md) for installation and first-use instructions.

## What is implemented in the source

| Capability | This prototype |
| --- | --- |
| Audio capture | Native microphone streaming, PCM16 mono, 24 kHz |
| Context | OpenAI Realtime conversation context during each session |
| Model | Editable model ID; default `gpt-realtime-2.1-mini` |
| Advice timing | Analyses after pauses or 20 seconds of continuous detected speech; playback waits for quiet |
| Intervention | Model can choose silence, with visible context; **Analisar agora** explicitly commits audio and requests analysis |
| Diagnostics | Microphone level, sent bytes, detected speech, committed audio, request/reply counts; copyable without conversation or key |
| Voice | Installed offline Android TTS at a reduced playback volume |
| Private route | Calling-capable earbuds, or the phone earpiece |
| Session control | Explicit Start/Stop, ongoing notification, 10/20/45-minute time limits |
| Memory | Up to 20 reviewed notes, 400 characters each, encrypted with Android Keystore |
| Languages | English (US) or Brazilian Portuguese advice |
| API credentials | User-entered personal key encrypted locally; never embedded in the APK |
| Failure behavior | Stops capture on network failure, audio interruption, or detected route loss |

## First use after installing the APK

1. Run **Testar áudio privado**. No AI key or microphone capture is needed for that test.
2. If needed, use **Configurações de conexão e voz → Install Android voice data** to download an offline voice.
3. Add your own API key in the app's Connection settings. Do not paste it into chat or a repository.
4. Set a short goal, for example: “Help me identify unclear assumptions and ask one better question.”
5. Start a 10-minute session with participants who agree to AI assistance.
6. Speak a complete sentence, pause for 3 seconds, and try **Analisar agora**. A context summary appears near the top even if no spoken nudge is useful.
7. Review any suggested memory before saving it. New notes are included from the next session onward.

You need internet access and separately billed OpenAI API access. Model access depends on your API project.
The Android voice is soft speech; its tone depends on the installed voice. This version does not generate a true whispered vocal style.

## Important boundaries of this version

- **In-person microphone audio:** it does not implement telephone, WhatsApp, or Teams call-audio capture.
- **Speaker identity:** there is no reliable diarization, voice enrollment, or wearer identification. The model can confuse speakers.
- **Input/output pairing:** Android may pair the Bluetooth microphone with Bluetooth playback. The phone microphone is requested, not guaranteed. The live screen reports the actual input when available.
- **Brief capture gaps:** input is deliberately omitted during spoken advice and for 300 ms afterward, to avoid feedback. This is not uninterrupted full-duplex capture.
- **Privacy of playback:** the app selects a private route, starts playback muted, verifies the route, and stops on detected changes. Zero audio leakage cannot be guaranteed without hardware testing; route changes are asynchronous.
- **Background operation:** a microphone foreground service and wake lock are implemented. Screen-lock, battery, Bluetooth, and incoming-call behavior still need testing on the actual phone.
- **Memory:** approved notes persist locally; full conversations do not persist locally between sessions. Context is bounded by the provider's session/context limits.
- **Cloud processing:** live audio, your goal, and approved notes go to OpenAI. “No local raw recording” does not mean zero retention by the provider. Consult its data controls.
- **Personal credentials:** direct use of your own key is a personal prototype tradeoff. Before distributing the app, use an authenticated backend issuing short-lived client secrets, with usage enforcement. The app also accepts an `ek_` client secret, but does not refresh expired secrets or implement the broker.
- **Test signing:** the cloud workflow uses Android's generated debug signing key. A fresh build can have a different signature and require uninstalling the earlier app, which deletes the saved API key, settings and local memories. Retain needed notes and have your API key available before uninstalling. Establish private, stable release signing before relying on persistent updates.

## Architecture

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Session controls, permissions, local settings, reviewed memory, audio test |
| `BrainService` | Foreground lifecycle, microphone stream, timeout, failure cleanup |
| `RealtimeClient` | GA Realtime WebSocket protocol, server VAD, text responses |
| `AdvicePolicy` | Deterministic quiet periods, cooldown, manual request scheduling |
| `Advice` | Parsing and bounds checks before speech |
| `PrivateVoice` | Offline synthesis, private device selection, playback checks and cleanup |
| `Vault` | AES-GCM encrypted configuration and approved notes; Android Keystore key |
| `SessionState` | In-memory UI state; cleared when a new session starts |

This prototype uses WebSocket for a small Java implementation with controllable audio routing.
OpenAI recommends WebRTC for mobile clients. A production version should evaluate WebRTC with short-lived credentials,
robust echo cancellation, interruption handling, and server-side usage controls.

## Validation

The updated policy passes 29 local behavioral checks. The cloud workflow also runs the advice-parser tests,
local WebSocket protocol fixtures, Android compilation and lint. The protocol fixtures cover manual requests
without VAD events, final text without deltas, and a correlated empty-buffer commit race. They do not call OpenAI.
See `VALIDATION.json` for the actual tested commit, cloud outcome and downloadable artifact checksums.

The remaining validation requires a real Android phone: installation, live API authentication and advice,
earpiece/Bluetooth routing, screen lock, incoming calls, network loss, and session timeout.
A successful build does not verify these physical-device behaviors.

For a first device test, verify a complete cycle: audio test → permission grant → one contextual nudge → Stop.
Then verify screen lock, incoming call interruption, earbud disconnection, network loss, and session timeout.
Confirm no private advice is audible through the loudspeaker. Source inspection is not a hardware test.

## Build toolchain

Android 12+ (API 31 minimum); compile/target SDK 36; AGP 8.11.1; Gradle 8.13; Java 17.
The GitHub workflow installs the tools. A Gradle wrapper is not included; local developers can use Gradle 8.13.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

## Official references

- [Android microphone foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone): microphone access must be granted before starting the service from a visible app.
- [Android communication-device routing](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)): selecting and releasing communication audio routes.
- [Android text-to-speech](https://developer.android.com/reference/android/speech/tts/TextToSpeech): synthesis, voice selection, and audio attributes.
- [OpenAI Realtime conversations](https://developers.openai.com/api/docs/guides/realtime-conversations): VAD with manual response generation and current event schemas.
- [OpenAI Realtime client secrets](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create): production mobile authentication.
- [Realtime Mini model](https://developers.openai.com/api/docs/models/gpt-realtime-2.1-mini): available modalities and model details.
- [API pricing](https://developers.openai.com/api/docs/pricing) and [data controls](https://developers.openai.com/api/docs/guides/your-data): account costs and provider data handling.
- [AGP 8.11 compatibility](https://developer.android.com/build/releases/agp-8-11-0-release-notes): SDK, Java, and Gradle compatibility.
- [Running a GitHub workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow) and [downloading artifacts](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts): browser-based builds and downloads.

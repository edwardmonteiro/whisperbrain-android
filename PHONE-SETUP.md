# Build WhisperBrain using only your Android phone

**An installable test APK is available from the successful cloud build. Physical audio and live API testing remain pending.**

## Download and install the cloud build

1. Open [this repository’s Actions tab](https://github.com/edwardmonteiro/whisperbrain-android/actions) in Chrome.
2. Select the latest successful **Build WhisperBrain APK** run.
3. Under **Artifacts**, download **WhisperBrain-Android-APK**. GitHub may require you to sign in.
4. Use Android’s Files or My Files app to extract the ZIP.
5. Open `app-debug.apk` and follow Android’s installation prompts.
6. For another build, select **Build WhisperBrain APK → Run workflow**.

Source commits automatically start the same workflow. If GitHub hides controls on mobile, enable **Desktop site** in Chrome.
You do not need Android Studio, a laptop, Termux, or an AI API key to compile the app.
GitHub Actions availability and included minutes depend on your GitHub account.
An APK exists only after the cloud build succeeds. A successful build does not verify physical audio behavior.

## Connect the installed app

1. Open **Test private audio**. Listen through calling-capable earbuds or the phone's earpiece.
2. Install an offline Android voice if the app asks for one.
3. Open **Connection & voice settings** and enter your own OpenAI API key there.
4. Choose the advice language and keep the initial **10-minute** session limit.
5. Write one conversation goal, allow the microphone, then tap **Start listening**.
6. Say a complete sentence and pause. Tap **Nudge me** if you want an explicit suggestion.
7. Tap **Stop listening**, or use **Stop** in the ongoing notification.

Use test conversations with participants who agree. This version captures microphone audio around the phone;
it does not implement call recording. It sends live audio and approved notes to OpenAI.
API credits are separate from ChatGPT subscriptions.

## If something fails

| What you see | What to do |
| --- | --- |
| GitHub build fails | Share the failed run's URL. Do not paste keys or private conversation content. |
| Installation blocked | Follow the phone's installer guidance or device administrator policy. Managed phones may prohibit sideloading. |
| “App not installed” after an update | A fresh debug build may have a different signing key. Uninstalling clears memories; retain any needed notes first. |
| No offline voice | Search Android settings for **Text-to-speech output** and install the selected language's voice data. |
| No private audio route | Enable calling audio for your earbuds, reconnect them, or allow the phone earpiece in the app. |
| API access denied | Check your API key, project permissions, model access, and separate API billing. |
| Silence despite live input | Pause after a complete sentence, then tap **Nudge me**. The model can choose silence. |
| Stops when the screen locks | This needs device-specific testing. Inspect the app's battery settings and share the exact symptom. |

The app's voice uses soft local speech. Earbud routing, background reliability, live API behavior,
and useful advice quality remain to be validated on your phone.

Official instructions: [run a workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow),
[download a build artifact](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts),
[OpenAI API pricing](https://developers.openai.com/api/docs/pricing).

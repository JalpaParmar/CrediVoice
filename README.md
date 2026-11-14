# CrediVoice Android app

CrediVoice reads likely bank credit alerts aloud from notifications. It is a native Kotlin Android project and does not use a server.

## First version

- Uses Android Notification access to inspect incoming notifications from SMS and bank apps. The user must enable this in Android Settings; old messages are not read.
- Looks for common credit words and amounts, and ignores common debit words.
- Lets the user pause alert monitoring, set quiet hours, choose observed sender IDs, and adjust speech volume.
- Saves up to 100 short alert summaries locally (time, amount, sender, and whether the alert was spoken). It does not save the full SMS body.
- Provides a recent history screen and a clear-history action.

The app uses Android's special Notification access because `RECEIVE_SMS` is a hard-restricted permission that ordinary apps cannot necessarily hold just because the user taps a runtime prompt. Notification access can expose notification content from all apps to the listener, so the app filters locally and only stores summaries of likely credit alerts. This path works when the SMS or bank app posts a notification containing the alert text. Android's notification listener can access notification content after the user approves it in Settings.

## Build an APK

Open this `CrediVoice` folder in Android Studio with JDK 17 and Android SDK Platform 35 installed. Let Gradle sync, then use **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The debug APK will be created at:

`app/build/outputs/apk/debug/app-debug.apk`

This environment does not currently have Java, Gradle, Android SDK, or `adb`, so the APK cannot be built or installed from here yet.

## Notes

- This is an early prototype. Banks use different message formats, so the credit matching rules will need real examples to tune. Test carefully before relying on spoken alerts.
- It supports SMS and bank app notifications that include the alert text. It cannot read SMS when the messaging app suppresses notifications or hides message previews.
- Android and device makers can restrict notification access, background services, or speech engine availability. The user must enable Notification access for CrediVoice.
- The app currently uses the device's default text-to-speech voice and language.

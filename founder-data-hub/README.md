# LifeGraph Founder MVP

Private Android experiment for collecting owner-consented behavioral signals locally.

## V0 captures
- Foreground application transitions and session duration via Android Usage Access.
- Device context snapshots: battery, charging state, and network transport.
- Notification metadata: package name and Android notification category only.
- Manual intent annotations.
- Intent, outcome, and satisfaction labels.

## V0 does not capture
- Notification titles or message bodies.
- Typed text or passwords.
- Accessibility content.
- Microphone or camera.
- Contacts.
- Precise location.

All event records stay in the app's local SQLite database until the owner exports or deletes them.

The debug APK is produced by the `Founder Data Hub APK` GitHub Actions workflow.

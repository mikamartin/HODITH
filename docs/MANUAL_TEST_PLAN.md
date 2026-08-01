# HODITH — Manual Test Plan

Journeys that cross a system-process boundary instrumented tests can't drive (see TESTING.md's
strategy §3 and its manual-only seed list). Cadence: before every release; full pass before Play
submissions.

## Notifications & permissions

1. **Trigger fires a notification.** Create an `AT_LEAST` Trigger, then log enough events to reach
   its threshold (or create a `SILENT_FOR` Trigger and wait past its interval, or advance device
   time). A notification appears with voice-flavoured title/body; tapping it opens the app. (Tap
   opens the app generically, not yet the specific Case — that's `feature/notification-actions`.)
2. **Check-in fires a notification.** Enable check-ins on a Case with no recent events past its
   effective interval (Hunch-derived, or the Settings default). A notification appears; tapping it
   opens the app.
3. **POST_NOTIFICATIONS permission flow.**
   - First Trigger created, or first Case check-in enabled → the system permission dialog appears
     (once — creating a second Trigger or enabling check-ins on another Case doesn't ask again).
   - **Deny:** no notifications post; Home shows the "notifications are off" banner; tapping its
     action opens system notification settings; re-enabling there and returning to Home clears the
     banner without restarting the app.
   - **Grant:** no banner; notifications post as in items 1–2.

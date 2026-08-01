# HODITH — Manual Test Plan

Journeys that cross a system-process boundary instrumented tests can't drive (see TESTING.md's
strategy §3 and its manual-only seed list). Cadence: before every release; full pass before Play
submissions.

## Notifications & permissions

1. **Trigger fires a notification.** Create an `AT_LEAST` Trigger, then log enough events to reach
   its threshold (or create a `SILENT_FOR` Trigger and wait past its interval, or advance device
   time). A notification appears with voice-flavoured title/body; tapping it opens directly on that
   Case's detail screen (not just the app generically).
2. **Check-in fires a notification.** Enable check-ins on a Case with no recent events past its
   effective interval (Hunch-derived, or the Settings default). A notification appears with Log/All
   quiet actions; tapping the notification body (not an action) opens directly on that Case.
3. **Check-in "Log" action, `ONE_TAP` Case.** Tap Log on a due check-in for a Case whose `logFlow`
   is `ONE_TAP` — an event is logged directly (no sheet), the notification is dismissed, and the
   new event appears in-app.
4. **Check-in "Log" action, `DETAIL_SHEET` Case.** Same, but for a `DETAIL_SHEET` Case — the log
   sheet opens via the same trampoline the widget uses; saving dismisses the notification and the
   event appears in-app.
5. **Check-in "All quiet" action.** Tap All quiet on a due check-in — no event is created, the
   notification is dismissed, and the check-in doesn't re-fire until its full effective interval
   elapses again from this point.
6. **Ignored check-in re-fires.** Leave a due check-in notification untouched (tap neither action)
   through another periodic evaluation pass (~6h, or trigger the WorkManager job manually) — it
   fires again rather than staying silently resolved.
7. **Check-in summary collapsing.** Get 2+ Cases due for a check-in in the same evaluation pass
   (e.g. advance device time past several Cases' intervals at once) — one summary notification
   appears ("N cases are quiet — tap to review") instead of one per Case; tapping it opens Home.
8. **POST_NOTIFICATIONS permission flow.**
   - First Trigger created, or first Case check-in enabled → the system permission dialog appears
     (once — creating a second Trigger or enabling check-ins on another Case doesn't ask again).
   - **Deny:** no notifications post; Home shows the "notifications are off" banner; tapping its
     action opens system notification settings; re-enabling there and returning to Home clears the
     banner without restarting the app.
   - **Grant:** no banner; notifications post as in items 1–7.

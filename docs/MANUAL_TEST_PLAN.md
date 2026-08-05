# HODITH — Manual Test Plan

Journeys that cross a system-process boundary instrumented tests can't drive (see TESTING.md's
strategy §3 and its manual-only seed list). Cadence: before every release; full pass before Play
submissions.

## Widgets

Neither Glance widget host renders in an instrumented test (`WidgetRefreshWorkerTest` only proves
the periodic refresh call resolves and doesn't throw) — actual on-screen rendering, tap targets, and
the system configure flow can only be checked on a real home screen.

1. **List widget background and corners.** Add the List widget to a home screen — its surface
   renders the intended dark neutral color and rounded corners, not a plain black rectangle
   (previously-known bug; fixed via `appWidgetBackground()`/`cornerRadius()`). Check on at least one
   launcher in dark mode and one in light mode, since the launcher — not HODITH's theme — decides the
   surrounding corner mask.
2. **List widget empty state.** With no Case pinned, the widget shows its "no pinned Cases" message
   in the intended muted color (not stark black-and-white), and tapping the message opens the app.
3. **List widget title tap.** Tapping the "How often does it truly happen?" header opens the app to
   Home (no specific Case).
4. **List widget case row tap.** Tapping a case row's icon/name/count area (not the `+`/Stop button)
   opens the app directly on that Case's detail screen. The `+`/Stop button itself still only logs or
   stops — it doesn't also navigate.
5. **List widget one-tap log** on a `ONE_TAP` case via its `+` button — event appears in-app.
6. **List widget `DETAIL_SHEET` tap** on its `+` button — sheet opens via the trampoline, saves, and
   the event appears in-app.
7. **List widget ongoing/elapsed/Stop.** Start an event on a `START_STOP` Case — the widget row shows
   ticking elapsed time and a Stop button; tapping Stop ends the event without opening the app.
8. **Single-case widget configure flow.** Add the Single-case widget — a single-select Case picker
   always appears (unlike the List widget's picker, which skips once anything is already pinned).
   Cancel leaves no widget placed; picking a Case and confirming places a widget bound to it.
9. **Single-case widget log + open details.** For the bound Case: tapping the dedicated `+`/log
   button logs per its `logFlow` (directly for `ONE_TAP`, via the trampoline sheet for
   `DETAIL_SHEET`); tapping the icon/count area elsewhere on the widget opens that Case's detail
   screen instead.
10. **Single-case widget ongoing/elapsed/Stop** — same as the List widget's per-row treatment (item
    7), scoped to the one bound Case.
11. **Single-case widget with a missing Case.** Archive or delete the Case a Single-case widget is
    bound to — the widget switches to its "Case is gone" message, and tapping it opens the app
    (Home, no deep link, since the Case is gone).
12. **Add two widgets for the same Case** (e.g. a Single-case widget and pin the same Case to the
    List widget) — logging or stopping from either one refreshes both.
13. **Reboot device with an ongoing event** — both widgets still show the correct elapsed time
    afterward, not a reset or stale value.

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

## Share cards

The card assembly logic (top-beat selection, section filtering, display-name override) is unit-tested
(`ShareCardStateTest`) and the preview screen's own gating is instrumented-tested
(`SharePreviewScreenTest`, not yet run on-device — see TESTING.md's Known environment issues). These
steps are about the parts only a real device/FileProvider/share-sheet handoff can prove: the actual
bitmap capture, the system share sheet, and how the image looks once it lands somewhere else.

1. **Share sheet opens with a real image.** From a Case with a handful of logged events, tap the
   Share icon on Case Detail's header, then tap Share on the preview screen — the system share sheet
   opens, and picking a target (e.g. a messenger app, or "Save to Photos") produces the actual
   rendered card image, not a blank/corrupt file.
2. **Story vs. Square both render correctly end to end.** Toggle between Story and Square on the
   preview screen — both formats produce a correctly-shaped image through the full capture → share
   pipeline (not just in the in-app preview).
3. **All three themes render correctly through the real pipeline.** Switch the app's theme
   (Settings) between Plain/Intense/Bright, then share from the same Case each time — the captured
   image matches that theme's skin (Intense's stamp, Bright's banner/sticker), not a stale or
   default one.
4. **Edited display name shows up on the shared image.** Type a custom name in the preview screen's
   name field, then share — the exported image shows the typed name, not the Case's actual name.
5. **Section checklist choices are reflected in the shared image**, not just the in-app preview —
   toggle a couple of sections off/on and confirm the exported image matches what was checked.

## Data & backup

The round-trip logic itself (schema-version rejection, malformed-JSON rejection, all-or-nothing
rollback) is covered by `BackupSerializerTest`/`FakeHodithRepositoryTest`/
`RoomHodithRepositoryBackupTest`/`SettingsViewModelTest` — these steps are about the real system
file picker and content-provider boundary those tests can't drive.

1. **Export.** With real data logged, tap Settings → Export data. The system "save to" picker opens;
   choosing a location produces a valid `.json` file there, and a success snackbar appears.
2. **Import (happy path).** Tap Import data → confirm the replace-all-data warning → pick a
   previously exported file in the system picker. A success snackbar appears and every Case/event
   from that file is back, replacing whatever was there before.
3. **Import cancel.** Tap Import data, then cancel the confirm dialog — no file picker opens, no
   data changes.
4. **Import a non-HODITH file.** Pick an arbitrary file (a photo, a text file) via the import picker
   — a "not a valid backup" snackbar appears and existing data is untouched.
5. **Import across app installs.** Export from one install (or before a fresh reinstall/data wipe),
   then import that file on the clean install — full restore, including tags and Hunch history.

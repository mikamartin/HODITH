# HODITH — Manual Test Plan

Journeys that cross a system-process boundary instrumented tests can't drive (see TESTING.md's
strategy §3 and its manual-only seed list). Cadence: before every release; full pass before Play
submissions.

## Widgets

The List widget's `LazyColumn` renders as a `RemoteViewsService`-backed `ListView` that only
populates once attached to a real window — `AppWidgetHost.createView()` alone never triggers that
(confirmed by manual inspection; see `WidgetActionsFlowTest`'s doc comment), so anything *inside* a
List widget row still needs a real home screen. Chrome outside that row list (title, empty state),
the Single-case widget's own content (it isn't behind a `ListView`), the configure flows, and the
DETAIL_SHEET trampoline sheet are covered by instrumented tests instead — see each item below for
which test.

1. **List widget background and corners.** Add the List widget to a home screen — its surface
   renders the intended dark neutral color and rounded corners, not a plain black rectangle
   (previously-known bug; fixed via `appWidgetBackground()`/`cornerRadius()`). Check on at least one
   launcher in dark mode and one in light mode, since the launcher — not HODITH's theme — decides the
   surrounding corner mask.
2. **List widget empty state color.** With no Case picked for this widget instance, it shows its
   "no Cases selected" message in the intended muted color (not stark black-and-white). (Tapping the
   message to open the app is covered by `WidgetChromeNavigationTest.listWidget_emptyStateTap_opensMainActivity`.)
3. **List widget case row tap.** Tapping a case row's icon/name/count area (not the `+`/Stop button)
   opens the app directly on that Case's detail screen. The `+`/Stop button itself still only logs or
   stops — it doesn't also navigate. (Inside the row `ListView` — can't be driven from an instrumented
   test; see the note above.)
4. **List widget one-tap log** on a `ONE_TAP` case via its `+` button — event appears in-app. (Same
   `ListView` limitation as item 3; the underlying `QuickLogAction` callback itself is covered via the
   Single-case widget in `WidgetActionsFlowTest.quickLogTap_insertsAnEventForAOneTapCase`, which wires
   up the identical callback outside a `ListView`.)
5. **List widget `DETAIL_SHEET` tap** on its `+` button — sheet opens via the trampoline, saves, and
   the event appears in-app. Only the row tap itself needs a human (same `ListView` limitation as item
   3) — the trampoline sheet it opens is covered end-to-end by `WidgetLogTrampolineActivityTest`.
6. **List widget ongoing/elapsed/Stop.** Start an event on a `START_STOP` Case — the widget row shows
   ticking elapsed time and a Stop button; tapping Stop ends the event without opening the app. Only
   the ticking-elapsed *display* and the row tap need a human (same `ListView` limitation) — the Stop
   action itself is covered via the Single-case widget in `WidgetActionsFlowTest.stopTap_endsTheOngoingEventForAStartStopCase`.
7. **List widget configure flow, per-instance selection.** Add two List widgets to the home
   screen and pick a different set of Cases for each — each shows only its own picks, not the
   other's. Long-press a placed List widget and choose Edit to reopen its picker and change its
   selection. (Picking Cases via the real configure Activity and confirming is covered by
   `ListWidgetConfigureFlowTest` for a single instance; this item is about a *second* independent
   instance and the Edit re-entry path, neither of which has coverage yet.)
8. **Single-case widget configure flow: Cancel.** Add the Single-case widget and cancel its picker —
   no widget is placed. (Picking a Case and confirming is covered by
   `SingleCaseWidgetConfigureFlowTest.singleCaseWidget_showsBoundCase_afterRealConfigureFlow`.)
9. **Single-case widget: tap the icon/count area to open Case details.** Tapping elsewhere on the
   widget (not the dedicated `+`/log button) opens that Case's detail screen. (The `+`/log button
   itself — logging directly for `ONE_TAP`, via the trampoline sheet for `DETAIL_SHEET` — is covered
   by `WidgetActionsFlowTest.quickLogTap_insertsAnEventForAOneTapCase` for the `ONE_TAP` case; the
   `DETAIL_SHEET` case's button tap doesn't have widget-click coverage yet, though the trampoline sheet
   it opens does, via `WidgetLogTrampolineActivityTest`.)
10. **Add two widgets for the same Case** (e.g. a Single-case widget and the same Case selected on
    a List widget) — logging or stopping from either one refreshes both.
11. **Reboot device with an ongoing event** — both widgets still show the correct elapsed time
    afterward, not a reset or stale value.

## Notifications & permissions

The real `Notifier` posting a correctly-worded notification (title/body/actions per the active
`Voice`) is covered by `NotifierContentTest`, and the Log/All quiet action handling by
`NotificationActionReceiverTest` — both call the real Android APIs (`NotificationManager`, a real
broadcast) rather than going through `NotificationEvaluator`'s Trigger/check-in *selection* logic
against the shared on-device database, which stays flaky at the instrumented layer (see
`NotifierContentTest`'s doc comment) but is already covered against a fake repository per
`TESTING.md`. What's left below is specifically what those tests can't reach: a notification's tap
target (`PendingIntent` doesn't expose its wrapped `Intent` through any public API, so this can only
be checked by actually tapping) and the real permission dialog/banner round trip.

1. **Trigger fires a notification: tap target.** Create an `AT_LEAST` Trigger, then log enough
   events to reach its threshold (or create a `SILENT_FOR` Trigger and wait past its interval, or
   advance device time). Tapping the notification opens directly on that Case's detail screen (not
   just the app generically). (The notification's voice-flavoured title/body is covered by
   `NotifierContentTest.notifyTriggerFired_postsANotificationWithTheVoiceTitleAndBody`.)
2. **Check-in fires a notification: tap target.** Enable check-ins on a Case with no recent events
   past its effective interval (Hunch-derived, or the Settings default). Tapping the notification
   body (not an action) opens directly on that Case. (Title/body/Log/All quiet actions are covered
   by `NotifierContentTest.notifyCheckInDue_postsANotificationWithLogAndAllQuietActions`.)
3. **Ignored check-in re-fires.** Leave a due check-in notification untouched (tap neither action)
   through another periodic evaluation pass (~6h, or trigger the WorkManager job manually) — it
   fires again rather than staying silently resolved.
4. **Check-in summary collapsing: real due-count selection and tap target.** Get 2+ Cases due for a
   check-in in the same evaluation pass (e.g. advance device time past several Cases' intervals at
   once) — one summary notification appears instead of one per Case, and tapping it opens Home.
   (The summary notification's own title and that it's a single collapsed post are covered by
   `NotifierContentTest.notifyCheckInsSummary_postsOneCollapsedNotification`; this item is about
   `NotificationEvaluator` actually selecting "2+ due" from real data and the tap target, neither of
   which that test exercises.)
5. **POST_NOTIFICATIONS permission flow.**
   - First Trigger created, or first Case check-in enabled → the system permission dialog appears
     (once — creating a second Trigger or enabling check-ins on another Case doesn't ask again).
   - **Deny:** no notifications post; Home shows the "notifications are off" banner; tapping its
     action opens system notification settings; re-enabling there and returning to Home clears the
     banner without restarting the app.
   - **Grant:** no banner; notifications post as in items 1–4.

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

## About & Contact

The callbacks themselves (tapping the row/link invokes the right function) are instrumented-tested
(`AboutScreenTest`, `SettingsScreenTest`) — what's left is that the real `Intent` each callback
builds actually resolves to the right external app, which no instrumented test in this repo can
assert (no Espresso-Intents dependency; see TESTING.md).

1. **Privacy policy link.** On the About screen, tap "Read the full privacy policy" (wording varies
   by voice) — the device's browser opens directly to the hosted privacy policy page, not a blank tab
   or an error.
2. **Contact Us.** In Settings' Support section, tap Contact Us — an email app chooser (or the
   device's default mail app) opens with the developer address pre-filled as the recipient.

## Data & backup

The round-trip logic itself (schema-version rejection, malformed-JSON rejection, semantically
invalid backups — bad field values, dangling references, duplicate ids — all-or-nothing rollback)
is covered by `BackupSerializerTest`/`FakeHodithRepositoryTest`/`RoomHodithRepositoryBackupTest`/
`SettingsViewModelTest`/`BackupValidationResultTest`, and the real `ContentResolver` boundary
underneath the system picker (writing/reading bytes through a real `Uri`) is covered by
`ContentResolverBackupFileWriterTest` — these steps are about what's left: the real system "save
to"/"open" picker UI itself.

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

Android's own OS-level device backup (separate from the export/import above) can't be exercised by
an instrumented test — Android's real backup transport isn't available in a test harness. See
DEV_PLAYBOOK.md §6 for the exact `adb shell bmgr` commands behind each journey below.

6. **Cloud backup toggle on.** With the Settings toggle on (the default), log real data, then force
   a backup pass (`adb shell bmgr backupnow`). The command reports success, confirming HODITH's data
   was actually captured.
7. **Cloud backup toggle off.** Turn the toggle off, then force a backup pass the same way — the
   command should report that HODITH was skipped (no data captured), confirming
   `HodithBackupAgent`'s skip actually takes effect rather than only updating the preference.
8. **Reproduce the underlying bug.** Wipe local data, uninstall, reinstall on the same Google
   account — with the toggle left on beforehand, old data reappears unprompted from the restored
   backup. This is the concrete verification that the About screen's disclosure is accurate, not
   just plausible from the manifest.

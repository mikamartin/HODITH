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
3. **List widget case row tap.** Tapping a case row's icon/name/count area (not the `+` button)
   opens the app directly on that Case's detail screen. The `+` button itself still only logs — it
   doesn't also navigate. (Inside the row `ListView` — can't be driven from an instrumented test; see
   the note above.)
4. **List widget one-tap log** on a `ONE_TAP` case via its `+` button — event appears in-app. (Same
   `ListView` limitation as item 3; the underlying `QuickLogAction` callback itself is covered via the
   Single-case widget in `WidgetActionsFlowTest.quickLogTap_insertsAnEventForAOneTapCase`, which wires
   up the identical callback outside a `ListView`.)
5. **List widget `DETAIL_SHEET` tap** on its `+` button — sheet opens via the trampoline, saves, and
   the event appears in-app. Only the row tap itself needs a human (same `ListView` limitation as item
   3) — the trampoline sheet it opens is covered end-to-end by `WidgetLogTrampolineActivityTest`.
6. **List widget ongoing/elapsed.** Start an event on a `START_STOP` Case — the widget row shows the
   "Ongoing" pill + ticking elapsed time, and keeps its `+` button (tapping it starts a second
   concurrent event, it never becomes a Stop). Run a second event at once (retro-log a still-open one
   from Case Detail, or use the seeded "Noisy neighbours" demo Case) — the row shows the pill + a
   count. Stop is only in Case Detail, reached by tapping the row. Only the ticking-elapsed *display*,
   the pill/row rendering, and the row tap need a human (same `ListView` limitation) — the log button
   starting a second event and the row deep-link are covered on the Single-case widget by
   `WidgetActionsFlowTest.logTap_startsASecondEventForARunningStartStopCase` and
   `WidgetActionsFlowTest.runningCase_showsOngoingPill_andTheCaseAreaOpensCaseDetail`.
7. **List widget today/this-week count for a duration event (spec §9/§14 active span).** On a
   duration-tracking Case (`MANUAL` or `START_STOP`), log an event that started before today and
   ended earlier today, and start one that's still running. The row's count reads "Today: 2" — a
   duration event counts on every day its span was active, not only its start day, so a run stopped
   and logged today shows the same day. Only the List widget row needs a human (same `ListView`
   limitation); the Single-case widget renders the identical count and is covered by
   `WidgetActionsFlowTest.singleCaseWidget_todayCount_creditsADurationEventStillActiveToday`, and the
   count math itself by `HomeViewModelMappingTest`.
8. **List widget configure flow, per-instance selection.** Add two List widgets to the home
   screen and pick a different set of Cases for each — each shows only its own picks, not the
   other's. Long-press a placed List widget and choose Edit to reopen its picker and change its
   selection. (Two Single-case instances with their own Cases are automated by
   `SingleCaseWidgetConfigureFlowTest.singleCaseWidget_twoInstances_eachShowsItsOwnCase`. A List
   widget's rows can't be read from a test, so the List checks stay here.)
9. **Single-case widget: tap the icon/count area to open Case details.** Tapping elsewhere on the
    widget (not the dedicated `+` log button) opens that Case's detail screen. (The `+`/log button
    itself — logging directly for `ONE_TAP`, via the trampoline sheet for `DETAIL_SHEET` — is covered
    by `WidgetActionsFlowTest.quickLogTap_insertsAnEventForAOneTapCase` for the `ONE_TAP` case; the
    `DETAIL_SHEET` case's button tap doesn't have widget-click coverage yet, though the trampoline sheet
    it opens does, via `WidgetLogTrampolineActivityTest`.)
10. **Add two widgets for the same Case, one of them a List widget** — logging from the List
    widget refreshes the Single-case widget and the reverse. (Two Single-case widgets are automated by
    `WidgetActionsFlowTest.logFromOneWidget_refreshesASecondWidgetForTheSameCase`.)
11. **Reboot device with an ongoing event** — both widgets still show the correct elapsed time
    afterward, not a reset or stale value.

## Notifications & permissions

The real `Notifier` posting a correctly-worded notification (title/body/actions per the active
`Voice`) is covered by `NotifierContentTest`, and the Log/All quiet action handling by
`NotificationActionReceiverTest` — both call the real Android APIs (`NotificationManager`, a real
broadcast) rather than going through `NotificationEvaluator`'s Watch/check-in *selection* logic
against the shared on-device database, which stays flaky at the instrumented layer (see
`NotifierContentTest`'s doc comment) but is already covered against a fake repository per
`TESTING.md`. What's left below is specifically what those tests can't reach: a notification's tap
target (`PendingIntent` doesn't expose its wrapped `Intent` through any public API, so this can only
be checked by actually tapping), a one-time check that the OS honours the grouping/alert flags the
instrumented tests only assert are set, and the real permission dialog/banner round trip.

1. **Watch fires a notification: tap target.** Create an `OFTEN` Watch, then log enough
   events to reach its threshold (or create a `QUIET` Watch and wait past its interval, or
   advance device time). Tapping the notification opens directly on that Case's detail screen (not
   just the app generically). (The notification's voice-flavoured title/body is covered by
   `NotifierContentTest.notifyNotificationFired_postsANotificationWithTheVoiceTitleAndBody`.)
2. **Check-in fires a notification: tap target.** Enable check-ins on a Case with no recent events
   past the Settings-default interval. Tapping the notification
   body (not an action) opens directly on that Case. (Title/body/Log/All quiet actions are covered
   by `NotifierContentTest.notifyCheckInDue_postsANotificationWithLogAndAllQuietActions`.)
3. **Check-in grouping: summary tap target and OS-flag sanity.** Get 2+ Cases due for a check-in in
   the same evaluation pass (advance device time past several Cases' intervals at once). The shade
   bundles them into one HODITH stack under a group summary ("N cases need a look…"); tapping the
   summary opens the app on Home. One-time sanity that the OS honours flags the instrumented tests
   only assert are *set*: the batch makes one sound, not one per Case (`GROUP_ALERT_SUMMARY`), and
   leaving a check-in unanswered through the next ~6h pass re-posts it with an updated day count but
   no fresh sound or heads-up (`setOnlyAlertOnce`). The group structure, per-case Log / All quiet,
   the sibling surviving an All quiet, silent-repeat, and withdrawal of a no-longer-due check-in are
   covered by `NotifierContentTest` / `NotificationActionReceiverTest` / `NotificationEvaluatorTest`.
4. **POST_NOTIFICATIONS permission flow.**
   - First Watch created, or first Case check-in enabled → the system permission dialog appears
     (once — creating a second Watch or enabling check-ins on another Case doesn't ask again).
   - **Deny:** no notifications post; Home shows the "notifications are off" banner; tapping its
     action opens system notification settings; re-enabling there and returning to Home clears the
     banner without restarting the app.
   - **Grant:** no banner; notifications post as in items 1–3.

## Log tab filters

The chip row's own wiring (Sort/From/To chip rendering, each opening its picker directly, the Edit
icon's field toggles and their gating, the empty-range message, persistence through
`SettingsRepository`) is covered by `CaseDetailScreenTest`/`CaseDetailViewModelTest`. Day taps on the
shared range picker both the Log tab and Log Share use are covered by
`LogShareTabTest.rangeDialog_tappingAStartAndEndDay_handsBothDaysBack`.

1. **Log tab's "To" picker refuses a future date.** Open the Log tab's "To" chip and try to pick a day
   after today — that day is not selectable, the same way the History tab's own "To" picker refuses it.

## Share cards

The card assembly logic (top-beat selection, section filtering, the display name, and the History
filter/cap) is unit-tested (`ShareCardStateTest`) and the Share screen's tabs and their gating are
instrumented-tested (`ShareScreenTest`, `InsightShareTabTest`, `LogShareTabTest`). These steps are about
the parts only a real device/FileProvider/share-sheet handoff can prove: the actual bitmap capture,
the system share sheet, and how the image looks once it lands somewhere else.

1. **Each tab's share button reaches the real share sheet with a real image.** From a Case with a
   handful of logged events, tap the Share icon on Case Detail's header — the Share screen opens on
   Summary. Tap Share, and pick a target (e.g. a messenger app, or "Save to Photos") — it produces the
   actual rendered card image, not a blank/corrupt file. Repeat on Insights and on History.
2. **The exported image matches the preview, in each theme.** Switch the app's theme (Settings)
   between Plain/Intense/Bright and share from Summary and History in each — the exported image looks
   like the on-screen preview, skin included (Intense's stamp, Bright's banner/sticker). That each
   skin renders differently in a captured bitmap is automated by
   `ShareCardTemplateTest.storyCapture_isNotBlank_andChangesWithTheSkin`.

What these two steps don't repeat: that the typed name, the section choices, the History filters and
the entry cap reach the card. Those are checked by `ShareScreenTest` (the typed name across tabs),
`ShareCardStateTest` (what each option puts on the card), and `ShareCardTemplateTest` (what the card
draws). Capture renders the same composable those tests draw, so steps 1 and 2 only need to prove the
capture and the handoff.

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
3. **Import a non-HODITH file.** Pick an arbitrary file (a photo, a text file) via the import picker
   — a "not a valid backup" snackbar appears and existing data is untouched. (The parse result is
   unit-tested; the snackbar text is not asserted by any instrumented test yet.)
4. **Import across app installs.** Export from one install (or before a fresh reinstall/data wipe),
   then import that file on the clean install — full restore, including tags and watches.

Android's own OS-level device backup (separate from the export/import above) can't be exercised by
an instrumented test — Android's real backup transport isn't available in a test harness. See
DEV_PLAYBOOK.md §6 for the exact `adb shell bmgr` commands behind each journey below.

5. **Cloud backup toggle on.** With the Settings toggle on (the default), log real data, then force
   a backup pass (`adb shell bmgr backupnow`). The command reports success, confirming HODITH's data
   was actually captured.
6. **Cloud backup toggle off.** Turn the toggle off, then force a backup pass the same way — the
   command should report that HODITH was skipped (no data captured), confirming
   `HodithBackupAgent`'s skip actually takes effect rather than only updating the preference.
7. **Reproduce the underlying bug.** Wipe local data, uninstall, reinstall on the same Google
   account — with the toggle left on beforehand, old data reappears unprompted from the restored
   backup. This is the concrete verification that the About screen's disclosure is accurate, not
   just plausible from the manifest.

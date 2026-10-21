# Bipe fork notes

This repo is Bipe's Android app: a fork of Element X Android (AGPL-3.0). Upstream conventions in `AGENTS.md` still apply. This file adds the fork rules. Plan: `../bipe/docs/android-plan.md`.

## Rules

- **All Bipe code lives in `libraries/bipe`** (the mirror of BipeKit on iOS). Element X sources get small hooks only, marked `// Bipe`. Keep the upstream diff small so rebases stay cheap.
- **Never hand-edit `libraries/bipe/src/main/kotlin/.../generated/` or `res/values*/bipe_strings.xml`.** They come from `../bipe-shared` (`node tools/gen.mjs`). Change registries and strings there.
- Bipe UI strings are `R.string.bipe_*` from that module, not `temporary.xml` and not Localazy.
- Platform logic (badge verification, normalisation, scam detection) must pass `../bipe-shared/test-vectors/`, like iOS.
- New Bipe files carry the Bipe AGPL header (`Copyright 2026 Bipe.`, `SPDX-License-Identifier: AGPL-3.0-only`). `KonsistLicenseTest` accepts it.
- The `enterprise/` submodule (private Element repo) is not used. FOSS builds use `features/enterprise/impl-foss`.

## Upstream

- `upstream` = element-hq/element-x-android (push disabled). `origin` = bipe-chat/bipe-android. Local `main` tracks `origin/main`.
- Rebase: `git fetch upstream && git rebase upstream/develop`.

## Build (green gate)

```bash
./gradlew assembleGplayDebug
```

**Tester builds run in GitHub Actions** (`.github/workflows/bipe-apk.yml`, bipe#43). A change of the shared build number (`node tools/bump-build.mjs` in bipe-shared, which writes `BipeVersion.kt`) or `gh workflow run bipe-apk.yml -R bipe-chat/bipe-android` starts it. It checks out bipe-shared next to this repo, runs the shared vectors, builds `assembleGplayRelease` signed with the Bipe release key, and keeps the APK as a private workflow artifact for 7 days. Nothing is published on GitHub. The Magalu server pulls the newest artifact every 15 minutes and serves it at `https://bipe.chat/baixar/android/bipe.apk` (bipe-infra `k8s/bipe/apk-pull.yaml`).

Release key: PKCS12, alias `bipe`, certificate SHA-256 `42:0A:A2:32:1A:35:1D:D5:61:0A:19:71:5E:A6:3A:F8:A8:4C:0F:5E:74:06:07:5B:4A:03:F8:13:2B:FB:C6:CC`. GitHub secrets `BIPE_ANDROID_KEYSTORE_B64`, `BIPE_ANDROID_STOREPASSWORD`, `BIPE_ANDROID_KEYALIAS`; backup in MZ's Keychain (`bipe-android-release-keystore-b64`, `bipe-android-release-keystore-password`). Losing the key means installed apps cannot update. Without `BIPE_ANDROID_STOREFILE`, release builds keep the debug key.

The manual builds on **mini4p** (Tailscale `mini-4p`, JDK 21 in `~/opt/jdk-21`, tar copy of the tree) are retired for tester builds. They copied files but never deleted them, and their bipe-shared copy went stale without notice (13 October 2026).

### Debug build for a phone on MZ's Mac (mini4p)

MZ's Mac has no JDK. mini4p (`100.114.238.75`) builds, and the phone stays on the Mac's USB. Steps, run from `~/Dev/Bipe` on the Mac:

1. Copy a clean tree of both repos into a new folder on mini4p, named after the commit. A new folder means no stale files. Give tar a file list (`-T`). `git ls-files | xargs tar` splits into several tar streams, and the receiving tar stops after the first one: on 15 October 2026 it copied 5,000 of 11,990 files with no error. Compare the file count on mini4p with `git ls-files | wc -l`.
2. Write `local.properties` in the new tree: `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties`. On 15 October 2026 mini4p had no `~/Dev/Bipe/bipe-android` checkout to copy it from.
3. Make a snapshot commit in the copy (`git init -b main && git add -A && git commit -m "snapshot <sha>"`). The build reads the git revision (`GitRevisionValueSource`). Without `.git`, configuration fails with `Process 'command 'git'' finished with non-zero exit value 128`.
4. Build with `JAVA_HOME=~/opt/jdk-21/Contents/Home`: `./gradlew assembleGplayDebug --no-daemon --max-workers=4`.
5. Copy `app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk` (about 185 MB) to the Mac with `scp`, compare `shasum -a 256` on both sides, then `adb install -r`. The app installs as `chat.bipe.app.debug`.

```bash
D=Dev/Bipe/build-$(git -C bipe-android rev-parse --short HEAD); ssh 100.114.238.75 "rm -rf ~/$D && mkdir -p ~/$D/bipe-android ~/$D/bipe-shared"; for r in bipe-android bipe-shared; do (cd $r && git ls-files | while read f; do [ -f "$f" ] && echo "$f"; done > /tmp/$r.list && tar -cf - -T /tmp/$r.list) | ssh 100.114.238.75 "tar -xf - -C ~/$D/$r"; done
```

Shared test vectors (ADR 0031 rule 4): `libraries/bipe/src/test/.../SharedVectorTest.kt` reads `../bipe-shared/test-vectors` directly. On mini4p that is `~/Dev/Bipe/bipe-shared`, a copy that does not update by itself. Copy the current bipe-shared over before each test run (`git ls-files | tar`, same as the app tree). A stale copy fails or passes against old vectors without telling you: on 13 October 2026 it still had the vectors from before the rename and no `pins.json`. Gradle does not track the vector files as test inputs, so run `:libraries:bipe:cleanTestDebugUnitTest` first, or it reuses the old result. Use `--no-daemon --max-workers=4` (the daemon hangs at packaging). First green run: 13 October 2026, 7 of 7.

```bash
./gradlew :libraries:bipe:testDebugUnitTest
```

## Configuration

| What | Where | Value |
|---|---|---|
| Version name, build number | `plugins/src/main/kotlin/BipeVersion.kt` (generated), read by `Versions.kt` | From `bipe-shared/product/version.json` (ADR 0088). `versionCode` = build × 10 + ABI code. iOS uses the same build number. Before each tester upload: `node tools/bump-build.mjs` in bipe-shared, then commit and push bipe-shared, bipe-ios and this repo. Element's `tools/release/release.sh` is not used. |
| Application ID, name | `plugins/src/main/kotlin/config/BuildTimeConfig.kt` | `chat.bipe.app` (debug adds `.debug`, nightly `.nightly`), "Bipe" (ADR 0054) |
| OAuth redirect scheme | `BuildTimeConfig.METADATA_HOST_REVERSED` | `chat.bipe.app` (debug `chat.bipe.app.debug`) |
| OAuth client, logo, terms, privacy URLs | `BuildTimeConfig.URL_*` | `https://bipe.chat/...` (MAS requires them on the client URI host) |
| Server | `appconfig/.../BipeConfig.kt`, used by `DefaultEnterpriseService` (impl-foss) | `bipe.net.br`, base `https://matrix.bipe.net.br`. Allow list of one, so the server picker is hidden. |
| Bipe API | `BipeConfig.API_BASE_URL` | `https://api.bipe.net.br` |
| Firebase push gateway | `FirebaseConfig.PUSHER_HTTP_URL` | `https://push.bipe.net.br/_matrix/push/v1/notify` (own Sygnal, never matrix.org) |
| Pusher app IDs | `BuildTimeConfig.PUSHER_APP_ID_*` | `chat.bipe.app.android.prod` / `.android.dev`. Sygnal must list them. |
| Telemetry | `plugins/.../DependencyHandleScope.kt` | No Sentry or PostHog (bipe#55): their provider modules and libraries are deleted, every build uses `services:analytics:noop`, and Element Call gets no PostHog or Sentry values. Problem reports go to the Bipe API (bipe#367): `BUG_REPORT_URL` and `BUG_REPORT_APP_NAME` in `BuildTimeConfig.kt`, the access token in `DefaultBugReporter`, no report URL before sign-in (`DefaultBugReporterUrlProvider`), logs off by default (`BugReportState`). The entry is "Avisar sobre um problema" in Ajuda e sobre. |
| Trimmed libraries (15 Oct 2026) | `features/rageshake/impl`, `features/preferences/impl` | `DefaultRageShake` is a no-op (no shake to report), so seismic is gone. The developer settings lose the brand colour picker (enterprise builds only), so `io.mhssn:colorpicker` is gone. |
| Locales | `plugins/.../extension/locales.kt`, `app/src/main/res/xml/locales_config.xml` | pt-BR and en only. Edited by hand: do not re-run the Localazy import. |
| Accent colour | Compound tokens | Açaí from `../bipe-tokens`: `./tools/compound/import_bipe_tokens.sh`. Notification accent `#80307B` in `NotificationConfig`. |
| App links | `app/src/main/AndroidManifest.xml` | `bipe.chat` (and www) paths `/@`, `/convite/` and `/recuperar/` (recovery link, token in the fragment). Old `proseio.com.br` links still open the app (second filter, no autoVerify). Element hosts removed. |
| Location maps | `plugins/src/main/kotlin/config/BuildTimeConfig.kt` | `https://maps.bipe.net.br/styles`, styles `light` and `dark`, no key (bipe ADR 0131; tileserver-gl in bipe-infra). `MAPTILER_KEY_REQUIRED` is true only for `api.maptiler.com`. Never set a MapTiler key or URL: every tile and static map request carries the shared location. |

## Device-first accounts (ADR 0057, ADR 0059, bipe#154)

- **First run (bipe#415):** the start screen variant, the sign-up steps, the "Já tenho conta" rows and the later prompts come from bipe-shared `registries/onboarding.json` (generated `BipeOnboarding.kt`), the same source as iOS. `LoginFlowNode` starts on `OnBoarding` for Bipe: no Element Classic check, no spinner. `OnBoardingPresenter` picks `BipeOnboardingStart` from the invite link and `GET /v1/signup/config` (`BipeStartScreenInfo`). An invite link shows "@ana te chamou para o Bipe." instead of opening sign-up. Notifications: no Element FTUE step; `MessageComposerPresenter` asks once after the first sent message (Android 13+). The @: `BipeHandlePromptRow` on Conversas while `GET /v1/me/handle` has none (`BipeHandlePrompt`).
- **Sign-up** (`features/login/.../bipesignup`): invite code (skipped with a link; "Não tem convite?" opens the waitlist sheet, `POST /v1/waitlist`), name and terms, the 8-digit PIN typed twice, then the fallback prompt "Se você perder o celular" with "Adicionar e-mail" or "Agora não". No @ and no e-mail on the first form. Toast "Conta criada. Fica neste celular." Guardians and the recovery key are not offered yet. The request has no password: `POST /v1/signup` returns the hidden password and the recovery token once. No attestation (Play Integrity, #92), as before.
- **PIN rule:** `BipePin.problem()` (libraries/bipe `account/`), from `registries/accounts.json`. `SharedVectorTest.pins` runs `test-vectors/pins.json`.
- **Storage:** `BipeAccountVault` keeps the hidden passwords in one file in `noBackupFilesDir`, encrypted with an Android Keystore key (`bipe.SECRET_KEY_ALIAS_ACCOUNT`). Passwords and local tokens are a map by Matrix ID (`accounts`, ADR 0086, bipe#265): a second account never replaces the first. The first read of a vault from before #265 moves its one account into the map. Block Store has one entry per account (key `chat.bipe.recovery_token.<hash of the Matrix ID>`); the old entry `chat.bipe.recovery_token` moves to its account's key once the vault knows the account, else it stays and still works for a token recovery. The pending sign-up and the pending email recovery stay one per device: they have no Matrix ID until they end. Nothing backs it up (ADR 0059: the password stays on the device). The recovery token goes to `BipeRecoveryTokenStore`: Block Store with `shouldBackupToCloud = true` in the gplay build (`libraries/bipeblockstore`, `play-services-auth-blockstore` 16.4.0, `gplayImplementation` only). The F-Droid build has no Block Store: the token stays in the vault and does not sync, so a new phone cannot use the token path there. Google backs Block Store up end-to-end encrypted only when the phone has a screen lock.
- **Sign-in without a browser:** `BipeDeviceAccount.signIn` calls `MatrixAuthenticationService.setHomeserver` and `login(userId, password)`. The Rust SDK sends `m.login.password` (`m.id.user`). MAS answers it through its compatibility layer (`passwords.enabled: true`) and creates a compat session. The SDK uses OAuth only when the app asks for an OAuth URL, so this path never opens a browser.
- **App start without a session** (`LoginFlowNode.bipeStartDeviceAccount`): with the hidden password in the vault (e.g. sign-up made the account but the sign-in failed), sign in with no screen. With a waiting e-mail recovery or a recovery token in Block Store, open "Voltar pra sua conta": the token path asks for the PIN first (ADR 0059: a synced item never signs in alone). A sign-out deletes the hidden password (`SessionListener`), so after a sign-out, including the lock screen's sign-out after wrong PINs, the way back is the token and the PIN, which the server counts.
- **App lock:** off by default (mz, 2026-10-20: Bipe is a normal app, not a secure messenger). "Bloquear o app" in the Senha section of Sua conta turns it on (`BipeAppLockRow`, libraries/bipe `account/ui`). The switch shows whether a lock PIN is set. On: with one account, the owner check of "Trocar senha" (`BipeBiometricCheck`; nothing to check with, refused), then Element's lock screen setup (`LockScreenEntryPoint.Target.Setup`: new PIN, confirm, biometric offer). Back from the setup sets no PIN, so the switch stays off. Off: biometrics or the phone's screen lock, else the app lock PIN, then `BipeAppLock.removePin` deletes the PIN and the biometric unlock. "Desbloquear com biometria" shows only while the lock is on. Nothing forces the setup at first run (`LockScreenConfig.IS_PIN_MANDATORY` is `false`). Users who had a lock PIN keep it. The lock is Element's lock screen with an 8-digit PIN (`LockScreenConfig.PIN_SIZE`) and the Bipe rule in `PinValidator`. The lock PIN is the device PIN (ADR 0086, bipe#266). While the lock is on and the phone holds one account (`BipeDeviceAccount.devicePinFollowsAccountPin`), it follows the account PIN, so the user has one PIN: Bipe updates the lock PIN after sign-in, token recovery, the beta move and "Trocar senha" (`BipeDeviceAccount.adoptAccountPin`), and `SetupPinPresenter` sends a PIN set in the lock screen setup to `PUT /v1/me/pin`. Sign-up, sign-in, recovery and a PIN change never turn the lock on. With two or more accounts, a set device PIN stays, no lock-screen PIN reaches the server, and the PIN footer in Sua conta says so (`bipe_account_pin_footer_device`).
- **Settings (Você):** `BipeAccountRows` under the invite row: "Trocar senha" after a biometric check (`BipeBiometricCheck`: biometrics or the phone's screen lock; without either, the current app lock PIN; without that, refused), the blocked state from `GET /v1/me/account` (a new PIN unblocks), and for beta accounts "Crie sua senha", which sets the PIN, calls `POST /v1/me/device-first` and stores the new credentials. Then `BipeRecoveryEmailRows`, the same copy and flow as the iOS email section: the confirmed and the pending address from `GET /v1/me/account`, a dialog to add or change it (`PUT /v1/me/email` with `lang`; HTTP 202, the address stays pending until the link is opened) and "Remover e-mail" after a confirmation (`DELETE /v1/me/email`).
- **Recovery** (`features/login/.../biperecovery`): token and PIN (`/v1/recovery/token`); QR code from the old phone (Element's QR sign-in); e-mail: `/v1/recovery/email`, then the app link `https://bipe.chat/recuperar/#<token>` asks for the PIN or ("Esqueci minha senha") a new PIN, `POST /v1/recovery/email/confirm`, keeps the claim secret in the vault and polls `/v1/recovery/{id}/claim` every minute. Guardian and CPF paths are not built (API not built).
- **Old devices:** `BipeRecoveryAlerts` (in `LoggedInFlowNode`) polls `GET /v1/me/account` every minute in the foreground and shows waiting recoveries (`bipe_recovery_alert_*`) with "Cancelar, não fui eu" (`POST /v1/me/recoveries/{id}/cancel`), and token sign-ins on another device. It ignores token recoveries older than this device's own sign-in, which covers this device's own token recovery. "Fui eu" hides the alert on this device only.
- **Terms re-accept (ADR 0113, bipe#366):** `BipeTermsReaccept` (libraries/bipe `terms/`, in `LoggedInFlowNode`) shows a blocking screen when the `GET /v1/me/account` status that `BipeRecoveryAlerts` polls loaded and its `terms_version` is not `BipeRegistries.LEGAL_VERSION` (none counts as different). A failed load never shows it. It waits while a recovery alert is open. The same `BipeTermsRow` as sign-up, Continue calls `POST /v1/me/terms`. No close, no sign-out, Back does nothing. The app lock is its own activity, so the screen shows after unlock.
- **Beta login:** "Conta do beta" on the recovery screen sets the server and opens Element's password screen (no browser). For Bipe it shows no server domain and no Matrix line, and labels the fields "E-mail" and "Senha da conta do beta", so the PIN and the account password never share a label. MAS takes the e-mail as the login (`login_with_email_allowed`).

## Link a computer (ADR 0116, ADR 0118, bipe#391)

- **Entry point:** Settings > Aparelhos > Aparelhos conectados (`BipeLinkedDevicesNode` in libraries/bipe) is the root of `LinkNewDeviceFlowNode`. It lists the other devices (`GET /_matrix/client/v3/devices`) with last use and "Desconectar", and has the only "Conectar um computador" button. Only the desktop direction: the phone scans the code that `chat.bipe.net.br` shows. `LinkNewDeviceRootNode` (both directions) is not used. `FeatureFlags.QrCodeLogin` is finished and on (bipe#431), so the button always shows and the developer settings have no toggle for it.
- **Support check:** `BipeWebLinkSession.isSupported` replaces `MatrixClient.canLinkNewDevice` (the FFI is true only for an OAuth session): `/versions` advertises `org.matrix.msc4108` and `BipeAccountVault` holds this account's hidden password.
- **Approval:** at `LinkDesktopStep.WaitingForAuth` the flow pushes `BipeWebApprovalNode` in place of the Chrome Custom Tab: full-access warning with the cooldown (`BipeRegistries.webLinkCooldownSeconds`), `BipeBiometricCheck`, then the hidden password is read. In-app `WebView` with its own WebView profile (`bipe-web-link`; on a WebView without multi-profile, the global cookies and storage are cleared before and after), MAS issuer host only (from `auth_metadata`). The MAS login form is filled once and never shown; a second login form is an error. The consent page shows the place from `GET /v1/web-link/place` (DB-IP attribution). `confirm()` when the page leaves the consent page or the user closes it; `cancel()` before the consent page. `POST /v1/web-link/finish` when the flow ends. The resume-time `confirm()` of upstream is gone for the desktop direction.
- **Sign-out of a linked device:** the MAS account page with `action=org.matrix.session_end&device_id=…` in the same private web view (Synapse with MAS refuses `DELETE /devices`).
- **Trap:** the Synapse rendezvous session lives 60 s from the QR code. Scan, warning, cooldown, biometric check and the MAS page must fit in it.
- Unverified (19 Oct 2026): the whole flow on a phone, the MAS consent page markup (IP in the page text, a `form` on `/device/<id>`), the MAS `session_end` page.

## Several accounts (ADR 0086, bipe#267)

- Multi-account is always on, with no feature flag. Upstream's multi-account code does the sessions, the add-account flow (`RootFlowNode` pushes `NotLoggedInFlow`, whose login flow runs with `allowBackNavigation`, so no silent sign-in) and the switch (`HomeEvent.SwitchToAccount`).
- **Chat list avatar:** a tap with two or more accounts switches to the next one (one account: settings, as before); a long press opens the account menu (`BipeAccountMenuAnchor` in `HomeTopBar`): every account with a `RadioButton` (the current one selected; a tap on another row switches), a divider, then "Nova conta" (`bipe_account_add`), which opens the "Nova conta" window.
- **Você tab menu (ADR 0135):** a long press on Você opens `BipeAccountMenuAnchor` (`HomeBottomBar`): every account with its avatar, the current one checked. "Nova conta" shows only while the phone has one account and the cap allows one more; with two or more it is the Sua conta row `ACCOUNT_ADD`. Same as iOS.
- **"Nova conta" window (bipe#445, bipe#446):** `BipeNewAccountView` and `BipeNewAccountPresenter` (libraries/bipe `account/newaccount/`), a bottom sheet. The avatar menu (`HomeFlowNode`) and the Sua conta row `ACCOUNT_ADD` in the section "Contas neste celular" (`BipeSettingsGroupNode`) open it. Rows: "Conta pessoal", off at the cap with `bipe_account_cap_reached`; "Conta da empresa", hidden until Play Integrity (#92, `BipeNewAccountState.showsCompanyAccount`); "Entrar numa conta que já existe", the add-account flow as before (`navigateToAddAccount`, sign-in and QR code).
- **Invite for a new account (bipe#445):** when the window opens under the cap, it reads the quota only (`BipeInviteCreator.myInvites`, `GET /v1/me/invites`). Invites left: the window shows `bipe_account_new_invite_note` under the sign-up rows. None left: the sign-up rows are off and show the quota text (`bipeInvitesNoneLeftText`). A tap on "Conta pessoal" creates one invite (`BipeInviteCreator.createIfAvailable`, libraries/bipe `invites/`, also used by "Convidar pessoas") with a progress indicator on the row, so a dismissed window never uses up an invite. Then the window closes and calls `bipeNavigateToSignup(code)`: `RootFlowNode` pushes `NotLoggedInFlow` with `LoginParams.bipeStartsSignup`, and `LoginFlowNode` opens `BipeSignup` with the code, so the sign-up starts at the name step (the invite link path). A failed request opens the sign-up without a code, so it shows its invite step. A first sign-up on an empty phone keeps the invite step.
- **Cap:** `BipeAccountCap` (libraries/bipe): the last `max_accounts` from `GET /v1/me/account`, else `BipeRegistries.MAX_PERSONAL_ACCOUNTS` (2). Company accounts do not count (#269).
- An account with a session is never signed in again: `BipeDeviceAccount` leaves signed-in accounts out of the silent sign-in and the token recovery.
- Element's upstream multi-account extras (feature flag, announcement, vertical swipe on the avatar) are removed (bipe#434). The avatar tap and long press match iOS.
- Not done: the account avatar in the composer, the account name in notifications (unverified), the rounded-square avatar for a verified company (ADR 0120, needs #252).

## Handle change lines (ADR 0066, bipe#166)

- **Remove the handle:** "Remover nome de usuário" on the "Nome de usuário" screen (`BipeHandleView`), also during the cooldown. A confirmation dialog (`bipe_handle_remove_body`), then `DELETE /v1/me/handle`. Same flow on iOS.

- `BipeHandleChanges` (in `BipeSession`) calls `POST /v1/handle-changes` in chunks of 100, with `since` = the oldest of the chunk's latest cached `at` (none when a user has no cached change; duplicates are dropped). It runs after each `resolve` refresh of `BipeHandleResolver` and on room open (`TimelinePresenter`), at most once a minute per user. The cache is in memory: an app restart fetches the history again.
- `BipeHandleChangeLines` (features/messages) inserts a virtual item (`TimelineItemBipeHandleChangeModel`) before the indexer, so event indices stay right. A line shows only for joined members, inside the loaded range (not older than the oldest loaded event), and after the member's latest join in the loaded range. Text: `bipe_handle_set` / `bipe_handle_changed` / `bipe_handle_removed` by the fields present, `%1$s` = the member's display name in the room. View: `BipeHandleChangeLine` (libraries/bipe).

## Welcome screen

- `BipeWelcomeView` and `Modifier.bipeWelcomeBackground` (libraries/bipe) replace Element's logo, welcome text and `onboarding_bg` artwork in `OnBoardingView`. Copy: `bipe_onboarding_*` in bipe-shared. iOS: `BipeWelcomeView`.

## Threads and galleries on by default, no Labs (15 Oct 2026)

- **No Labs entry in settings.** `FeatureFlags.Knock` ("Ask to join") is not in Labs any more and stays off; it shows in developer options only. `PreferencesRootPresenter` hides the Labs entry when no Labs feature is left.
- `FeatureFlags.Threads` and `FeatureFlags.SendGalleryMessages` default to on and are finished, so they are not in Labs and an old Labs opt-out is ignored.
- The session cache is built differently with threads on. `AppMigration11` marks the sessions of an existing install in `BipeThreadsCacheRebuild` (libraries/bipe). `LoggedInFlowNode` clears each marked session's cache once with `ClearCacheUseCase`, the path the Labs toggle used. The interface moved to `features/preferences/api` for this. iOS skips the clear for users who had threads on in Labs; Android clears once for every existing session.

## Groups MVP (bipe/docs/groups.md, ADR 0026)

- **Quiet by default (#97):** `BipeQuietGroups` (libraries/bipe) runs from `LoggedInFlowNode` when the session starts. Group default becomes mentions and keywords; the override rule `chat.bipe.poll_start` (`*.poll.start`) makes new polls notify. It waits for Synapse `msc4028_push_encrypted_events`. Android needs no extra entitlement to drop pushes, unlike iOS.
- **"O que você perdeu" (#98):** rules in `features/messages/.../timeline/BipeMissedSummaryFactory.kt`, view `BipeMissedSummaryView` (libraries/bipe), shown in the read marker row through `TimelineRoomInfo.bipeMissedSummary`.

## Tab bar (ADR 0053)

- **Tabs:** Conversas, Grupos, Você in `HomeNavigationBarItem.bipeVisibleItems`. The Spaces tab stays in the code but is not shown. The avatar in the top bar opens Você.
- **Conversas and Grupos** use the one room list (`RoomListPresenter`) with a fixed SDK category filter: `Category.People` or `Category.Group`. The SDK decides with the DM flag. The People and Rooms chips are hidden. Pedidos stays on Conversas only.
- **Invite pairs (ADR 0034, bipe#413):** `BipeInvitePairs` (libraries/bipe, `SessionScope`) loads `GET /v1/me/invite-connections` at session start and keeps the last list in SharedPreferences `bipe_invite_pairs`, per account. `RoomListPresenter` leaves a DM invite from a pair out of Pedidos and joins it once (`autoAccept`). An invite from anyone else reloads the pairs once per room (`recheck`). Same as iOS `BipeInvitePairs`.
- **Join notifier (bipe#458):** `BipeInviteJoinNotifier` (libraries/bipe, `SessionScope`) runs from `LoggedInFlowNode` at session start: `GET /v1/me/invites`, a toast `bipe_invites_joined` once per join (seen IDs per account in SharedPreferences `bipe_invite_joins`; the first check on a device only records). Same as iOS `BipeInviteJoinNotifier`.
- **Invite note (ADR 0136, bipe#458):** "Convidar pessoas" asks for the note first (`BipeInviteNoteDialog`, pre-filled with `bipe_invite_note_default`, empty means no note). `BipeInviteCreator.create(note)` stores it in `BipeInviteLinkStore` (own file `bipe-invite-notes` in `noBackupFilesDir`, own Keystore key). The server never receives it. `BipeInviteNoteSender` sends it after the join check and after `BipeInvitePairs.autoAccept`, only into the joined DM with `joinedUserId` (`findDM`, or the room just accepted when its heroes hold that user). It never creates a DM. `takeNote` removes the note before the send; a send error puts it back. Nova conta invites (#445) have no note. The invitee's chat shows `bipe_inviter_knows_you_joined` above the "Mandar um oi" hint until a message from the inviter is in the timeline (`MessagesPresenter`, `BipeInviterHint`).
- **Archive (bipe#435):** a swipe to the left on a joined chat, or the menu item "Arquivar", sets `m.lowpriority` (`BaseRoom.setIsLowPriority`), so other clients agree. `RoomListPresenter` keeps archived chats out of the tab and counts them for the "Arquivadas" row (`BipeArchivedRow`) at the top. The row opens the archived chats of the tab (`bipeShowsArchived`); there the swipe and the menu say "Desarquivar", and the row or Back leads back. Same as iOS, which opens its low priority filter.
- **App lock screen (bipe#435):** on the app lock (not the in-app PIN check), `PinUnlockView` shows the Bipe background (`bipeWelcomeBackground`), the Bipe logo and "Bipe bloqueado" in place of the lock icon and "Digite seu PIN". The PIN dots, the keypad and the biometric prompt stay as they are. iOS shows its version while Face ID runs.
- **Badges:** `BipeTabBadges` (libraries/bipe) sums `numUnreadNotifications` over all joined rooms, split by the DM flag. The quiet-group push rules limit a group's count to mentions, replies and new polls. A group invite not in `SeenInvitesStore` adds 1 to Grupos.
- **Você** is the settings root (`PreferencesRootView` without its top bar). Each row opens the settings flow at that screen (`PreferencesEntryPoint.InitialTarget.BipeScreen`).
- **Support DM (ADR 0114):** Ajuda → "Falar com o suporte" (`help.support`) → `PreferencesEntryPoint.Callback.navigateToBipeSupport` → `LoggedInFlowNode.bipeOpenSupportChat`: `BipeSupportChat` calls `POST /v1/me/support`, joins the invite, then the room opens. Same flow as iOS.
- **Self-chat ("Anotações"):** `BipeSelfChat` (libraries/bipe). The room ID is in global account data `chat.bipe.self_chat`, content `{"room_id": "..."}`, the same as iOS. It opens the stored room when the user is joined, else creates an encrypted private room with no invites. The room does not show in Conversas or Grupos.
- **Search** stays in the top bar on Conversas and Grupos.

## "Conferir no Bipe" (bipe ADR 0091, bipe#294)

- **Share target:** Element's share intent. `RootFlowNode.onIncomingShare` asks `BipePixShareReader` (libraries/bipe, `pix/`) for shared text, or the QR codes in shared images (zxing-cpp, the reader Element uses for login QR). When it finds a Pix code or key it pushes `NavTarget.BipePixCheck` with `BipePixCheckView`, also with no account. "Enviar para uma conversa" continues to Element's share flow (`continueIncomingShare`). Close goes back to the app, not to the sharing app.
- **Logic:** `PixCheck` (BR Code TLV, CRC-16, key types, keys in the text, the rule 9 warning without `PIX_CODE` and `PIX_KEY`). Cases: `bipe-shared/test-vectors/pix-check.json` in `SharedVectorTest`.

## Not done

- **Firebase:** `libraries/pushproviders/firebase/src/*/res/values/firebase.xml` still holds Element's Firebase project (`vector-alpha`). FCM cannot work with the Bipe application ID until Bipe has its own Firebase project.
- **UnifiedPush:** the default gateway is still `matrix.gateway.unifiedpush.org`, a third party.
- **Help links:** `LearnMoreConfig` still points at `element.io/help`. iOS hides these links.
- **App icon:** `appicon/element` is Element's icon.
- **Default language:** a device in a language other than pt or en gets en (the `values/` fallback). iOS defaults to pt-BR.
- **`assetlinks.json`** on `bipe.chat` for verified app links.
- Bipe screens (phase 5) and the routing for Bipe links.

## Stickers (ADR 0029, 0033, issue #101)

- **Picker:** attachments sheet → `PickAttachmentSource.Sticker` → `MessageComposerState.stickerPicker` → `StickerPickerBottomSheet` in `MessagesView`. UI: `messagecomposer/BipeStickerPicker.kt`.
- **Packs:** MSC2545 (`im.ponies.*`). `DefaultStickerPackRepository` in `libraries/bipe/stickers` reads them over HTTP: the SDK cannot read custom room state under sliding sync. Same order as iOS: the user's pack, this room's packs, then enabled packs from other rooms.
- **Send:** `JoinedRoom.sendRaw("m.sticker", …)`. The SDK has no typed sticker send. `sendRaw` skips the send queue: no local echo, no retry. In a thread the content carries an `m.thread` relation.
- **Import:** "Importar do WhatsApp" in the sheet opens the system document picker for a WhatsApp chat export ZIP, WebP or PNG. `StickerImporter` (`java.util.zip`) reads the `.webp` entries, checks magic bytes, size (1 MB) and sides (1024 px), derives the shortcode from SHA-256 (`st_…`). `DefaultStickerPackRepository.importStickers` uploads them and adds them to `im.ponies.user_emotes`. Same rules as iOS.
- **WhatsApp folder:** the picker opens in `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers` (`EXTRA_INITIAL_URI`, Android 8+), where WhatsApp keeps every received sticker as `.webp`. The user selects several files there. This is an Android-only route; the help text says so (`bipe_stickers_import_help_folder_android`). Whether every Android version lets the picker open that folder is not verified.
- **Animated WebP:** Coil's `AnimatedImageDecoder` (already registered) plays it on Android 9+.
- **Make from a photo:** "Criar de uma foto" in the sheet → system photo picker → `DefaultStickerPackRepository.makeSticker` crops a centred square, scales to 512 × 512 and encodes WebP. No subject cut-out: that needs ML Kit (a Google dependency, not decided). iOS cuts out the subject with Vision.
- **Save a received sticker:** action `TimelineItemAction.SaveSticker` → `MessagesPresenter.handleSaveSticker` downloads the bytes with `MatrixMediaLoader` and calls `addSticker`.
- **Not compiled yet** (11 October 2026): this Mac has no JDK or Android SDK.

## DMs are DMs (16 Oct 2026)

- A DM never turns into a group. Room details for a DM: no topic, no polls history, no "Convidar" to the DM. "Convidar para um grupo" lists your joined groups (`BipeInviteToGroup` in libraries/bipe; not DMs, spaces, replaced rooms or the self-chat) and invites the other person to the one you pick. The group's invite permission is checked at pick time.
- The composer has no poll option in a DM (`MessageComposerState.isDm`).
- No room setup lines in a DM timeline: `RoomBeginningPostProcessor` drops invites, joins and "encryption enabled" (upstream already dropped the create event and the creator's join). A leave says "saiu da conversa" (`TimelineItemsFactoryConfig.isDm`). The leave snackbar and the media empty states say "conversa" in a DM. The details top bar has no title (upstream), so it needs no DM title.
- A DM has no leave in the UI. "Apagar conversa" replaces it in the room list menu and in room details (bipe#464). The user stays in the room, so the other person sees no change and no "saiu" line.
- One exception: the report screen (`ReportRoomView`) keeps its leave switch in a DM. With it on, the report leaves the room, so the other person sees that you left. Report and leave is the safety path, so it stays.
- "Apagar conversa" writes a marker in global account data `chat.bipe.cleared_chats`: `{ "rooms": { "<room_id>": { "ts": <ms> } } }`. The marker is the `origin_server_ts` of the latest event of the room, or the current time when the room has no latest event. `BipeClearedChats` reads the event from the server over HTTP (`BipeServerAccountData`; the SDK's copy waits for the next sync and can miss another device's marker), merges the entry and writes it back. It keeps the other entries, and a marker never moves back. A failed read writes nothing and shows the clear error (bipe#479). `ClearedChats` holds the rules and passes the shared vectors (`test-vectors/cleared-chats.json`).
- The room list, the archive, the tab badges and search leave out a DM when its latest event is at or before its marker. A newer event brings the DM back in its place by time, with the normal notification.
- The DM timeline drops every event at or before the marker, with its reactions and read receipts (`BipeClearedTimeline`). It has no room beginning. Back-pagination stops when the loaded events reach the marker. Message search drops the same events. The media gallery (`TimelineMediaGalleryDataSource`), the pinned list and the pinned banner (`DefaultPinnedEventsTimelineProvider.bipeKeptItems`) drop them too. The pinned count in room details still counts every pinned event ID, as on iOS.
- The share and forward pickers (`RoomSelectSearchDataSource`, markers of the selected account from `bipeClearedChatsFlow`) and the search history leave out a hidden DM. A clear removes the room's notifications from the tray (`NotificationCleaner.clearMessagesForRoom`) and its dynamic direct share shortcut (`NotificationConversationService.onClearedRoom`). A pinned shortcut stays. When every chat of Conversas is hidden, the tab shows its empty state.
- The SDK observes only typed account data events, not `chat.bipe.cleared_chats`. `BipeClearedChats` reads the event again from the local store when the room list changes (at most once a second) and after its own write. It keeps its own markers until the sync brings them back. Other devices of the account follow when account data syncs.
- Starting a chat with the same person opens the same room: the user is still joined, so `get_dm_rooms` finds it. The room opens empty after the marker.
- Clearing deletes nothing on the server or in the local media cache: the SDK has no media cache call for one room. The clear sends no read receipt: ADR 0050 allows public receipts only, and a public one tells the other person the chat was read.
- A DM that the user left before this change stays as it is.
- iOS does the same (`BipeInviteToGroupSheet`).
- A DM shows no 'sala' in any string (bipe#507); a new DM-reachable string gets a `bipe_dm_*` variant.
- A DM shows no topic anywhere (bipe#527). `RoomBeginningPostProcessor` drops topic events from the DM timeline, and `StateContentFormatter` gives no line for `OtherState.RoomTopic` with `isDm`, so a topic event is never the room list last message. A DM invite (`JoinRoomView`) shows no topic.
- A group says "descrição" for its topic, never "tópico": in Bipe "tópico" is a thread (`linguagem.md`). `bipe_string_overrides.xml` aliases the topic lines, `common_topic`, the create field and "Adicionar" to `bipe_group_*` strings (bipe#527).
- A group has no 'sala' in any string (bipe#508); new strings use 'grupo' or 'comunidade'.

## Differences from iOS (ADR 0031)

Each line is a difference with its reason. A difference not listed here is a bug. The table is generated from `bipe-shared/product/parity.json` (ADR 0092): change it there, then run `node tools/parity.mjs --write` in bipe-shared.

<!-- parity:differences (generated from bipe-shared/product/parity.json by tools/parity.mjs --write; do not edit) -->

| Area | Android | iOS | Reason |
|---|---|---|---|
| Dev app ID | `chat.bipe.app.debug` | `chat.bipe.app.dev` | Element's build-type suffix on each platform |
| OAuth redirect | `<application id>:/` | `<bundle id>:/oauth/callback` | Element's default per platform; both are reverse-domain private-use schemes |
| Push | FCM through Sygnal; UnifiedPush optional | APNs through Sygnal | OS push channel (#89, #90, #91) |
| Sign-up attestation | None yet | App Attest | Play Integrity not built on Android (#92) **Gap** |
| Criar conta da empresa | Company option in Nova conta hidden | Company option in Nova conta on a phone with App Attest | A company account needs an attested device (ADR 0043, ADR 0087); Play Integrity not built on Android (#92) **Gap** |
| Synced recovery token | Block Store (gplay); none in the F-Droid build | iCloud Keychain | Platform credential store (ADR 0059). F-Droid has no Google Play services |
| App lock | Off by default; switch in Sua conta. Element's lock screen, biometrics optional | Off by default; switch in Sua conta. Element's lock screen, biometrics optional | Element X lock screen per platform |
| Check before a PIN change | Biometrics or the phone's screen lock; else the app lock PIN | Biometrics or the phone's passcode | `LAContext` on iOS, `BiometricPrompt` with `DEVICE_CREDENTIAL` on Android |
| Invite code input | Pasted text collapses to the code in the field | Paste button | Platform idiom |
| Voice messages | Shows the transcript and checks it for scams; no sender-side transcription | Sender-side transcription, pause and resume | On-device recognition on low-end Android unmeasured (#80, #81) **Gap** |
| Where invites live | Convidar pessoas in settings | Contatos e convites: invite buttons, people who joined with your invite, and unused invites in the contacts screen | Android has no contacts screen yet (#401) **Gap** |
| Sticker from a photo | Centred square crop | Subject cut-out (Vision) | Cut-out needs ML Kit, not decided (#101) **Undecided** |
| Sticker import | Chat export ZIP or files; picker opens in WhatsApp's stickers folder | Chat export ZIP or files | iOS apps cannot read WhatsApp's files |
| Signed-device isolation | Fixed on | User preference, default on | Undecided (#102) **Undecided** |
| Language fallback | Bipe strings pt-BR, Element strings en | pt-BR for other device languages | Undecided (#102) **Undecided** |
| Handle lookup | Every user search | Start chat only | Undecided (#102) **Undecided** |
| Search | Search button in the top bar of Conversas and Grupos | Search tab (iOS 26+), inline before iOS 26 | Platform idiom (ADR 0053) |
| Quiet groups "done" marker | The poll rule in the account's push rules (once per account) | A flag on the device (once per device) | Android has no per-session store in libraries/bipe; same result for the user |
| Conferir no Bipe: close | Back to Bipe | Back to the app that shared | The iOS check runs in the share extension; Android shows it in the app (ADR 0091) |
| Message actions | Element's bottom sheet | Native context menu on long press: reaction row, actions, the message as preview | Platform idiom |
| Terms screen and a recovery alert due together | Recovery alert first, then the terms screen | Terms screen first, the alert right after Continue | On iOS a root alert cannot show over the full-screen terms cover; on Android the terms screen is an overlay that waits for the alert |
| Settings: Idioma | No row | Row that opens the iPhone Ajustes | iOS sets the app language in system settings; Android follows the system language |
| Warning haptic | One long-press pulse | Notification warning pattern | Android has no warning haptic constant (ADR 0134) |

<!-- /parity:differences -->

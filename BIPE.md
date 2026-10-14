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

The dev Mac has no JDK or Android SDK. Build on **mini4p** (Tailscale `mini-4p`), which has JDK 21 in `~/opt/jdk-21` and the SDK in `~/Library/Android/sdk` (platform 37.0, build-tools 37.0.0). It has no GitHub access: copy the tree over (`git ls-files -co --exclude-standard | tar`), make it a local git repo (the build reads git for BuildConfig), add `local.properties`, then `JAVA_HOME=~/opt/jdk-21/Contents/Home ./gradlew assembleGplayDebug`. First green build: 12 October 2026. The tar copy adds files but never deletes them, so files removed in the repo stay on mini4p (13 Oct 2026: old `.webp` launcher icons next to the new `.png` ones broke `packageDebugResources` with "Duplicate resources"). Build in a fresh directory, for example `~/Dev/Bipe/bipe-android-qr`.

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
| Telemetry | `BuildTimeConfig` | Sentry and PostHog keys stay `null`. Rageshake URL and app name are empty in `appconfig`, which disables bug reports. |
| Locales | `plugins/.../extension/locales.kt`, `app/src/main/res/xml/locales_config.xml` | pt-BR and en only. Edited by hand: do not re-run the Localazy import. |
| Accent colour | Compound tokens | Açaí from `../bipe-tokens`: `./tools/compound/import_bipe_tokens.sh`. Notification accent `#80307B` in `NotificationConfig`. |
| App links | `app/src/main/AndroidManifest.xml` | `bipe.chat` (and www) paths `/@`, `/convite/` and `/recuperar/` (recovery link, token in the fragment). Old `proseio.com.br` links still open the app (second filter, no autoVerify). Element hosts removed. |

## Device-first accounts (ADR 0057, ADR 0059, bipe#154)

- **Sign-up** (`features/login/.../bipesignup`): name, optional @, invite code, optional e-mail, then the 8-digit PIN typed twice, then (without an e-mail) the fallback prompt "Se você perder o celular" with "Adicionar e-mail" or "Agora não". Guardians and the recovery key are not offered yet. The request has no password: `POST /v1/signup` returns the hidden password and the recovery token once. No attestation (Play Integrity, #92), as before.
- **PIN rule:** `BipePin.problem()` (libraries/bipe `account/`), from `registries/accounts.json`. `SharedVectorTest.pins` runs `test-vectors/pins.json`.
- **Storage:** `BipeAccountVault` keeps the hidden password in one file in `noBackupFilesDir`, encrypted with an Android Keystore key (`bipe.SECRET_KEY_ALIAS_ACCOUNT`). Nothing backs it up (ADR 0059: the password stays on the device). The recovery token goes to `BipeRecoveryTokenStore`: Block Store with `shouldBackupToCloud = true` in the gplay build (`libraries/bipeblockstore`, `play-services-auth-blockstore` 16.4.0, `gplayImplementation` only). The F-Droid build has no Block Store: the token stays in the vault and does not sync, so a new phone cannot use the token path there. Google backs Block Store up end-to-end encrypted only when the phone has a screen lock.
- **Sign-in without a browser:** `BipeDeviceAccount.signIn` calls `MatrixAuthenticationService.setHomeserver` and `login(userId, password)`. The Rust SDK sends `m.login.password` (`m.id.user`). MAS answers it through its compatibility layer (`passwords.enabled: true`) and creates a compat session. The SDK uses OAuth only when the app asks for an OAuth URL, so this path never opens a browser.
- **App start without a session** (`LoginFlowNode.bipeStartDeviceAccount`): with the hidden password in the vault (e.g. sign-up made the account but the sign-in failed), sign in with no screen. With a waiting e-mail recovery or a recovery token in Block Store, open "Voltar pra sua conta": the token path asks for the PIN first (ADR 0059: a synced item never signs in alone). A sign-out deletes the hidden password (`SessionListener`), so after a sign-out, including the lock screen's sign-out after wrong PINs, the way back is the token and the PIN, which the server counts.
- **App lock:** Element's lock screen with an 8-digit PIN (`LockScreenConfig.PIN_SIZE`), the Bipe rule in `PinValidator`, biometric unlock in its settings. The account PIN and the lock PIN are one PIN: Bipe sets the lock PIN after sign-up, token recovery and the beta move (`BipeAppLock`, implemented in features/lockscreen), and `SetupPinPresenter` sends a PIN set in the lock screen settings to `PUT /v1/me/pin`.
- **Settings (Você):** `BipeAccountRows` under the invite row: "Trocar senha" after a biometric check (`BipeBiometricCheck`: biometrics or the phone's screen lock; without either, the current app lock PIN; without that, refused), the blocked state from `GET /v1/me/account` (a new PIN unblocks), and for beta accounts "Crie sua senha", which sets the PIN, calls `POST /v1/me/device-first` and stores the new credentials. Then `BipeRecoveryEmailRows`, the same copy and flow as the iOS email section: the confirmed and the pending address from `GET /v1/me/account`, a dialog to add or change it (`PUT /v1/me/email` with `lang`; HTTP 202, the address stays pending until the link is opened) and "Remover e-mail" after a confirmation (`DELETE /v1/me/email`).
- **Recovery** (`features/login/.../biperecovery`): token and PIN (`/v1/recovery/token`); QR code from the old phone (Element's QR sign-in); e-mail: `/v1/recovery/email`, then the app link `https://bipe.chat/recuperar/#<token>` asks for the PIN or ("Esqueci minha senha") a new PIN, `POST /v1/recovery/email/confirm`, keeps the claim secret in the vault and polls `/v1/recovery/{id}/claim` every minute. Guardian and CPF paths are not built (API not built).
- **Old devices:** `BipeRecoveryAlerts` (in `LoggedInFlowNode`) polls `GET /v1/me/account` every minute in the foreground and shows waiting recoveries (`bipe_recovery_alert_*`) with "Cancelar, não fui eu" (`POST /v1/me/recoveries/{id}/cancel`), and token sign-ins on another device. It ignores token recoveries older than this device's own sign-in, which covers this device's own token recovery. "Fui eu" hides the alert on this device only.
- **Beta login:** the onboarding sign-in opens Element's password screen (no browser) with "E-mail" as the field placeholder. MAS takes the e-mail as the login (`login_with_email_allowed`).

## Handle change lines (ADR 0066, bipe#166)

- **Remove the handle:** "Remover nome de usuário" on the "Nome de usuário" screen (`BipeHandleView`), also during the cooldown. A confirmation dialog (`bipe_handle_remove_body`), then `DELETE /v1/me/handle`. Same flow on iOS.

- `BipeHandleChanges` (in `BipeSession`) calls `POST /v1/handle-changes` in chunks of 100, with `since` = the oldest of the chunk's latest cached `at` (none when a user has no cached change; duplicates are dropped). It runs after each `resolve` refresh of `BipeHandleResolver` and on room open (`TimelinePresenter`), at most once a minute per user. The cache is in memory: an app restart fetches the history again.
- `BipeHandleChangeLines` (features/messages) inserts a virtual item (`TimelineItemBipeHandleChangeModel`) before the indexer, so event indices stay right. A line shows only for joined members, inside the loaded range (not older than the oldest loaded event), and after the member's latest join in the loaded range. Text: `bipe_handle_set` / `bipe_handle_changed` / `bipe_handle_removed` by the fields present, `%1$s` = the member's display name in the room. View: `BipeHandleChangeLine` (libraries/bipe).

## Welcome screen

- `BipeWelcomeView` and `Modifier.bipeWelcomeBackground` (libraries/bipe) replace Element's logo, welcome text and `onboarding_bg` artwork in `OnBoardingView`. Copy: `bipe_onboarding_*` in bipe-shared. iOS: `BipeWelcomeView`.

## Groups MVP (bipe/docs/groups.md, ADR 0026)

- **Quiet by default (#97):** `BipeQuietGroups` (libraries/bipe) runs from `LoggedInFlowNode` when the session starts. Group default becomes mentions and keywords; the override rule `chat.bipe.poll_start` (`*.poll.start`) makes new polls notify. It waits for Synapse `msc4028_push_encrypted_events`. Android needs no extra entitlement to drop pushes, unlike iOS.
- **"O que você perdeu" (#98):** rules in `features/messages/.../timeline/BipeMissedSummaryFactory.kt`, view `BipeMissedSummaryView` (libraries/bipe), shown in the read marker row through `TimelineRoomInfo.bipeMissedSummary`.

## Tab bar (ADR 0053)

- **Tabs:** Conversas, Grupos, Você in `HomeNavigationBarItem.bipeVisibleItems`. The Spaces tab stays in the code but is not shown. The avatar in the top bar opens Você.
- **Conversas and Grupos** use the one room list (`RoomListPresenter`) with a fixed SDK category filter: `Category.People` or `Category.Group`. The SDK decides with the DM flag. The People and Rooms chips are hidden. Pedidos stays on Conversas only.
- **Badges:** `BipeTabBadges` (libraries/bipe) sums `numUnreadNotifications` over all joined rooms, split by the DM flag. The quiet-group push rules limit a group's count to mentions, replies and new polls.
- **Você** is the settings root (`PreferencesRootView` without its top bar). Each row opens the settings flow at that screen (`PreferencesEntryPoint.InitialTarget.BipeScreen`).
- **Self-chat ("Anotações"):** `BipeSelfChat` (libraries/bipe). The room ID is in global account data `chat.bipe.self_chat`, content `{"room_id": "..."}`, the same as iOS. It opens the stored room when the user is joined, else creates an encrypted private room with no invites. The room does not show in Conversas or Grupos.
- **Search** stays in the top bar on Conversas and Grupos.

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
- **Packs:** MSC2545 (`im.ponies.*`). `HttpStickerPackRepository` in `libraries/bipe/stickers` reads them over HTTP: the SDK cannot read custom room state under sliding sync. Same order as iOS: the user's pack, this room's packs, then enabled packs from other rooms.
- **Send:** `JoinedRoom.sendRaw("m.sticker", …)`. The SDK has no typed sticker send. `sendRaw` skips the send queue: no local echo, no retry. In a thread the content carries an `m.thread` relation.
- **Import:** "Importar do WhatsApp" in the sheet opens the system document picker for a WhatsApp chat export ZIP, WebP or PNG. `StickerImporter` (`java.util.zip`) reads the `.webp` entries, checks magic bytes, size (1 MB) and sides (1024 px), derives the shortcode from SHA-256 (`st_…`). `HttpStickerPackRepository.importStickers` uploads them and adds them to `im.ponies.user_emotes`. Same rules as iOS.
- **WhatsApp folder:** the picker opens in `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers` (`EXTRA_INITIAL_URI`, Android 8+), where WhatsApp keeps every received sticker as `.webp`. The user selects several files there. This is an Android-only route; the help text says so (`bipe_stickers_import_help_folder_android`). Whether every Android version lets the picker open that folder is not verified.
- **Animated WebP:** Coil's `AnimatedImageDecoder` (already registered) plays it on Android 9+.
- **Make from a photo:** "Criar de uma foto" in the sheet → system photo picker → `HttpStickerPackRepository.makeSticker` crops a centred square, scales to 512 × 512 and encodes WebP. No subject cut-out: that needs ML Kit (a Google dependency, not decided). iOS cuts out the subject with Vision.
- **Save a received sticker:** action `TimelineItemAction.SaveSticker` → `MessagesPresenter.handleSaveSticker` downloads the bytes with `MatrixMediaLoader` and calls `addSticker`.
- **Not compiled yet** (11 October 2026): this Mac has no JDK or Android SDK.

## Differences from iOS (ADR 0031)

Each line is a difference with its reason. A difference not listed here is a bug. The table is generated from `bipe-shared/product/parity.json` (ADR 0092): change it there, then run `node tools/parity.mjs --write` in bipe-shared.

<!-- parity:differences (generated from bipe-shared/product/parity.json by tools/parity.mjs --write; do not edit) -->

| Area | Android | iOS | Reason |
|---|---|---|---|
| Dev app ID | `chat.bipe.app.debug` | `chat.bipe.app.dev` | Element's build-type suffix on each platform |
| OAuth redirect | `<application id>:/` | `<bundle id>:/oauth/callback` | Element's default per platform; both are reverse-domain private-use schemes |
| Push | FCM through Sygnal; UnifiedPush optional | APNs through Sygnal | OS push channel (#89, #90, #91) |
| Sign-up attestation | None yet | App Attest | Play Integrity not built on Android (#92) **Gap** |
| Synced recovery token | Block Store (gplay); none in the F-Droid build | iCloud Keychain | Platform credential store (ADR 0059). F-Droid has no Google Play services |
| App lock | Element's lock screen, 8-digit account PIN, biometric option | Element's lock screen, the account PIN, biometrics on | Element X lock screen per platform |
| Check before a PIN change | Biometrics or the phone's screen lock; else the app lock PIN | Biometrics or the phone's passcode | `LAContext` on iOS, `BiometricPrompt` with `DEVICE_CREDENTIAL` on Android |
| Invite code input | Pasted text collapses to the code in the field | Paste button | Platform idiom |
| Voice messages | Shows the transcript and checks it for scams; no sender-side transcription | Sender-side transcription, pause and resume | On-device recognition on low-end Android unmeasured (#80, #81) **Gap** |
| Help links | Still point to element.io | Hidden | Not done on Android (#205) **Gap** |
| Unencrypted room option | Removed | Still offered | Not done on iOS (#78) **Gap** |
| Sign-up form | Name, then PIN | One form | Not done on iOS (#196) **Gap** |
| bipe.chat/@handle link | Opens the app, goes nowhere | Opens the profile | Not done on Android (#299) **Gap** |
| Sticker from a photo | Centred square crop | Subject cut-out (Vision) | Cut-out needs ML Kit, not decided (#101) **Undecided** |
| Sticker import | Chat export ZIP or files; picker opens in WhatsApp's stickers folder | Chat export ZIP or files | iOS apps cannot read WhatsApp's files |
| Signed-device isolation | Fixed on | User preference, default on | Undecided (#102) **Undecided** |
| Language fallback | Bipe strings pt-BR, Element strings en | pt-BR for other device languages | Undecided (#102) **Undecided** |
| Handle lookup | Every user search | Start chat only | Undecided (#102) **Undecided** |
| Search | Search button in the top bar of Conversas and Grupos | Search tab (iOS 26+), inline before iOS 26 | Platform idiom (ADR 0053) |
| Quiet groups "done" marker | The poll rule in the account's push rules (once per account) | A flag on the device (once per device) | Android has no per-session store in libraries/bipe; same result for the user |

<!-- /parity:differences -->

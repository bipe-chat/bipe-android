# Proseio fork notes

This repo is Proseio's Android app: a fork of Element X Android (AGPL-3.0). Upstream conventions in `AGENTS.md` still apply. This file adds the fork rules. Plan: `../proseio/docs/android-plan.md`.

## Rules

- **All Proseio code lives in `libraries/proseio`** (the mirror of ProseioKit on iOS). Element X sources get small hooks only, marked `// Proseio`. Keep the upstream diff small so rebases stay cheap.
- **Never hand-edit `libraries/proseio/src/main/kotlin/.../generated/` or `res/values*/proseio_strings.xml`.** They come from `../proseio-shared` (`node tools/gen.mjs`). Change registries and strings there.
- Proseio UI strings are `R.string.proseio_*` from that module, not `temporary.xml` and not Localazy.
- Platform logic (badge verification, normalisation, scam detection) must pass `../proseio-shared/test-vectors/`, like iOS.
- New Proseio files carry the Proseio AGPL header (`Copyright 2026 Proseio.`, `SPDX-License-Identifier: AGPL-3.0-only`). `KonsistLicenseTest` accepts it.
- The `enterprise/` submodule (private Element repo) is not used. FOSS builds use `features/enterprise/impl-foss`.

## Upstream

- `upstream` = element-hq/element-x-android (push disabled). `origin` = proseio/proseio-android. Local `main` tracks `origin/main`.
- Rebase: `git fetch upstream && git rebase upstream/develop`.

## Build (green gate)

```bash
./gradlew assembleGplayDebug
```

The dev Mac has no JDK or Android SDK. Build on **mini4p** (Tailscale `mini-4p`), which has JDK 21 in `~/opt/jdk-21` and the SDK in `~/Library/Android/sdk` (platform 37.0, build-tools 37.0.0). It has no GitHub access: copy the tree over (`git ls-files -co --exclude-standard | tar`), make it a local git repo (the build reads git for BuildConfig), add `local.properties`, then `JAVA_HOME=~/opt/jdk-21/Contents/Home ./gradlew assembleGplayDebug`. First green build: 12 October 2026.

Shared test vectors (ADR 0031 rule 4): `libraries/proseio/src/test/.../SharedVectorTest.kt` reads `../proseio-shared/test-vectors` directly. Not run yet either.

```bash
./gradlew :libraries:proseio:testDebugUnitTest
```

## Configuration

| What | Where | Value |
|---|---|---|
| Application ID, name | `plugins/src/main/kotlin/config/BuildTimeConfig.kt` | `br.com.proseio.app` (debug adds `.debug`), "Bipe" (ADR 0054) |
| OAuth redirect scheme | `BuildTimeConfig.METADATA_HOST_REVERSED` | `br.com.proseio.app` (debug `br.com.proseio.app.debug`) |
| OAuth client, logo, terms, privacy URLs | `BuildTimeConfig.URL_*` | `https://proseio.com.br/...` (MAS requires them on the client URI host) |
| Server | `appconfig/.../ProseioConfig.kt`, used by `DefaultEnterpriseService` (impl-foss) | `proseio.com.br`, base `https://matrix.proseio.com.br`. Allow list of one, so the server picker is hidden. |
| Proseio API | `ProseioConfig.API_BASE_URL` | `https://api.proseio.com.br` |
| Firebase push gateway | `FirebaseConfig.PUSHER_HTTP_URL` | `https://push.proseio.com.br/_matrix/push/v1/notify` (own Sygnal, never matrix.org) |
| Pusher app IDs | `BuildTimeConfig.PUSHER_APP_ID_*` | `br.com.proseio.app.android.prod` / `.android.dev`. Sygnal must list them. |
| Telemetry | `BuildTimeConfig` | Sentry and PostHog keys stay `null`. Rageshake URL and app name are empty in `appconfig`, which disables bug reports. |
| Locales | `plugins/.../extension/locales.kt`, `app/src/main/res/xml/locales_config.xml` | pt-BR and en only. Edited by hand: do not re-run the Localazy import. |
| Accent colour | Compound tokens | Açaí from `../proseio-tokens`: `./tools/compound/import_proseio_tokens.sh`. Notification accent `#80307B` in `NotificationConfig`. |
| App links | `app/src/main/AndroidManifest.xml` | `proseio.com.br` paths `/@` and `/convite/`. Element hosts removed. |

## Groups MVP (proseio/docs/groups.md, ADR 0026)

- **Quiet by default (#97):** `ProseioQuietGroups` (libraries/proseio) runs from `LoggedInFlowNode` when the session starts. Group default becomes mentions and keywords; the override rule `br.com.proseio.poll_start` (`*.poll.start`) makes new polls notify. It waits for Synapse `msc4028_push_encrypted_events`. Android needs no extra entitlement to drop pushes, unlike iOS.
- **"O que você perdeu" (#98):** rules in `features/messages/.../timeline/ProseioMissedSummaryFactory.kt`, view `ProseioMissedSummaryView` (libraries/proseio), shown in the read marker row through `TimelineRoomInfo.proseioMissedSummary`.

## Tab bar (ADR 0053)

- **Tabs:** Conversas, Grupos, Você in `HomeNavigationBarItem.proseioVisibleItems`. The Spaces tab stays in the code but is not shown. The avatar in the top bar opens Você.
- **Conversas and Grupos** use the one room list (`RoomListPresenter`) with a fixed SDK category filter: `Category.People` or `Category.Group`. The SDK decides with the DM flag. The People and Rooms chips are hidden. Pedidos stays on Conversas only.
- **Badges:** `ProseioTabBadges` (libraries/proseio) sums `numUnreadNotifications` over all joined rooms, split by the DM flag. The quiet-group push rules limit a group's count to mentions, replies and new polls.
- **Você** is the settings root (`PreferencesRootView` without its top bar). Each row opens the settings flow at that screen (`PreferencesEntryPoint.InitialTarget.ProseioScreen`).
- **Self-chat ("Anotações"):** `ProseioSelfChat` (libraries/proseio). The room ID is in global account data `br.com.proseio.self_chat`, content `{"room_id": "..."}`, the same as iOS. It opens the stored room when the user is joined, else creates an encrypted private room with no invites. The room does not show in Conversas or Grupos.
- **Search** stays in the top bar on Conversas and Grupos.

## Not done

- **Firebase:** `libraries/pushproviders/firebase/src/*/res/values/firebase.xml` still holds Element's Firebase project (`vector-alpha`). FCM cannot work with the Proseio application ID until Proseio has its own Firebase project.
- **UnifiedPush:** the default gateway is still `matrix.gateway.unifiedpush.org`, a third party.
- **Help links:** `LearnMoreConfig` still points at `element.io/help`. iOS hides these links.
- **App icon:** `appicon/element` is Element's icon.
- **Default language:** a device in a language other than pt or en gets en (the `values/` fallback). iOS defaults to pt-BR.
- **`assetlinks.json`** on `proseio.com.br` for verified app links.
- Proseio screens (phase 5) and the routing for Proseio links.

## Stickers (ADR 0029, 0033, issue #101)

- **Picker:** attachments sheet → `PickAttachmentSource.Sticker` → `MessageComposerState.stickerPicker` → `StickerPickerBottomSheet` in `MessagesView`. UI: `messagecomposer/ProseioStickerPicker.kt`.
- **Packs:** MSC2545 (`im.ponies.*`). `HttpStickerPackRepository` in `libraries/proseio/stickers` reads them over HTTP: the SDK cannot read custom room state under sliding sync. Same order as iOS: the user's pack, this room's packs, then enabled packs from other rooms.
- **Send:** `JoinedRoom.sendRaw("m.sticker", …)`. The SDK has no typed sticker send. `sendRaw` skips the send queue: no local echo, no retry. In a thread the content carries an `m.thread` relation.
- **Import:** "Importar do WhatsApp" in the sheet opens the system document picker for a WhatsApp chat export ZIP, WebP or PNG. `StickerImporter` (`java.util.zip`) reads the `.webp` entries, checks magic bytes, size (1 MB) and sides (1024 px), derives the shortcode from SHA-256 (`st_…`). `HttpStickerPackRepository.importStickers` uploads them and adds them to `im.ponies.user_emotes`. Same rules as iOS.
- **WhatsApp folder:** the picker opens in `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers` (`EXTRA_INITIAL_URI`, Android 8+), where WhatsApp keeps every received sticker as `.webp`. The user selects several files there. This is an Android-only route; the help text says so (`proseio_stickers_import_help_folder_android`). Whether every Android version lets the picker open that folder is not verified.
- **Animated WebP:** Coil's `AnimatedImageDecoder` (already registered) plays it on Android 9+.
- **Make from a photo:** "Criar de uma foto" in the sheet → system photo picker → `HttpStickerPackRepository.makeSticker` crops a centred square, scales to 512 × 512 and encodes WebP. No subject cut-out: that needs ML Kit (a Google dependency, not decided). iOS cuts out the subject with Vision.
- **Save a received sticker:** action `TimelineItemAction.SaveSticker` → `MessagesPresenter.handleSaveSticker` downloads the bytes with `MatrixMediaLoader` and calls `addSticker`.
- **Not compiled yet** (11 October 2026): this Mac has no JDK or Android SDK.

## Differences from iOS (ADR 0031)

Each line is a deliberate difference with its reason. A difference not listed here is a bug.

| Area | Android | iOS | Reason |
|---|---|---|---|
| Dev application ID | `br.com.proseio.app.debug` | `br.com.proseio.app.dev` | Element's build-type suffix on each platform |
| OAuth redirect | `<application id>:/` | `<bundle id>:/oauth/callback` | Element's default per platform; both are reverse-domain private-use schemes |
| Push | FCM through Sygnal; UnifiedPush optional | APNs through Sygnal | OS push channel (#89, #90, #91) |
| Sign-up attestation | None yet | App Attest | Play Integrity not built (#92). **Gap** |
| Invite code input | Pasted text collapses to the code in the field | Paste button | Platform idiom |
| Voice messages | Shows the transcript and checks it for scams; no sender-side transcription | Sender-side transcription, pause and resume | On-device recognition on low-end Android unmeasured (#80, #81). **Gap** |
| Help links | Still point to element.io | Hidden | Not done on Android. **Gap** |
| App icon | Element's icon | Placeholder açaí bubble | Not done on Android. **Gap** |
| Sticker from a photo | Subject cut-out (Vision) | Centred square crop | Cut-out needs ML Kit, not decided |
| Sticker import | Chat export ZIP or files | Chat export ZIP or files; picker opens in WhatsApp's stickers folder | iOS apps cannot read WhatsApp's files |
| Signed-device isolation | Fixed on | User preference, default on | Undecided (#102) |
| Language fallback | en for other device languages | pt-BR | Undecided (#102) |
| Handle lookup | Every user search | Start chat only | Undecided (#102) |
| Unencrypted room option | Removed | Still offered | Not done on iOS (#78). **Gap** |
| Search | Search button in the top bar of Conversas and Grupos | Search tab | Platform idiom: Android has no search-tab pattern (ADR 0053) |
| Quiet groups "done" marker | The poll rule in the account's push rules (once per account) | A flag on the device (once per device) | Android has no per-session store in libraries/proseio; same result for the user |

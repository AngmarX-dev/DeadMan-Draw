# ☠️ Dead Man's Draw

**A pirate push-your-luck card game for Android — built natively with Kotlin.**

Dead Man's Draw is an Android game about deciding how long to risk a growing treasure board. The first player to push too far can lose an unbanked haul, so every draw is a wager. The game engine is plain Kotlin, separate from the Android UI, and the project no longer depends on Unity.

[![Android Kotlin CI](https://github.com/AngmarX-dev/DeadMan-Draw/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AngmarX-dev/DeadMan-Draw/actions/workflows/android-ci.yml)

## 🏴‍☠️ Play the game

Draw cards to grow the shared board. Each type normally appears only once on the board; a duplicate burns the unprotected cards. Collect to bank the haul and pass the turn. Special cards can change your fortunes, while Kraken pressure may force you to risk more draws.

### Current gameplay

- **2–8 players** with named pirates and player icons.
- **Ten card types:** Anchor, Cannon, Chest, Hook, Key, Kraken, Map, Mermaid, Oracle and Sword.
- **60-card setup:** 50 cards in the Draw Deck and 10 in the Burn Deck.
- **Targeted special-card actions:** Cannon, Hook, Map and Sword have interactive resolution flows and can be skipped.
- **Seventeen pirate traits:** effects are connected to the game engine, including Safe Harbor protection, Davy Jones' Locker, Misfire, Parry, Master Gunner, Scavenger, Treasure Hunter and Golden Scales.
- **Three AI difficulty levels:** Easy banks early, Normal balances risk and reward, and Hard estimates bust risk from remaining card types.
- **Custom rules:** turn Kraken forced draws and the Chest + Key bonus on or off.
- **Local pass-and-play:** share one device for a game with multiple human players.
- **Push-your-luck turns:** forced draws, duplicate busts, collection, and deck-exhaustion handling.
- **Scoring:** the highest banked value per type counts; trait bonuses are reflected in score breakdowns.
- **Chest + Key rewards:** collect a Burn Deck bonus; Treasure Hunter triples it, while Plunderer steals the bonus from a rival's bank.

### Visual design and accessibility

- **Original card illustrations from `Images.rar`** are now used on the board for all ten card types, with a vector fallback if an image resource is missing.
- **Trait artwork** from the archive appears in the trait-selection cards, and the Draw/Burn Deck indicators use the supplied card-back images.
- **Card-specific animations:** Cannon recoil, Kraken pulse, Sword movement, Map turn and Oracle glow, plus card-deal animations.
- **Animated screen transitions and controls**, bust feedback and a victory confetti celebration.
- **Three table themes:** Deep Ocean, Black Pearl and Tropical Reef.
- **Optional sound and haptic cues**, plus a Reduce Animations setting.
- **Shield indicators, named cards and content descriptions** so status is not conveyed by color alone.
- **Scrollable card board and large tap targets**, with portrait and landscape support.
- **First-run guide, card glossary, trait descriptions, turn log, end-game score breakdown and match statistics.**

### Persian interface inspired by `BackOfGame.pdf`

- Persian, right-to-left main menu with New Game, Continue, Group Game, Guide, Settings and Coin Shop navigation.
- Player settings include a saved display name, selectable avatar, sound/haptics, reduced motion, text size and table theme.
- A 12-colour card-back picker applies the selected tint to the Draw/Burn deck art.
- New Game setup supports 2–8 players, Easy/Normal/Hard difficulty, custom rules and local pass-and-play.
- The coin shop presents the three packs from the supplied mockups. Real-money purchases are intentionally disabled until a payment provider is integrated; choosing a pack never charges money or grants coins.

### Imported image assets

The `Images.rar` archive is retained at the repository root. Its original image files are extracted to `app/src/main/assets/images/Images/`; Android-compatible copies are also generated in `app/src/main/res/drawable-nodpi/` with valid lowercase resource names. The game UI loads the card and trait illustrations directly from these Android resources. Unity `.meta` import descriptors are not copied into the Android assets folder.

### Save and resume

The current voyage is stored in the app's private files while a game is in progress. Reopen the app to resume the saved voyage, or choose **New Voyage** to discard it. Completed games are recorded in local statistics.

## 📦 Install the Android app

### Install the debug APK

1. Open the [latest successful Android CI run](https://github.com/AngmarX-dev/DeadMan-Draw/actions/workflows/android-ci.yml).
2. Open a successful run and download the `dead-man-draw-apks` artifact.
3. Extract the downloaded ZIP to find `app-debug.apk`.
4. Install it on your Android device. You may need to allow installation from the file manager that opens the APK.

With Android Debug Bridge (ADB) and a connected device:

```bash
adb install -r app-debug.apk
```

### Versioned builds

Push a version tag to trigger the release workflow:

```bash
git tag v1.1.0
git push origin v1.1.0
```

The workflow runs the tests and packages the debug APK plus an optimized release APK. The debug APK is intended for easy testing. Configure these repository Actions secrets to produce a signed release APK: `ANDROID_KEYSTORE_BASE64` (base64-encoded keystore), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. Without them, the optimized release APK remains **unsigned** and is not store-ready.

See [GitHub Actions](https://github.com/AngmarX-dev/DeadMan-Draw/actions) for build logs and artifacts, or [GitHub Releases](https://github.com/AngmarX-dev/DeadMan-Draw/releases) for tagged packages.

## 🛠️ Build from source

### Requirements

- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle 8.9 (the CI workflow installs this version)
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

Run these commands from the repository root:

```bash
gradle --no-daemon clean testDebugUnitTest assembleDebug assembleRelease
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

To build the unsigned optimized release APK too:

```bash
gradle --no-daemon assembleRelease
```

Output:

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

## 🧪 Tests and CI

The JUnit suite covers deck construction and values, rule validation, duplicate busts, targeted card actions, core trait effects, custom rules, scoring, AI behaviour and save-state serialization. Seeded `kotlin.random.Random` instances make test scenarios repeatable.

Every push and pull request to `main` or `master` runs GitHub Actions to set up JDK/Android SDK/Gradle, run unit tests, build a debug APK and publish the APK as an artifact. Tagged versions use the release workflow.

## 🗺️ Roadmap status

The completed items below are implemented in the current code. The remaining items are deliberately left unchecked instead of being advertised as finished.

### Gameplay
- [x] Finish interactive targeting and resolution for Cannon, Hook, Map and Sword.
- [x] Connect all 17 trait effects to engine behaviour.
- [x] Make Easy / Normal / Hard change AI risk and collection decisions.
- [x] Add a turn log and explanations for special-card effects.
- [x] Add confirmation before collecting a larger board.
- [x] Add end-game score breakdowns and local match statistics.
- [x] Add quick rematch and configurable game rules.
- [x] Add local pass-and-play for multiple players on one device.

### Art, sound and feel
- [x] Add custom vector artwork for all ten card types.
- [x] Add optional draw, collect, bust and victory sound cues.
- [x] Add optional haptic feedback and a reduced-motion setting.
- [x] Add distinct animation treatments for special cards.
- [x] Add shield/state indicators and names so meaning is not color-only.
- [ ] Replace the vector illustrations and tone cues with a polished original art and sound asset pack.

### Usability and platform
- [x] Save and restore an unfinished voyage.
- [x] Add a first-run guide and a card glossary.
- [x] Support landscape orientation, scrollable layouts and scalable text.
- [x] Add accessibility descriptions and larger tap targets.
- [x] Add captain/rival names, player icons and selectable table themes.
- [x] Add a user-facing text-size control and wider-screen spacing.\n- [ ] Add dedicated multi-pane tablet layouts.

### Quality and publishing
- [x] Expand automated tests for busting, collecting, scoring, special cards, traits, AI and save-state serialization.
- [x] Add deterministic seeded scenarios for debugging.
- [x] Add local pass-and-play support.
- [x] Add a version-tagged release workflow and installation instructions.
- [ ] Add the production signing secrets and publish a verified optimized, signed release APK.
- [ ] Add secure online multiplayer, including an authoritative backend, lobby/match lifecycle, reconnection and server-side validation.

## 🤝 Contributing

Bug reports are most useful when they include the device/Android version, steps to reproduce, expected behaviour and the actual result. Please run `gradle --no-daemon clean testDebugUnitTest assembleDebug` before submitting a pull request.

---

**Project:** [AngmarX-dev/DeadMan-Draw](https://github.com/AngmarX-dev/DeadMan-Draw) · **Platform:** Android · **Language:** Kotlin

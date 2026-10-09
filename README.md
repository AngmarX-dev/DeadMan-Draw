# ☠️ Dead Man's Draw

**A pirate push-your-luck card game for Android, built natively with Kotlin.**  
This repository is a native Android project, not a Unity project. The game rules are separated into a Kotlin engine, while the interface is implemented with Android views.

[![Android CI](https://github.com/AngmarX-dev/DeadMan-Draw/actions/workflows/android-ci.yml/badge.svg)](https://github.com/AngmarX-dev/DeadMan-Draw/actions/workflows/android-ci.yml)

## 🏴‍☠️ Game overview

Build a treasure haul one card at a time. Each card type can appear only once on the shared board. Push your luck for a bigger score—or collect the board before a duplicate burns the unbanked cards.

## ✨ Current features

### Game and rules
- **2–8 players** in a game: one human player and AI-controlled rivals.
- **Ten card types:** Anchor, Cannon, Chest, Hook, Key, Kraken, Map, Mermaid, Oracle and Sword.
- **60-card setup:** 50 cards in the Draw Deck and 10 in the Burn Deck.
- **Push-your-luck turns:** draw cards, risk a duplicate-type bust, or collect the board and pass your turn.
- **Kraken pressure:** Kraken can require additional draws before the board can be collected.
- **Treasure scoring:** the highest banked value of each card type contributes to the final score.
- **Chest + Key bonus:** collecting a board with both can add cards from the Burn Deck.
- **Pirate traits:** 17 trait definitions with a trait choice at the start of a voyage.
- **AI opponents:** basic turn-taking and collect-versus-draw decisions.

### Visual polish and animation
- **Animated screen transitions** with a smooth fade and slide.
- **Animated card arrivals:** cards pop onto the board, settle into place and straighten as they land.
- **Color-coded treasure cards** with symbols, names, values and gold borders.
- **Bust feedback:** a shake animation and a warning color make a duplicate-card bust stand out.
- **Victory celebration:** a brief in-app confetti effect when the game ends.
- **Animated controls** with a press-and-release scale effect.
- **Pirate-inspired dark theme** with navy backgrounds, warm gold accents and highlighted panels.
- **Scrollable treasure board** so cards remain accessible on narrow screens.
- **Trait selection and in-game trait help**.

### Developer experience
- Kotlin game engine isolated from the Android activity.
- Unit tests for deck sizes, card value ranges and player-count validation.
- GitHub Actions workflow to run unit tests, build a debug APK and upload the APK artifact.

## 🚧 Roadmap

These are planned improvements, not claims that they are already implemented.

### Gameplay
- [ ] Finish interactive targeting and resolution for Cannon, Hook, Map and Sword.
- [ ] Complete and test all 17 trait effects against their descriptions.
- [ ] Make Easy / Normal / Hard affect actual AI strategy.
- [ ] Add a clearer turn log and explanations for special-card effects.
- [ ] Add a confirmation or undo option before collecting a large board.
- [ ] Add end-of-game score breakdowns and a match statistics screen.
- [ ] Add quick rematch and configurable game rules.

### Art, sound and feel
- [ ] Replace text-based cards with illustrated pirate card artwork.
- [ ] Add optional sound effects for draws, collecting, busts and victory.
- [ ] Add optional haptic feedback and a settings toggle for animation intensity.
- [ ] Add more distinct animations for special cards, Kraken turns and collecting treasure.
- [ ] Add an optional reduced-motion mode and improved color-blind-friendly indicators.

### Usability and platform
- [ ] Save and restore an unfinished game after the app is closed.
- [ ] Add a guided first-game tutorial and a glossary of card effects.
- [ ] Improve layout for tablets, landscape orientation and larger text settings.
- [ ] Add accessibility descriptions and larger tap targets.
- [ ] Add player names, avatars and selectable table themes.

### Quality and future modes
- [ ] Expand automated tests for busting, collecting, scoring and special traits.
- [ ] Add seeded/reproducible game scenarios for debugging.
- [ ] Add local pass-and-play support for multiple human players.
- [ ] Explore online multiplayer after the core rules are complete and well-tested.
- [ ] Add release builds, versioned releases and a documented installation process.

## 🧪 Build and test

### Requirements
- JDK 17
- Android SDK Platform 35
- Gradle 8.9 (the GitHub Actions workflow installs the expected Gradle version)
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

Run from the repository root:

```bash
gradle --no-daemon clean testDebugUnitTest assembleDebug
```

The debug APK will be created at:

```
app/build/outputs/apk/debug/app-debug.apk
```

To install on a connected Android device with ADB enabled:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## ✅ Project status and scope

The Android-native foundation, basic turn loop, initial scoring, visual effects and CI pipeline are in place. Some game rules remain incomplete: the trait list includes definitions for planned abilities, but not all special-card targeting flows and trait effects are implemented yet. The difficulty selector is present in the interface, while more meaningful differences between AI difficulty levels remain on the roadmap.

Contributions and issue reports are welcome—especially reproducible rules bugs, device-specific UI issues and failing tests.

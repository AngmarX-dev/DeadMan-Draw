# Dead Man's Draw ☠️

A native Android implementation of the pirate push-your-luck card game, built with **Kotlin** and the Android SDK. This repository is not a Unity project; the app and game logic use the required `DeadManDraws` package/namespace.

## Build

- JDK 17
- Android SDK platform 35
- Gradle 8.9 (CI installs Gradle)
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

From the repository root:

```bash
gradle testDebugUnitTest assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Current implementation

- Native Android setup screen with player-count and AI-difficulty controls.
- Kotlin game model for the ten card types and 17 trait definitions.
- 60-card deck setup: one lowest-value card of each type goes into the shuffled Burn Deck; the other 50 cards form the Draw Deck.
- Turn-based drawing, duplicate-type busting, collecting cards, trait selection, score calculation, and a basic AI turn.
- Unit tests for deck sizes, value ranges, and player-count validation.
- GitHub Actions build and test workflow.

## Notes

This is the initial Kotlin migration foundation, not a claim that every special card and trait effect is complete. Some interactive effects (including the full target-selection flows for Cannon, Hook, Map, and Sword) are explicitly marked in the game UI as pending implementation. The Unity scene/object architecture has been replaced with a native Android activity, while game rules live in a separate engine class.

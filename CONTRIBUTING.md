# Contributing

Thank you for helping improve Smart Route for the security and developer
communities.

## Getting started

1. Fork the repository.
2. `cp secrets.properties.example secrets.properties` and add your Maps key.
3. Open the project root in Android Studio (`File → Open`) — it imports as a
   standard Gradle project; AGP 8.7+, JDK 17, minSdk 24, targetSdk 34.
4. Create a branch: `git checkout -b feature/your-change`.
5. Run tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
6. Build a debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
7. Open a pull request with a clear description.

## Code guidelines

- Java 17, Material 3 components, AndroidX everywhere.
- Keep mock-injection logic centralized in `engine/MockLocationEngine.java`
  and `service/MockLocationForegroundService.java`.
- The route simulator (`engine/NativeRouteSimulator.java`) must remain a
  pure Java class with no Android dependencies — it's covered by JVM unit
  tests under `app/src/test/`.
- Document any new permission in both [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)
  and the README's permissions table.
- Do not add features intended solely for harassment, fraud, or evading law
  enforcement. PRs in that direction will be rejected.

## Pull request checklist

- [ ] `./gradlew :app:assembleDebug` succeeds.
- [ ] `./gradlew :app:testDebugUnitTest` passes.
- [ ] `./gradlew :app:lintDebug` produces no new warnings.
- [ ] README / USAGE updated if behavior or setup changes.
- [ ] No secrets (API keys, signing keystores, etc.) committed.

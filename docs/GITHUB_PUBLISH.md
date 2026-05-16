# Publishing to GitHub

## 1. Create the repository

On GitHub: **New repository** → name e.g. `smart-route` → Public → MIT license (optional).

## 2. Push from your machine

```bash
cd /home/simaa/Desktop/tryhackme/Tsukiyomi/Smart_Route
git init
git add .
git commit -m "Initial release: Smart Route v2 mock GPS for security research"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/smart-route.git
git push -u origin main
```

## 3. Before pushing

- Replace `YOUR_GOOGLE_MAPS_API_KEY` in `strings.xml` with a restricted key, or use Gradle secrets (do not commit real keys).
- Add repository topics: `android`, `flutter`, `gps`, `mock-location`, `cybersecurity`, `penetration-testing`.

## 4. Release

Tag `v2.0.0` and attach an APK built with:

```bash
flutter build apk --release
```

## 5. For your university report

Link the repo, include:

- Setup screenshots (Developer options, mock app selection)
- GPX export from a test run
- Honest limitations from [SECURITY.md](../SECURITY.md)
- Use cases from [USE_CASES.md](USE_CASES.md)

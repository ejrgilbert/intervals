# intervals

An app to help track labeled intervals. This was built for the purpose
of creating and executing run plans, but it can generalize to anything
that has timed intervals.

For now, this is just built/installed manually, but I may cut releases
to make it easier to install the `.apk` by just going to the repo on my
GH app and downloading the release.

# ideas

- [ ] figure out how to save run setups and create overarching plans to follow

# build
1. Open your project in Android Studio.
2. Go to Build → Build Bundle(s) / APK(s) → Build APK(s).
3. Wait for it to finish; you’ll get a notification in the bottom-right corner.
4. Click Locate in the notification. This will open the folder containing the APK (usually under `app/build/outputs/apk/debug/app-debug.apk`).

# transfer to android phone
- You can do this via USB cable, Google Drive, email, or any file-sharing app.
- On your phone, open the file and tap to install.
- If it says “Install blocked,” you need to allow Install unknown apps:
  1. Go to Settings → Apps → [Your file manager] → Install unknown apps.
  2. Enable the permission.
  3. Try installing again.


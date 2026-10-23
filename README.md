# Ironfield: Android app with automatic updates

The app is a small Android shell that opens the game full screen from your own free website on GitHub Pages.

- **Game updates:** replace `web/index.html` on GitHub and every player gets the new version the next time they open the app. They don't reinstall anything.
- **App updates:** these are rare and only needed if the Android shell itself changes. GitHub builds a new APK, and the installed app pops up "App update available" with a download button.
- **Offline:** the app uses the last version it loaded, or the copy built into the APK. Co-op needs internet anyway.

Everything is free: a GitHub account, GitHub Pages and GitHub Actions.

## One-time setup (about 15 minutes)

1. **Make a GitHub account** at github.com if you don't have one.
2. **Create a repository** named `ironfield`. It must be **Public**, because GitHub Pages is only free for public repositories. Don't add a README.
3. **Upload this folder's contents.** On the empty repository page, click **uploading an existing file** and drag in everything from this folder: `web`, `android`, `.github`, `README.md` and `.gitignore`. Then click **Commit changes**.
   - The `.github` folder is hidden on some computers. Turn on "show hidden files", or upload the files inside `.github/workflows` by hand into a folder you name `.github/workflows`.
4. **Turn on the website.** Go to **Settings → Pages → Build and deployment → Source** and choose **GitHub Actions**.
5. **Add the signing key.** Open the separate `ironfield-signing-key-KEEP-PRIVATE` folder and follow `READ-ME-FIRST.txt`. This adds two secrets under **Settings → Secrets and variables → Actions**.
6. **Publish the game.** Go to the **Actions** tab, then **Publish game update → Run workflow**. After a minute the game is live at `https://umvtt.github.io/ironfield/`, and it also works in any browser.
7. **Build the app.** In **Actions**, run **Build Android app → Run workflow**. It takes about 5 minutes. The APK then appears under **Releases** on the repository's main page.

## Give the app to people

Send them this link (replace YOUR-NAME):

`https://github.com/Umvtt/ironfield/releases/latest/download/ironfield.apk`

On their phone they open it, allow "install unknown apps" for their browser when Android asks, and install.

## Publish a game update

1. Rename the new `ironfield.html` to `index.html`. On GitHub, open the `web` folder, choose **Add file → Upload files**, and drop it in. It replaces the old one.
2. Commit. The **Publish game update** workflow runs by itself.
3. About a minute later, players get the update the next time they open the app.

## Publish an app update (rarely needed)

Change anything under `android/` and commit, or run **Build Android app** by hand. Installed apps then offer the new APK when they start. Saved progress is kept because the app is signed with the same key.

## Notes

- Saves stay on each phone, the same as in the browser.
- The Android back button works like Esc in the game. In the hangar, it closes the app.
- Google Play is a separate step: it needs a $25 developer account and a store listing. This setup is for sharing the APK directly.

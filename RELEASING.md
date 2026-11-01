# Releasing Tago

Each release is built and signed by GitHub Actions (`.github/workflows/release.yml`) and published
as a GitHub Release with the APK attached. People install it from the release page, or let
[Obtainium](https://github.com/ImranR98/Obtainium) keep it up to date.

## One-time setup

### 1. Create the GitHub repository
On github.com: **New repository**, name it `tago`, choose **Public** (Obtainium reads public
releases without a token), and do **not** add a README, .gitignore or licence (this repo has them).

### 2. Tell git who you are
Commits show this name and email publicly. For a private address, use the one GitHub gives you
under **Settings → Emails → Keep my email addresses private**.

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

### 3. Make the first commit and push
```bash
git commit -m "Tago 1.0.0"
git remote add origin https://github.com/YOUR-USERNAME/tago.git
git push -u origin main
```
Git for Windows opens a browser to sign in to GitHub the first time.

### 4. Create the release signing key (once, keep forever)
Every update must be signed with this same key, or Android refuses to install it over the old
version. **If it is lost, no existing install can ever be updated.** Keep it outside this folder,
back it up in two places, and store its password somewhere safe.

In PowerShell:
```powershell
New-Item -ItemType Directory -Force "$HOME\Documents\tago-keys"
& "C:\Program Files\Android\Android Studio1\jbr\bin\keytool.exe" -genkeypair -v -keystore "$HOME\Documents\tago-keys\tago-release.jks" -alias tago -keyalg RSA -keysize 4096 -validity 10000
```
It asks for a password and a few name fields (any values are fine).

### 5. Give GitHub the key as secrets
Repository → **Settings → Secrets and variables → Actions → New repository secret**, four times:

| Name | Value |
|---|---|
| `TAGO_KEYSTORE_BASE64` | the key file as text (command below copies it to the clipboard) |
| `TAGO_KEYSTORE_PASSWORD` | the password you chose |
| `TAGO_KEY_ALIAS` | `tago` |
| `TAGO_KEY_PASSWORD` | the same password (keytool uses one for both unless you set two) |

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Documents\tago-keys\tago-release.jks")) | Set-Clipboard
```

## Every release

```bash
git tag v1.0.0
git push origin v1.0.0
```
Watch it in the repository's **Actions** tab (about 5 minutes). When it finishes,
**Releases** has `Tago-v1.0.0.apk`. The tag sets the version: `v1.2.3` becomes version 1.2.3,
and each tag must be higher than the last (v1.0.1, v1.1.0, v2.0.0 ...).

Share this link, which always points at the newest release:
`https://github.com/YOUR-USERNAME/tago/releases/latest`

## For the people you share with
- **Install:** open the latest-release link on the phone, download the APK, allow installs from
  the browser when asked.
- **Automatic updates:** install Obtainium, tap **Add app**, paste
  `https://github.com/YOUR-USERNAME/tago`. It checks for new releases and installs them.

## Moving from the debug build
Phones running the test APK (`dist/Tago-1.0-debug.apk`, signed with this PC's debug key) can't
update to the release-signed version: Android sees a different signer. Export a backup in
Settings first, uninstall, install the release, then restore the backup.

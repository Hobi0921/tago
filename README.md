<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/tago/png/tago-horizontal-reversed-color-1208.png">
    <img src="branding/tago/png/tago-horizontal-color-1208.png" alt="Tago" width="360">
  </picture>
</p>

<p align="center"><b>Your passwords, kept out of sight.</b></p>

<p align="center">
  A password manager for Android that keeps every login encrypted on your phone,<br>
  unlocked only by your fingerprint, face or screen lock.
</p>

<p align="center">
  <a href="https://github.com/StenisTired/tago/releases/latest"><b>Download the latest version</b></a>
  &nbsp;·&nbsp; Android 8.0 or newer
</p>

---

## Why Tago

Most of us reuse a few passwords everywhere, because remembering dozens of strong ones is
impossible. Tago remembers them for you, fills them into your apps and websites, and helps you
replace the weak and reused ones.

It does this without an account and without a cloud. Your vault lives only on your phone,
encrypted, and only you can open it: not the developer, and not a server somewhere.

*Tago* is Tagalog for **"hidden, kept out of sight."** The logo is the letter T traced on
Android's nine-dot unlock grid: the secret pattern only you know.

## Features

### Your vault
- **Encrypted storage.** Every login is kept in an AES-256 encrypted database. The key that
  opens it is protected by Android's Keystore (in dedicated security hardware on phones that
  have it) and released only after your fingerprint, face or screen lock.
- **Search, favourites and quick copy.** Find a login as you type, star the ones you use most,
  and copy a password with one tap.
- **Everything about a login in one place.** Username, password, website, notes and 2FA codes,
  plus previous passwords if you ever need to go back.
- **Auto-lock.** Locks itself when you leave the app: immediately, or after 30 seconds, 1, 5 or
  15 minutes (1 minute by default).

### Fill and save in other apps
- **Autofill in apps and browsers.** Your logins appear right in sign-in forms. If the vault is
  locked, Tago asks for your fingerprint first.
- **Saves new logins for you.** Sign in somewhere new and Tago offers to save it.
- **Keeps up when you change a password.** Change a password in another app and Tago updates the
  existing login instead of creating a duplicate. The old password is kept in its history.
- **Works with most browsers,** including Chrome, Firefox, Edge, Brave, Samsung Internet, Opera and
  DuckDuckGo.

### Strong passwords
- **Generator.** Random passwords with the length and characters you choose, or easy-to-type
  passphrases made of words. It also appears on sign-up forms as "Use a strong password".
- **Strength checker.** Type any password to see how long it would take to crack and what makes
  it weak. It catches the patterns real attackers try first, so `Password123!` is rated
  *very weak*, not strong.

### Password health
- **A health score** for the whole vault, and a list of exactly what to fix.
- **Finds reused and weak passwords,** and passwords you haven't changed in a long time.
- **Breach check.** Checks whether your passwords have appeared in known data breaches, using
  *Have I Been Pwned*, without ever sending a password (see Privacy below).
- **2FA codes.** Store a login's authenticator key and Tago shows its current 6-digit code.

### Safety details
- **No screenshots.** Screens that show secrets can't be captured or recorded, and Tago's preview
  in the recent-apps list stays blank.
- **Clipboard cleared after 45 seconds,** and copied passwords are marked private so Android
  doesn't show them in its clipboard preview.
- **Masked by default.** Passwords stay hidden until you tap to reveal them, and hide again when
  you leave.
- **Encrypted backups.** Export your vault to a file protected by a passphrase you choose, and
  restore it on a new phone.
- **Light and dark themes,** following your phone's setting.

## Install

1. On your Android phone, open the
   [latest release](https://github.com/StenisTired/tago/releases/latest) and download the
   `.apk` file.
2. Open it. If Android asks, allow installs from your browser or file manager.
3. Open Tago and tap **Set up Tago**. It asks for your fingerprint or screen lock: that's what
   unlocks your vault from then on.

Tago isn't on the Play Store, so Android or Play Protect may warn you about an app from an
unknown developer. That is normal for apps installed this way.

### Get updates automatically
Install [Obtainium](https://github.com/ImranR98/Obtainium), tap **Add app**, and paste:

```
https://github.com/StenisTired/tago
```

Obtainium checks for new versions of Tago and installs them for you.

## Getting started

1. **Turn on autofill.** The vault shows a card for it; tap **Turn on** and pick Tago in the
   screen that opens. In Chrome, also turn on *Chrome settings → Autofill services → Autofill
   using another service.*
2. **Add your logins,** or just sign in to your apps and websites and let Tago offer to save them.
3. **Make a backup.** Go to **Settings → Back up now** and keep the file somewhere safe.

## Before you rely on it

- **Keep a backup.** Your vault exists on one phone only; there is no sync. If you lose the
  phone, a backup file is the only way to get your passwords back.
- **Changing your screen lock or fingerprints can lock you out.** That's a deliberate security
  measure: new fingerprints must not be able to open an old vault. If it happens, Tago offers to
  start a fresh vault and restore your backup into it.
- **Moving to a new phone:** install Tago there, choose **I have a backup** on the welcome screen,
  and pick your backup file.

## Privacy

Tago has no account, no ads and no analytics. Your passwords never leave your phone except in
backup files you create yourself. It only goes online for two optional things:

- **Breach check** (only when you tap it). Tago sends the first 5 characters of a fingerprint of
  each password (its SHA-1 hash) to *Have I Been Pwned*, and compares the results on your phone.
  The password itself, and even its full hash, is never sent.
- **Website logos.** To show a site's logo next to a login, Tago downloads it directly from that
  website, never from a third-party icon service. You can turn this off in Settings.

Cloud backup of the app's data is switched off, so your vault is never copied to Google's
servers.

## For developers

Tago is written in Java with Android Views and Material 3. Build notes, the security design and
open ideas are in [DEVELOPMENT.md](DEVELOPMENT.md). Releases are built and signed by GitHub
Actions when a version tag is pushed; see [RELEASING.md](RELEASING.md).

<p align="center"><sub>Made by <a href="https://github.com/StenisTired">StenisTired</a>.</sub></p>

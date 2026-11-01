# Tago for Android (formerly PassVault)

The app is now called **Tago** (Tagalog for "hidden, kept out of sight"). Only user-facing text and
the icon changed: the code keeps its `com.passvault` package and application ID, so Tago installs
over existing PassVault builds and old backups still restore. Brand files and their generators are
in `branding/tago` (start with `guide/tago-logo-guide.pdf`).

An Android port of the JavaFX password manager, in Java with XML layouts.
The three original files map across like this:

| Desktop | Android |
|---|---|
| `PasswordEntry.java` | `data/PasswordEntry.java` — now a Room `@Entity` |
| `PasswordFileHandler.java` | `data/AppDatabase.java` + `VaultRepository.java` — encrypted SQLite |
| `PasswordStrengthChecker.java` | `util/PasswordStrengthChecker.java` — rewritten, entropy-based |
| `MainController.java` | split across `ui/` fragments |
| `style.css` | `res/values/colors.xml` + `themes.xml` — same palette |

## What changed, and why

**Storage.** The desktop version wrote `site|user|password|strength` to a plaintext
file. On Android that file is readable by anything with root, and it lands in cloud
backups by default. Passwords now live in a SQLCipher database (AES-256, page-level).
The pipe delimiter is gone too — it corrupted any password containing `|`.

**Key handling.** The database passphrase is 32 random bytes, generated once. It is
never stored in the clear: it's wrapped with an AES-GCM key that lives in the Android
Keystore under `setUserAuthenticationRequired(true)`, so the OS refuses to produce a
working cipher until biometric or device-credential auth passes. On devices with a
secure element the key never leaves it.

**Strength scoring.** The old scorer counted length tiers and character classes, which
rated `Password123!` as Very Strong. The new one estimates entropy from the character
pool, then subtracts bits for the patterns that make passwords guessable: dictionary
words after leet de-substitution, keyboard runs, sequences, repeats, years, and the
capital-word-digits-symbol template. Passphrases get a separate word-based estimate,
because counting characters rated `correct-horse-battery-staple` at 164 bits when the
real figure is nearer 57.

Measured behaviour after the rewrite:

```
"Password123!"                  Very weak    22.0 bits   instantly
"Manila2024!"                   Very weak    26.0 bits   instantly
"correct-horse-battery-staple"  Strong       57.2 bits   9 days
"7Kq!vX2mZ#pL9wRt"              Very strong 105.1 bits   longer than the universe has existed
```

**Strength is no longer stored.** It's derived on read, so improving the scorer
re-rates every existing entry instead of leaving stale labels in the database.

## Features

- Biometric / device-credential unlock, with auto-lock after 60 seconds in the background
- Encrypted vault with search, add, edit, delete
- Live strength checker with entropy, crack-time estimate, and specific advice
- Generator: configurable length and character classes, or a 1,492-word passphrase mode
- Autofill in apps and browsers: fills logins (unlocking first if needed), offers a generated
  password on sign-up and change-password forms, saves new logins and updates the existing
  entry when a password is changed in another app (the old one goes to history)
- `FLAG_SECURE` on both activities: no screenshots, no recents thumbnail
- Clipboard marked sensitive and auto-cleared after 45 seconds
- Cloud backup and device transfer explicitly disabled

## Build

Requires Android Studio, JDK 17, `minSdk 26` (the autofill API's floor).

```
./gradlew assembleDebug
```

Enable autofill after installing: **Settings → Passwords & accounts → Autofill service
→ PassVault**. Android will not offer it until the app is installed and the vault has
been set up once. In Chrome 135+, also turn on Chrome settings → Autofill services →
"Autofill using another service"; other Chromium browsers and Samsung Internet work through
Android's compatibility mode (Android 9+).

## Things worth knowing before you rely on this

**Changing your screen lock or biometrics destroys the vault.** `setInvalidatedByBiometricEnrollment(true)`
means enrolling a new fingerprint invalidates the Keystore key, and the wrapped
passphrase becomes undecryptable. That is the correct security behaviour, but without
an export feature it means data loss. Build encrypted export/import before you put
real passwords in.

**No sync and no recovery.** The vault exists on one device only.

**The word list is inlined.** 1,492 words at 10.5 bits each. A production build should
ship the EFF 7776-word long list (12.9 bits/word) as an asset instead.

**The common-password list is small.** It catches the obvious cases. Real coverage
means shipping a 10k-entry list, or swapping in the `zxcvbn` Kotlin port and calling
it from Java.

## Version 2 additions

- **Encrypted export/import** (`security/VaultExporter.java`). PBKDF2-HMAC-SHA256 at
  600k iterations, AES-256-GCM. Verified for round-trip fidelity, tamper detection,
  and unique salt/IV per export. Restore is additive, so a mistyped filename cannot
  destroy the vault.
- **Vault health** (`audit/`). Reuse, weakness, and staleness offline; breach checking
  on demand. Reuse outranks weakness because one site's breach compromises every
  account sharing that password.
- **Breach checking** (`net/BreachChecker.java`). Have I Been Pwned via k-anonymity:
  only the first five characters of the SHA-1 leave the device. A failed check reports
  as unknown, never as clean.
- **TOTP** (`util/TotpGenerator.java`, `util/Base32.java`). Passes all five RFC 6238
  test vectors.
- **Password history** with a Room migration. Schema is v2, `exportSchema = true`, and
  there is deliberately no destructive fallback.
- **Recovery path** (`ui/UnlockActivity.java`). When the Keystore key is invalidated,
  the unlock screen offers to erase the unreadable database, build a fresh vault and
  open the restore picker. Without it, that state was a dead end.
- **Backup visibility** (`data/AppPrefs.java`). Settings shows when the last export
  happened; the vault shows a dismissible reminder while there has never been one.
- **Auto-lock timeout** picker (immediately / 30s / 1m / 5m / 15m) and live autofill
  status in Settings.
- **Bottom navigation** across Vault / Health / Tools (Generate + Check) / Settings.
  Tabs are kept alive and hidden/shown, so switching keeps search, scroll and results.
- **Entry detail sheet** (`ui/EntryDetailSheet.java`). Copy username, password or the
  live 2FA code, reveal, favorite, previous passwords, and Edit / Delete with Undo.
- **Full-screen editor** (`ui/EntryEditorDialog.java`) with 2FA key and notes. Edits go
  through `VaultRepository.applyEdit`, which loads the row first so fields the editor
  does not show (`packageName`, `favorite`, `createdAt`) survive.
- **Secure dialogs** (`ui/SecureWindows.java`). FLAG_SECURE is per window, so every
  dialog and sheet that can show a secret sets it on its own window.

## Suggested next steps

1. Swipe-to-delete on vault rows via `ItemTouchHelper`
2. Scan a QR code for the 2FA setup key instead of pasting it
3. Swap PBKDF2 for Argon2id in the exporter and bump the envelope version
4. Ship the EFF 7776-word list as an asset instead of the inlined 1,492 words
5. Write a Room migration test against the exported schema JSON

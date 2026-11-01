package com.passvault.autofill;

import android.app.assist.AssistStructure;
import android.os.Build;
import android.text.InputType;
import android.util.Pair;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Walks the AssistStructure the autofill framework hands us and works out the username
 * field, every password field and what each one is for, and which site or app it belongs to.
 *
 * Works on any text field the framework reports, not only EditText, so Compose, custom
 * views and web pages (WebView, browsers) are covered. Signals, strongest first:
 *   1. Declared autofill hints ("password", "newPassword", "username", ...).
 *   2. Web form attributes (type, autocomplete, name, id, placeholder).
 *   3. Input type.
 *   4. Keywords in the view id, hint and content description.
 *   5. Position: the text field just before a password is usually the username.
 */
public class ParsedStructure {

    /** What a password field is for. Change forms have all three; logins only CURRENT. */
    public enum Role { CURRENT, NEW, CONFIRM, UNKNOWN }

    public static final class PasswordField {
        public final AutofillId id;
        @Nullable public final String value;
        private final Role declared;
        private Role role;

        PasswordField(AutofillId id, @Nullable String value, Role declared) {
            this.id = id;
            this.value = value;
            this.declared = declared;
            this.role = declared;
        }

        public Role role() { return role; }
    }

    /** Package of the app that asked. For a browser, {@link #webDomain} is what matters. */
    public String packageName;
    /** Normalised host of the page, or null for a native app screen. */
    @Nullable public String webDomain;

    @Nullable public AutofillId usernameId;
    @Nullable private String usernameValue;
    private int usernameScore;

    public final List<PasswordField> passwords = new ArrayList<>();

    /** Last plain text field seen before the first password: the username, by position. */
    @Nullable private AutofillId textBeforePassword;
    @Nullable private String textBeforePasswordValue;
    @Nullable private String urlBarText;

    // ------------------------------------------------------------------ queries

    public boolean isUsable() {
        return usernameId != null || !passwords.isEmpty();
    }

    /** True on sign-up and change-password forms: somewhere to put a new password. */
    public boolean hasNewPasswordField() {
        for (PasswordField f : passwords) {
            if (f.role == Role.NEW || f.role == Role.CONFIRM) return true;
        }
        return false;
    }

    /** Where a saved password goes: the login field, or a change form's "current" field. */
    @Nullable
    public AutofillId fillPasswordId() {
        for (PasswordField f : passwords) {
            if (f.role == Role.CURRENT) return f.id;
        }
        return null;
    }

    /** New and confirm fields, which a generated password fills together. */
    public List<AutofillId> newPasswordIds() {
        List<AutofillId> ids = new ArrayList<>();
        for (PasswordField f : passwords) {
            if (f.role == Role.NEW || f.role == Role.CONFIRM) ids.add(f.id);
        }
        return ids;
    }

    public AutofillId[] allIds() {
        List<AutofillId> ids = new ArrayList<>();
        if (usernameId != null) ids.add(usernameId);
        for (PasswordField f : passwords) ids.add(f.id);
        return ids.toArray(new AutofillId[0]);
    }

    @Nullable public String usernameValue() { return nonEmpty(usernameValue); }

    @Nullable public String currentPasswordValue() { return valueFor(Role.CURRENT); }

    @Nullable public String newPasswordValue() { return valueFor(Role.NEW); }

    @Nullable
    private String valueFor(Role role) {
        for (PasswordField f : passwords) {
            if (f.role == role && nonEmpty(f.value) != null) return f.value;
        }
        return null;
    }

    // ------------------------------------------------------------------ parsing

    public static ParsedStructure parse(AssistStructure structure) {
        ParsedStructure parsed = new ParsedStructure();
        parsed.packageName = structure.getActivityComponent().getPackageName();

        int windows = structure.getWindowNodeCount();
        for (int i = 0; i < windows; i++) {
            parsed.traverse(structure.getWindowNodeAt(i).getRootViewNode());
        }

        // Compatibility-mode browsers often report no web domain; their address bar has it.
        if (parsed.webDomain == null && parsed.urlBarText != null) {
            parsed.webDomain = Domains.normalize(parsed.urlBarText);
        }
        if (parsed.usernameId == null && parsed.textBeforePassword != null
                && !parsed.passwords.isEmpty() && parsed.passwords.size() <= 2) {
            parsed.usernameId = parsed.textBeforePassword;
            parsed.usernameValue = parsed.textBeforePasswordValue;
        }
        parsed.resolveRoles();
        return parsed;
    }

    private void traverse(AssistStructure.ViewNode node) {
        if (node == null) return;
        // Hidden subtrees hold decoys and inactive steps of multi-step forms.
        if (node.getVisibility() != View.VISIBLE) return;

        String domain = node.getWebDomain();
        if (webDomain == null && domain != null && !domain.isEmpty()) {
            webDomain = Domains.normalize(domain);
        }

        if (isUrlBar(node)) {
            CharSequence text = node.getText();
            if (text != null) urlBarText = text.toString();
        } else if (!excluded(node)) {
            classify(node);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.getImportantForAutofill()
                == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            traverse(node.getChildAt(i));
        }
    }

    private boolean isUrlBar(AssistStructure.ViewNode node) {
        String id = node.getIdEntry();
        return id != null && id.equals(Browsers.urlBarId(packageName));
    }

    private static boolean excluded(AssistStructure.ViewNode node) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int important = node.getImportantForAutofill();
            if (important == View.IMPORTANT_FOR_AUTOFILL_NO
                    || important == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS) {
                return true;
            }
        }
        return false;
    }

    private void classify(AssistStructure.ViewNode node) {
        AutofillId id = node.getAutofillId();
        if (id == null || node.getAutofillType() != View.AUTOFILL_TYPE_TEXT) return;
        // Passwords may only be filled into editable fields, not labels that report text.
        if (!node.isEnabled()) return;

        String value = textValue(node);
        Kind kind = Kind.NONE;
        Role role = Role.UNKNOWN;

        // 1. Declared hints.
        String[] hints = node.getAutofillHints();
        if (hints != null) {
            for (String hint : hints) {
                if (hint == null) continue;
                String h = hint.toLowerCase(Locale.ROOT);
                if (isOneTimeCode(h)) return;
                if (h.contains("newpassword") || h.contains("new-password")) {
                    kind = Kind.PASSWORD; role = Role.NEW; break;
                }
                if (h.contains("current-password")) {
                    kind = Kind.PASSWORD; role = Role.CURRENT; break;
                }
                if (h.contains("password")) { kind = Kind.PASSWORD; break; }
                if (h.contains("username") || h.contains("email") || h.contains("phone")) {
                    kind = Kind.USERNAME_DECLARED; break;
                }
            }
        }

        // 2. Web form attributes.
        String html = "";
        ViewStructure.HtmlInfo info = node.getHtmlInfo();
        if (info != null && info.getAttributes() != null) {
            String type = null, autocomplete = null;
            StringBuilder words = new StringBuilder();
            for (Pair<String, String> attr : info.getAttributes()) {
                if (attr.first == null || attr.second == null) continue;
                String name = attr.first.toLowerCase(Locale.ROOT);
                String val = attr.second.toLowerCase(Locale.ROOT);
                switch (name) {
                    case "type": type = val; break;
                    case "autocomplete": autocomplete = val; break;
                    case "name": case "id": case "placeholder": case "aria-label": case "label":
                        words.append(val).append(' ');
                        break;
                    default: break;
                }
            }
            html = words.toString();
            if (type != null && (type.equals("hidden") || type.equals("submit")
                    || type.equals("button") || type.equals("search"))) return;
            if (autocomplete != null) {
                if (isOneTimeCode(autocomplete)) return;
                if (kind == Kind.NONE) {
                    if (autocomplete.contains("new-password")) {
                        kind = Kind.PASSWORD; role = Role.NEW;
                    } else if (autocomplete.contains("current-password")) {
                        kind = Kind.PASSWORD; role = Role.CURRENT;
                    } else if (autocomplete.contains("username") || autocomplete.contains("email")) {
                        kind = Kind.USERNAME_DECLARED;
                    }
                }
            }
            if (kind == Kind.NONE && "password".equals(type)) kind = Kind.PASSWORD;
            if (kind == Kind.NONE && "email".equals(type)) kind = Kind.USERNAME_DECLARED;
        }

        String words = (safe(node.getIdEntry()) + " " + safe(node.getHint()) + " "
                + safe(node.getContentDescription()) + " " + html).toLowerCase(Locale.getDefault());
        if (containsAny(words, OTP_WORDS) || containsAny(words, SEARCH_WORDS)) return;

        // 3. Input type.
        int inputType = node.getInputType();
        if (kind == Kind.NONE && isPasswordInputType(inputType)) kind = Kind.PASSWORD;
        if (kind == Kind.NONE && isEmailInputType(inputType)) kind = Kind.USERNAME_DECLARED;

        // 4. Keywords.
        if (kind == Kind.NONE && containsAny(words, PASSWORD_WORDS)) kind = Kind.PASSWORD;
        if (kind == Kind.NONE && containsAny(words, USERNAME_WORDS)) kind = Kind.USERNAME_KEYWORD;

        if (kind == Kind.PASSWORD) {
            if (role == Role.UNKNOWN) role = roleFromWords(words);
            passwords.add(new PasswordField(id, value, role));
            return;
        }

        int score = kind == Kind.USERNAME_DECLARED ? 3 : kind == Kind.USERNAME_KEYWORD ? 2 : 0;
        if (score > usernameScore) {
            usernameId = id;
            usernameValue = value;
            usernameScore = score;
        }

        // 5. Position. Plain text or phone fields only, and only before the first password.
        if (passwords.isEmpty() && score == 0 && isPlainTextOrPhone(inputType)) {
            textBeforePassword = id;
            textBeforePasswordValue = value;
        }
    }

    /**
     * Fills in roles the page did not declare, from the order of the fields.
     * Undeclared pairs are a sign-up form (new + confirm) unless the two values typed into
     * them differ, which only a change form (current + new) produces.
     */
    private void resolveRoles() {
        int n = passwords.size();
        if (n == 0) return;

        boolean anyDeclared = false;
        for (PasswordField f : passwords) anyDeclared |= f.declared != Role.UNKNOWN;

        if (!anyDeclared) {
            if (n == 1) {
                passwords.get(0).role = Role.CURRENT;
            } else if (n == 2) {
                String a = nonEmpty(passwords.get(0).value), b = nonEmpty(passwords.get(1).value);
                boolean change = a != null && b != null && !a.equals(b);
                passwords.get(0).role = change ? Role.CURRENT : Role.NEW;
                passwords.get(1).role = change ? Role.NEW : Role.CONFIRM;
            } else {
                passwords.get(0).role = Role.CURRENT;
                passwords.get(1).role = Role.NEW;
                for (int i = 2; i < n; i++) passwords.get(i).role = Role.CONFIRM;
            }
            return;
        }

        for (int i = 0; i < n; i++) {
            PasswordField f = passwords.get(i);
            if (f.role != Role.UNKNOWN) continue;
            Role prev = i > 0 ? passwords.get(i - 1).role : null;
            if (prev == Role.CURRENT) {
                f.role = Role.NEW;
            } else if (prev == Role.NEW || prev == Role.CONFIRM) {
                f.role = Role.CONFIRM;
            } else {
                Role next = nextDeclared(i);
                f.role = next == Role.NEW ? Role.CURRENT
                        : next == Role.CONFIRM ? Role.NEW
                        : Role.CURRENT;
            }
        }
    }

    @Nullable
    private Role nextDeclared(int from) {
        for (int i = from + 1; i < passwords.size(); i++) {
            if (passwords.get(i).declared != Role.UNKNOWN) return passwords.get(i).declared;
        }
        return null;
    }

    private static Role roleFromWords(String words) {
        // Confirm first: "confirm new password" is the confirm field, not the new one.
        if (containsAny(words, CONFIRM_WORDS)) return Role.CONFIRM;
        if (containsAny(words, CURRENT_WORDS)) return Role.CURRENT;
        if (containsAny(words, NEW_WORDS)) return Role.NEW;
        return Role.UNKNOWN;
    }

    // ------------------------------------------------------------------ input types

    /**
     * Variations are values inside a mask, not bit flags. The old check
     * {@code (type & TYPE_TEXT_VARIATION_PASSWORD) != 0} also matched web email fields.
     */
    private static boolean isPasswordInputType(int type) {
        int cls = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        }
        return cls == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private static boolean isEmailInputType(int type) {
        int variation = type & InputType.TYPE_MASK_VARIATION;
        return (type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
    }

    private static boolean isPlainTextOrPhone(int type) {
        int cls = type & InputType.TYPE_MASK_CLASS;
        if (cls == InputType.TYPE_CLASS_PHONE) return true;
        if (cls != InputType.TYPE_CLASS_TEXT) return false;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_NORMAL
                || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
    }

    // ------------------------------------------------------------------ words

    private enum Kind { NONE, PASSWORD, USERNAME_DECLARED, USERNAME_KEYWORD }

    private static final String[] PASSWORD_WORDS = {
            "password", "passwd", "pass", "pwd", "passcode", "passwort", "contraseña",
            "contrasena", "mot de passe", "senha", "wachtwoord", "parola", "пароль",
            "パスワード", "密码", "密碼", "비밀번호", "kata sandi", "mật khẩu", "รหัสผ่าน",
    };
    private static final String[] USERNAME_WORDS = {
            "user", "email", "e-mail", "mail", "login", "account", "phone", "mobile",
            "userid", "member", "customer", "nickname", "usuario", "correo", "benutzer",
            "identifiant", "utilisateur", "konto", "username",
    };
    private static final String[] NEW_WORDS = {
            "new", "neu", "nuev", "nouv", "novo", "create", "choose", "register", "signup",
            "sign_up", "sign-up", "reset",
    };
    private static final String[] CONFIRM_WORDS = {
            "confirm", "repeat", "retype", "re-type", "re_type", "reenter", "re-enter",
            "re_enter", "again", "verify", "verif", "bestätig", "wiederhol", "confirma",
    };
    private static final String[] CURRENT_WORDS = {
            "current", "old", "existing", "aktuell", "actual", "anterior", "ancien",
    };
    private static final String[] OTP_WORDS = {
            "otp", "one-time", "onetime", "2fa", "totp", "verification code", "verificationcode",
            "sms code", "smscode", "captcha", "security code",
    };
    private static final String[] SEARCH_WORDS = { "search", "query", "find " };

    private static boolean isOneTimeCode(String s) {
        return s.contains("otp") || s.contains("one-time-code") || s.contains("smsotp");
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    @Nullable
    private static String textValue(AssistStructure.ViewNode node) {
        AutofillValue value = node.getAutofillValue();
        if (value == null || !value.isText()) return null;
        return value.getTextValue().toString();
    }

    @Nullable
    private static String nonEmpty(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }

    private static String safe(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}

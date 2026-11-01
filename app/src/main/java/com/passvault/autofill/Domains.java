package com.passvault.autofill;

import androidx.annotation.Nullable;

import com.passvault.data.PasswordEntry;
import com.passvault.ui.SiteIcons;

/** Host handling for autofill matching. Same normalisation the site logos use. */
final class Domains {

    private Domains() { }

    /** "https://www.PayPal.com/ph?x" → "paypal.com"; null if it is not a public hostname. */
    @Nullable
    static String normalize(@Nullable String raw) {
        String host = SiteIcons.normalizeHost(raw);
        if (host == null) return null;
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /**
     * The entry's site for matching: its domain field, or its name when that is a domain
     * ("netflix.com"), which is how many people label entries.
     */
    @Nullable
    static String of(PasswordEntry entry) {
        String domain = normalize(entry.webDomain);
        if (domain != null) return domain;
        if (entry.siteName.indexOf('.') > 0 && entry.siteName.indexOf(' ') < 0) {
            return normalize(entry.siteName);
        }
        return null;
    }

    /** "accounts.google.com" matches a saved "google.com"; the reverse does not. */
    static boolean matches(@Nullable String requestHost, @Nullable String savedHost) {
        if (requestHost == null || savedHost == null) return false;
        return requestHost.equals(savedHost) || requestHost.endsWith("." + savedHost);
    }
}

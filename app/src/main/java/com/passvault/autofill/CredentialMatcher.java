package com.passvault.autofill;

import androidx.annotation.Nullable;

import com.passvault.data.PasswordEntry;

import java.util.ArrayList;
import java.util.List;

/** Which saved logins belong to the screen that asked. */
final class CredentialMatcher {

    private CredentialMatcher() { }

    /**
     * A web page (browser or WebView) matches by domain only. A native app screen matches by
     * the package the login was saved from, or linked to after the user picked it there.
     */
    static List<PasswordEntry> forSite(List<PasswordEntry> all, @Nullable String packageName,
                                       @Nullable String webDomain) {
        List<PasswordEntry> out = new ArrayList<>();
        for (PasswordEntry entry : all) {
            if (webDomain != null) {
                if (Domains.matches(webDomain, Domains.of(entry))) out.add(entry);
            } else if (packageName != null && packageName.equals(entry.packageName)) {
                out.add(entry);
            }
        }
        return out;
    }
}

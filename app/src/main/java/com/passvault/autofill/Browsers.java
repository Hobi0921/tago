package com.passvault.autofill;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Browsers PassVault knows, and the view id of each one's address bar.
 *
 * In compatibility mode (res/xml/autofill_service.xml) a browser's page structure often comes
 * without a web domain, so the domain is read from the address bar instead. Only these
 * packages are trusted for that: any other app could put "bank.com" in a text field.
 */
final class Browsers {

    private static final Map<String, String> URL_BAR_IDS = new HashMap<>();

    static {
        for (String pkg : new String[]{
                "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
                "org.chromium.chrome", "com.brave.browser", "com.brave.browser_beta",
                "com.microsoft.emmx", "com.kiwibrowser.browser", "com.vivaldi.browser",
                "com.ecosia.android"}) {
            URL_BAR_IDS.put(pkg, "url_bar");
        }
        URL_BAR_IDS.put("com.sec.android.app.sbrowser", "location_bar_edit_text");
        URL_BAR_IDS.put("com.sec.android.app.sbrowser.beta", "location_bar_edit_text");
        URL_BAR_IDS.put("com.opera.browser", "url_field");
        URL_BAR_IDS.put("com.opera.browser.beta", "url_field");
        URL_BAR_IDS.put("com.opera.mini.native", "url_field");
        URL_BAR_IDS.put("com.duckduckgo.mobile.android", "omnibarTextInput");
        URL_BAR_IDS.put("com.yandex.browser", "bro_omnibar_address_title_text");
        URL_BAR_IDS.put("org.mozilla.firefox", "mozac_browser_toolbar_url_view");
        URL_BAR_IDS.put("org.mozilla.firefox_beta", "mozac_browser_toolbar_url_view");
        URL_BAR_IDS.put("org.mozilla.fenix", "mozac_browser_toolbar_url_view");
        URL_BAR_IDS.put("org.mozilla.focus", "mozac_browser_toolbar_url_view");
    }

    private Browsers() { }

    static boolean isBrowser(@Nullable String pkg) {
        return pkg != null && URL_BAR_IDS.containsKey(pkg);
    }

    @Nullable
    static String urlBarId(@Nullable String pkg) {
        return pkg == null ? null : URL_BAR_IDS.get(pkg);
    }
}

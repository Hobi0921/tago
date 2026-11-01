package com.passvault.ui; // TODO: change to your package

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.text.BreakIterator;
import java.util.Locale;

/** Fixes #5 and #6: empty optional lines, and initials that survive emoji and lowercase. */
public final class UiText {

    private UiText() {}

    /**
     * First user-perceived character, uppercased. "gmail" -> "G", "🏦 BPI" -> "🏦",
     * "  ébay" -> "É". charAt(0) would split an emoji's surrogate pair into "�".
     */
    public static String initialOf(String name) {
        if (name == null) return "?";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "?";
        BreakIterator it = BreakIterator.getCharacterInstance(Locale.getDefault());
        it.setText(trimmed);
        int end = it.next();
        if (end == BreakIterator.DONE) end = trimmed.length();
        return trimmed.substring(0, end).toUpperCase(Locale.getDefault());
    }

    /** An empty TextView still takes a line of height; hide it instead. */
    public static void setTextOrGone(TextView view, CharSequence text) {
        if (TextUtils.isEmpty(text) || TextUtils.getTrimmedLength(text) == 0) {
            view.setText(null);
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }
}

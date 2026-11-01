package com.passvault.ui;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.passvault.R;
import com.passvault.data.PasswordEntry;
import com.passvault.data.PasswordHistory;
import com.passvault.data.VaultRepository;
import com.passvault.util.PasswordStrengthChecker;
import com.passvault.util.StrengthResult;
import com.passvault.util.TotpGenerator;

import java.util.Date;
import java.util.List;

/**
 * Read view for one entry: copy username, password or 2FA code, reveal, star,
 * see previous passwords, and reach Edit / Delete.
 *
 * Only the entry id is passed in. The entry itself is observed from Room, so the
 * sheet refreshes after an edit and closes itself if the entry is deleted.
 */
public class EntryDetailSheet extends BottomSheetDialogFragment {

    /** Fragment-result key the host listens on to perform delete-with-undo. */
    public static final String RESULT_DELETE = "entry_detail_delete";
    public static final String KEY_ID = "id";

    private static final String ARG_ID = "id";

    /** Fixed length, so the mask does not reveal how long the password is. */
    private static final String MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    public static EntryDetailSheet newInstance(long entryId) {
        EntryDetailSheet sheet = new EntryDetailSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, entryId);
        sheet.setArguments(args);
        return sheet;
    }

    private long entryId;
    private PasswordEntry current;
    private boolean revealed;

    /** Smooth ring + seconds; runs only between onStart and onStop. */
    private Motion.TotpRing totpTimer;
    private boolean started;

    private TextView initial, site, meta, username, password, totp, totpSeconds, notes;
    private ImageButton favorite, reveal;
    private View usernameRow, totpRow, notesRow, historySection;
    private LinearLayout historyList;
    private CircularProgressIndicator totpRing;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return SecureWindows.secure(super.onCreateDialog(savedInstanceState));
    }

    @Override
    public void onStart() {
        super.onStart();
        started = true;
        renderTotp();

        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) return;

        // Open fully rather than half-collapsed: the copy buttons are the point.
        BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // Match the app surface instead of Material's default sheet colour.
        View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (container != null) {
            container.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.surface)));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_entry_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        entryId = requireArguments().getLong(ARG_ID);

        initial = view.findViewById(R.id.detailInitial);
        site = view.findViewById(R.id.detailSite);
        meta = view.findViewById(R.id.detailMeta);
        favorite = view.findViewById(R.id.detailFavorite);
        usernameRow = view.findViewById(R.id.detailUsernameRow);
        username = view.findViewById(R.id.detailUsername);
        password = view.findViewById(R.id.detailPassword);
        reveal = view.findViewById(R.id.detailReveal);
        totpRow = view.findViewById(R.id.detailTotpRow);
        totp = view.findViewById(R.id.detailTotp);
        totpRing = view.findViewById(R.id.detailTotpRing);
        totpSeconds = view.findViewById(R.id.detailTotpSeconds);
        totpTimer = new Motion.TotpRing(totpRing, totpSeconds, TotpGenerator.TIME_STEP_SECONDS,
                this::renderTotp);
        // Last five seconds: warn, so nobody starts typing a code about to expire.
        totpTimer.setOnSecond(left -> totpRing.setIndicatorColor(ContextCompat.getColor(
                requireContext(), left <= 5 ? R.color.warning : R.color.accent)));
        notesRow = view.findViewById(R.id.detailNotesRow);
        notes = view.findViewById(R.id.detailNotes);
        historySection = view.findViewById(R.id.detailHistorySection);
        historyList = view.findViewById(R.id.detailHistoryList);

        VaultRepository repository = VaultRepository.get(requireContext());

        favorite.setOnClickListener(v -> {
            if (current != null) repository.setFavorite(entryId, !current.favorite);
        });

        reveal.setOnClickListener(v -> {
            revealed = !revealed;
            renderPassword();
        });

        view.findViewById(R.id.detailCopyUsername).setOnClickListener(v -> {
            if (current != null) {
                ClipboardHelper.copyPlain(v, current.username, R.string.detail_username_copied);
            }
        });

        view.findViewById(R.id.detailCopyPassword).setOnClickListener(v -> {
            if (current != null) {
                ClipboardHelper.copySecret(v, current.password, R.string.vault_copied);
            }
        });

        view.findViewById(R.id.detailCopyTotp).setOnClickListener(v -> {
            if (current == null || current.totpSecret == null) return;
            String code = TotpGenerator.generate(current.totpSecret);
            if (code != null) ClipboardHelper.copySecret(v, code, R.string.detail_totp_copied);
        });

        view.findViewById(R.id.detailEdit).setOnClickListener(v ->
                EntryEditorDialog.newInstance(entryId)
                        .show(getParentFragmentManager(), "editor"));

        view.findViewById(R.id.detailDelete).setOnClickListener(v -> {
            getParentFragmentManager()
                    .setFragmentResult(RESULT_DELETE, EntryActions.deleteResult(entryId));
            dismiss();
        });

        try {
            repository.observeEntry(entryId).observe(getViewLifecycleOwner(), entry -> {
                if (entry == null) {
                    // Deleted (here or elsewhere). Nothing left to show.
                    dismissAllowingStateLoss();
                    return;
                }
                current = entry;
                render(entry);
            });
            repository.observeHistory(entryId).observe(getViewLifecycleOwner(), this::renderHistory);
        } catch (IllegalStateException locked) {
            dismissAllowingStateLoss();
        }
    }

    // ------------------------------------------------------------------ render

    private void render(PasswordEntry entry) {
        String label = entry.displayLabel();
        Avatars.bind(requireView().findViewById(R.id.detailAvatar), initial,
                requireView().findViewById(R.id.detailIcon), entry.webDomain, label);
        site.setText(label);

        StrengthResult strength = PasswordStrengthChecker.analyse(entry.password);
        meta.setText(buildMeta(strength, entry.passwordUpdatedAt));

        favorite.setImageResource(entry.favorite ? R.drawable.ic_star : R.drawable.ic_star_border);
        favorite.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                entry.favorite ? R.color.caution : R.color.text_muted)));
        favorite.setContentDescription(getString(entry.favorite
                ? R.string.detail_favorite_remove : R.string.detail_favorite_add));

        boolean hasUsername = !TextUtils.isEmpty(entry.username);
        usernameRow.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
        username.setText(entry.username);

        renderPassword();

        boolean hasTotp = !TextUtils.isEmpty(entry.totpSecret);
        totpRow.setVisibility(hasTotp ? View.VISIBLE : View.GONE);
        renderTotp();

        boolean hasNotes = !TextUtils.isEmpty(entry.notes);
        notesRow.setVisibility(hasNotes ? View.VISIBLE : View.GONE);
        notes.setText(entry.notes);
    }

    /** "Strong · changed 3 days ago", with only the rating in the rating's colour. */
    private CharSequence buildMeta(StrengthResult strength, long changedAt) {
        long now = System.currentTimeMillis();
        CharSequence when = now - changedAt < DateUtils.MINUTE_IN_MILLIS
                ? getString(R.string.detail_changed_just_now)
                : DateUtils.getRelativeTimeSpanString(changedAt, now, DateUtils.MINUTE_IN_MILLIS);

        String text = getString(R.string.detail_changed, strength.level.label, when);
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_muted)),
                0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(strength.level.color(requireContext())),
                0, strength.level.label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private void renderPassword() {
        if (current == null) return;
        password.setText(revealed ? current.password : MASK);
        reveal.setImageResource(revealed ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        reveal.setContentDescription(getString(revealed ? R.string.detail_hide : R.string.detail_reveal));
    }

    /**
     * Shows the current code and keeps the ring running while the sheet is started. The ring
     * calls this again at each new 30s window, so the code changes exactly when it expires.
     */
    private void renderTotp() {
        if (totpTimer == null) return;
        if (current == null || TextUtils.isEmpty(current.totpSecret) || getView() == null) {
            totpTimer.stop();
            return;
        }

        String code = TotpGenerator.generate(current.totpSecret);
        if (code == null) {
            totpTimer.stop();
            totp.setText(R.string.detail_totp_invalid);
            totp.setTextSize(13);
            totpRing.setVisibility(View.GONE);
            totpSeconds.setVisibility(View.GONE);
            return;
        }

        totp.setTextSize(24);
        totp.setText(code.length() == 6 ? code.substring(0, 3) + " " + code.substring(3) : code);
        totpRing.setVisibility(View.VISIBLE);
        totpSeconds.setVisibility(View.VISIBLE);
        if (started) totpTimer.start();   // no-op if already running
    }

    private void renderHistory(List<PasswordHistory> history) {
        historyList.removeAllViews();
        boolean any = history != null && !history.isEmpty();
        historySection.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) return;

        java.text.DateFormat format = DateFormat.getMediumDateFormat(requireContext());
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (PasswordHistory item : history) {
            View row = inflater.inflate(R.layout.item_history, historyList, false);
            ((TextView) row.findViewById(R.id.historyDate)).setText(
                    getString(R.string.detail_history_item, format.format(new Date(item.replacedAt))));
            row.findViewById(R.id.historyCopy).setOnClickListener(v ->
                    ClipboardHelper.copySecret(v, item.password, R.string.detail_old_copied));
            historyList.addView(row);
        }
    }

    /**
     * Leaving the app (or the screen turning off) re-masks the password and stops the 2FA
     * ring. Before, a revealed password was still showing on return, and the ticker kept
     * running in the background.
     */
    @Override
    public void onStop() {
        started = false;
        if (totpTimer != null) totpTimer.stop();
        if (revealed) {
            revealed = false;
            renderPassword();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (totpTimer != null) totpTimer.stop();
        super.onDestroyView();
    }
}

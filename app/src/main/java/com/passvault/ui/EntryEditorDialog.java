package com.passvault.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.passvault.R;
import com.passvault.data.AppPrefs;
import com.passvault.data.PasswordEntry;
import com.passvault.data.VaultRepository;
import com.passvault.util.Base32;
import com.passvault.util.PasswordGenerator;
import com.passvault.util.PasswordStrengthChecker;
import com.passvault.util.StrengthResult;
import com.passvault.util.TotpGenerator;

/**
 * Full-screen add/edit. An id of 0 means "new entry".
 *
 * Only the id goes into the arguments. Arguments are part of the saved-instance
 * Bundle, and the old version put the plaintext password there.
 */
public class EntryEditorDialog extends AppCompatDialogFragment {

    private static final String ARG_ID = "id";
    private static final String STATE_INITIAL_HASH = "initial_hash";

    public static EntryEditorDialog newInstance(long entryId) {
        EntryEditorDialog dialog = new EntryEditorDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, entryId);
        dialog.setArguments(args);
        return dialog;
    }

    /** New entry prefilled from the generator. The password is handed over in memory. */
    public static EntryEditorDialog newInstanceWithPassword(String password) {
        EntryEditorDialog dialog = newInstance(0L);
        dialog.prefillPassword = password;
        return dialog;
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    private long entryId;
    private String prefillPassword;
    /**
     * Hash of the fields as first shown, for the "discard changes?" check. A hash
     * rather than the text itself so no secret ends up in saved state.
     */
    private int initialHash;

    private MaterialToolbar toolbar;
    private TextInputLayout siteLayout, passwordLayout, totpLayout;
    private EditText site, username, password, domain, totpSecret, notes;

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_PassVault_FullScreenDialog);
        // Back and outside touches go through attemptClose() instead.
        setCancelable(false);
        entryId = requireArguments().getLong(ARG_ID, 0L);
        if (savedInstanceState != null) {
            initialHash = savedInstanceState.getInt(STATE_INITIAL_HASH);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = SecureWindows.secure(super.onCreateDialog(savedInstanceState));
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) attemptClose();
            return true;
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.surface));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_entry_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        toolbar = view.findViewById(R.id.editorToolbar);
        siteLayout = view.findViewById(R.id.editorSiteLayout);
        passwordLayout = view.findViewById(R.id.editorPasswordLayout);
        totpLayout = view.findViewById(R.id.editorTotpLayout);

        site = view.findViewById(R.id.editorSite);
        username = view.findViewById(R.id.editorUsername);
        password = view.findViewById(R.id.editorPassword);
        domain = view.findViewById(R.id.editorDomain);
        totpSecret = view.findViewById(R.id.editorTotp);
        notes = view.findViewById(R.id.editorNotes);

        toolbar.setTitle(isEditing() ? R.string.editor_edit_title : R.string.editor_add_title);
        toolbar.setNavigationOnClickListener(v -> attemptClose());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                save();
                return true;
            }
            return false;
        });

        // Uses the generator tab's saved settings rather than library defaults, so
        // one Generate button does not produce a different shape of password.
        view.findViewById(R.id.editorGenerate).setOnClickListener(v -> {
            AppPrefs prefs = new AppPrefs(requireContext());
            password.setText(prefs.generatorUsesWords()
                    ? PasswordGenerator.generatePassphrase(prefs.wordCount(), prefs.appendNumber())
                    : PasswordGenerator.generate(prefs.generatorOptions()));
        });

        // Live rating under the password, same scorer as the list and generator.
        password.addTextChangedListener(new SimpleWatcher(() -> {
            passwordLayout.setError(null);
            renderStrength();
        }));
        site.addTextChangedListener(new SimpleWatcher(() -> siteLayout.setError(null)));
        username.addTextChangedListener(new SimpleWatcher(() -> siteLayout.setError(null)));
        totpSecret.addTextChangedListener(new SimpleWatcher(() -> totpLayout.setError(null)));

        if (savedInstanceState != null) {
            // Rotation: the EditTexts restore their own text.
            renderStrength();
            return;
        }

        if (isEditing()) {
            VaultRepository.get(requireContext()).getEntry(entryId, entry -> main.post(() -> {
                if (!isAdded()) return;
                if (entry == null) {
                    dismissAllowingStateLoss();
                    return;
                }
                populate(entry);
            }));
        } else {
            if (prefillPassword != null) password.setText(prefillPassword);
            prefillPassword = null;
            initialHash = currentHash();
            site.requestFocus();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_INITIAL_HASH, initialHash);
    }

    // ----------------------------------------------------------------- populate

    private boolean isEditing() {
        return entryId != 0L;
    }

    private void populate(PasswordEntry entry) {
        site.setText(entry.siteName);
        username.setText(entry.username);
        password.setText(entry.password);
        domain.setText(entry.webDomain);
        totpSecret.setText(entry.totpSecret);
        notes.setText(entry.notes);
        initialHash = currentHash();
    }

    private void renderStrength() {
        String pw = password.getText().toString();
        if (pw.isEmpty()) {
            passwordLayout.setHelperText(null);
            return;
        }
        StrengthResult result = PasswordStrengthChecker.analyse(pw);
        passwordLayout.setHelperText(
                getString(R.string.editor_strength, result.level.label, result.crackTime));
        passwordLayout.setHelperTextColor(
                android.content.res.ColorStateList.valueOf(result.level.color(requireContext())));
    }

    // --------------------------------------------------------------------- save

    private void save() {
        String siteText = site.getText().toString().trim();
        String userText = username.getText().toString().trim();
        String pw = password.getText().toString();
        String domainText = domain.getText().toString().trim();
        String totpText = totpSecret.getText().toString().trim();
        String notesText = notes.getText().toString().trim();

        // Validate everything, then focus the first problem, rather than
        // dismissing and throwing away what was typed.
        View firstError = null;

        if (pw.isEmpty()) {
            passwordLayout.setError(getString(R.string.editor_error_password));
            firstError = password;
        }

        if (siteText.isEmpty() && userText.isEmpty()) {
            siteLayout.setError(getString(R.string.editor_error_label));
            if (firstError == null) firstError = site;
        }

        String secret = null;
        if (!totpText.isEmpty()) {
            secret = normaliseTotp(totpText);
            if (!Base32.isValid(secret)) {
                totpLayout.setError(getString(R.string.editor_error_totp));
                if (firstError == null) firstError = totpSecret;
            }
        }

        if (firstError != null) {
            firstError.requestFocus();
            return;
        }

        VaultRepository.Edit edit = new VaultRepository.Edit();
        edit.siteName = siteText;
        edit.username = userText;
        edit.password = pw;
        edit.webDomain = domainText.isEmpty() ? null : normaliseDomain(domainText);
        edit.totpSecret = secret;
        edit.notes = notesText.isEmpty() ? null : notesText;

        VaultRepository repo = VaultRepository.get(requireContext());
        if (isEditing()) {
            repo.applyEdit(entryId, edit, null);
        } else {
            repo.insertEdit(edit, null);
        }
        dismiss();
    }

    /** Accepts a bare key ("JBSW Y3DP ...") or a full otpauth:// link. */
    private String normaliseTotp(String raw) {
        String secret = TotpGenerator.extractSecret(raw);
        return secret == null ? "" : secret.replace(" ", "").replace("-", "").toUpperCase();
    }

    /** Strip scheme, path and www. so "https://www.github.com/login" becomes "github.com". */
    private String normaliseDomain(String raw) {
        String d = raw.toLowerCase()
                .replaceFirst("^https?://", "")
                .replaceFirst("^www\\.", "");
        int slash = d.indexOf('/');
        if (slash > 0) d = d.substring(0, slash);
        return d;
    }

    // -------------------------------------------------------------------- close

    private void attemptClose() {
        if (currentHash() == initialHash) {
            dismiss();
            return;
        }
        SecureWindows.secure(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_discard_title)
                .setMessage(R.string.editor_discard_message)
                .setNegativeButton(R.string.editor_keep_editing, null)
                .setPositiveButton(R.string.editor_discard, (d, w) -> dismiss())
                .create())
                .show();
    }

    private int currentHash() {
        if (site == null) return 0;
        String joined = site.getText() + "\u0000" + username.getText() + "\u0000"
                + password.getText() + "\u0000" + domain.getText() + "\u0000"
                + totpSecret.getText() + "\u0000" + notes.getText();
        return joined.hashCode();
    }

    /** TextWatcher that only cares that the text changed. */
    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable s) { onChange.run(); }
    }
}

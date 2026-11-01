package com.passvault.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.passvault.R;

import android.view.View;

/**
 * Collects the backup passphrase. In export mode it asks twice and enforces a
 * minimum length, because a typo here produces a file nobody can ever open.
 */
public class PassphraseDialog extends DialogFragment {

    public interface Listener {
        void onPassphrase(char[] passphrase);
    }

    private static final String ARG_CONFIRM = "confirm";
    private static final int MIN_LENGTH = 12;

    private Listener listener;

    public static PassphraseDialog forExport(Listener listener) {
        return build(true, listener);
    }

    public static PassphraseDialog forImport(Listener listener) {
        return build(false, listener);
    }

    private static PassphraseDialog build(boolean confirm, Listener listener) {
        PassphraseDialog dialog = new PassphraseDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_CONFIRM, confirm);
        dialog.setArguments(args);
        dialog.listener = listener;
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        boolean confirm = requireArguments().getBoolean(ARG_CONFIRM);

        View view = getLayoutInflater().inflate(R.layout.dialog_passphrase, null);
        TextView hint = view.findViewById(R.id.passphraseHint);
        EditText input = view.findViewById(R.id.passphraseInput);
        EditText repeat = view.findViewById(R.id.passphraseConfirm);

        hint.setText(confirm ? R.string.passphrase_export_hint : R.string.passphrase_import_title);
        repeat.setVisibility(confirm ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(confirm ? R.string.passphrase_export_title
                                  : R.string.passphrase_import_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(android.R.string.ok, null) // overridden below
                .create();

        // Overriding the click after show() keeps the dialog open on validation
        // failure, instead of dismissing and losing what was typed.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String first = input.getText().toString();

                    if (confirm) {
                        if (first.length() < MIN_LENGTH) {
                            input.setError(getString(R.string.passphrase_too_short));
                            return;
                        }
                        if (!first.equals(repeat.getText().toString())) {
                            repeat.setError(getString(R.string.passphrase_mismatch));
                            return;
                        }
                    } else if (first.isEmpty()) {
                        input.setError(getString(R.string.passphrase_hint));
                        return;
                    }

                    if (listener != null) listener.onPassphrase(first.toCharArray());
                    dialog.dismiss();
                }));

        // Its own window, so it needs its own FLAG_SECURE.
        return SecureWindows.secure(dialog);
    }
}

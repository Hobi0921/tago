package com.passvault.ui;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.passvault.R;
import com.passvault.data.AppPrefs;
import com.passvault.data.PasswordEntry;
import com.passvault.data.VaultRepository;
import com.passvault.security.VaultExporter;
import com.passvault.security.VaultLock;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Backup, restore, and manual lock. */
public class SettingsFragment extends Fragment {

    private VaultRepository repository;
    private AppPrefs prefs;
    private TextView entryCount, backupStatus, autoLockValue, autofillStatus;
    private View autofillEnable;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Held between choosing the passphrase and the file picker returning. */
    private char[] pendingPassphrase;

    private ActivityResultLauncher<String> createFile;
    private ActivityResultLauncher<String[]> openFile;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createFile = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> { if (uri != null) writeBackup(uri); else clearPassphrase(); });

        openFile = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) readBackup(uri); else clearPassphrase(); });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = VaultRepository.get(requireContext());
        prefs = new AppPrefs(requireContext());

        entryCount = view.findViewById(R.id.settingsEntryCount);
        backupStatus = view.findViewById(R.id.settingsBackupStatus);
        autoLockValue = view.findViewById(R.id.settingsAutoLockValue);
        autofillStatus = view.findViewById(R.id.settingsAutofillStatus);
        autofillEnable = view.findViewById(R.id.settingsAutofillEnable);

        view.findViewById(R.id.settingsAutoLockRow).setOnClickListener(v -> pickAutoLock());
        autofillEnable.setOnClickListener(v -> openAutofillSettings());

        Button export = view.findViewById(R.id.settingsExport);
        export.setOnClickListener(v ->
                PassphraseDialog.forExport(passphrase -> {
                    pendingPassphrase = passphrase;
                    createFile.launch(suggestedFilename());
                }).show(getParentFragmentManager(), "export"));

        final Button restore = view.findViewById(R.id.settingsImport);
        restore.setOnClickListener(v ->
                PassphraseDialog.forImport(passphrase -> {
                    pendingPassphrase = passphrase;
                    openFile.launch(new String[]{"application/json", "*/*"});
                }).show(getParentFragmentManager(), "import"));

        Button lock = view.findViewById(R.id.settingsLockNow);
        // Goes through the activity so the screen actually leaves the vault. Calling
        // VaultLock directly closed the database but left Settings on screen.
        lock.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).lockNow();
            } else {
                VaultLock.get().lock();
            }
        });

        refreshCount();
        renderBackupStatus();
        renderAutoLock();

        // Arrives here straight after the unlock screen rebuilt the vault, so the
        // restore picker opens without the user having to find it.
        if (savedInstanceState == null && requireActivity().getIntent()
                .getBooleanExtra(UnlockActivity.EXTRA_OPEN_RESTORE, false)) {
            requireActivity().getIntent().removeExtra(UnlockActivity.EXTRA_OPEN_RESTORE);
            // Posted, not called inline: this fragment is still being added, and
            // showing a dialog from inside that transaction is not allowed.
            main.post(() -> { if (isAdded()) restore.performClick(); });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Autofill is switched on in system settings, so re-check on the way back.
        renderAutofill();
    }

    // ------------------------------------------------------------- status lines

    private void renderBackupStatus() {
        long last = prefs.lastBackupAt();
        boolean never = last == 0L;

        backupStatus.setText(never
                ? getString(R.string.settings_backup_never)
                : getString(R.string.settings_backup_last, DateUtils.getRelativeTimeSpanString(
                        last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)));
        backupStatus.setTextColor(color(never ? R.color.warning : R.color.text_muted));
    }

    private void renderAutoLock() {
        autoLockValue.setText(autoLockLabel(prefs.autoLockMs()));
    }

    private String autoLockLabel(long ms) {
        int index = 0;
        for (int i = 0; i < AppPrefs.AUTO_LOCK_CHOICES.length; i++) {
            if (AppPrefs.AUTO_LOCK_CHOICES[i] == ms) index = i;
        }
        return getResources().getStringArray(R.array.settings_autolock_labels)[index];
    }

    private void pickAutoLock() {
        long currentMs = prefs.autoLockMs();
        int checked = 0;
        for (int i = 0; i < AppPrefs.AUTO_LOCK_CHOICES.length; i++) {
            if (AppPrefs.AUTO_LOCK_CHOICES[i] == currentMs) checked = i;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_autolock)
                .setSingleChoiceItems(R.array.settings_autolock_labels, checked, (dialog, which) -> {
                    long ms = AppPrefs.AUTO_LOCK_CHOICES[which];
                    prefs.setAutoLockMs(ms);
                    // Applies to the running process immediately, not just next launch.
                    VaultLock.get().setTimeoutMs(ms);
                    renderAutoLock();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void renderAutofill() {
        if (autofillStatus == null) return;

        boolean supported = AutofillSetup.isSupported(requireContext());
        boolean enabled = AutofillSetup.isEnabled(requireContext());

        autofillStatus.setText(enabled
                ? R.string.settings_autofill_on : R.string.settings_autofill_off);
        autofillStatus.setTextColor(color(enabled ? R.color.good : R.color.text_muted));
        autofillEnable.setVisibility(!enabled && supported ? View.VISIBLE : View.GONE);
    }

    private void openAutofillSettings() {
        if (!AutofillSetup.open(requireContext())) {
            toast(getString(R.string.settings_autofill_unsupported));
        }
    }

    private int color(int resId) {
        return ContextCompat.getColor(requireContext(), resId);
    }

    private String suggestedFilename() {
        String stamp = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        // ".pvault.json" stays: it is the backup format, and old files must keep matching.
        return "tago-backup-" + stamp + ".pvault.json";
    }

    // ------------------------------------------------------------------ export

    private void writeBackup(Uri uri) {
        char[] passphrase = pendingPassphrase;
        pendingPassphrase = null;
        if (passphrase == null) return;

        repository.runOnIo(() -> {
            String message;
            try {
                List<PasswordEntry> entries = repository.getAllBlocking();
                try (OutputStream out =
                             requireContext().getContentResolver().openOutputStream(uri)) {
                    VaultExporter.export(entries, passphrase, out);
                }
                prefs.recordBackup();
                message = getString(R.string.export_done);
            } catch (Exception e) {
                message = getString(R.string.export_failed);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
            toast(message);
            main.post(() -> { if (isAdded()) renderBackupStatus(); });
        });
    }

    // ------------------------------------------------------------------ import

    private void readBackup(Uri uri) {
        char[] passphrase = pendingPassphrase;
        pendingPassphrase = null;
        if (passphrase == null) return;

        repository.runOnIo(() -> {
            try {
                VaultExporter.ImportResult result;
                try (InputStream in =
                             requireContext().getContentResolver().openInputStream(uri)) {
                    result = VaultExporter.importVault(in, passphrase);
                }

                // Additive restore. Replacing the vault would make a mistyped
                // filename destructive, so imported entries are appended.
                repository.importAll(result.entries, written -> {
                    toast(getString(R.string.import_done, written));
                    main.post(this::refreshCount);
                });

            } catch (VaultExporter.WrongPassphraseException wrong) {
                toast(getString(R.string.import_wrong_passphrase));
            } catch (Exception e) {
                toast(getString(R.string.import_failed));
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private void clearPassphrase() {
        if (pendingPassphrase != null) {
            Arrays.fill(pendingPassphrase, '\0');
            pendingPassphrase = null;
        }
    }

    private void refreshCount() {
        repository.runOnIo(() -> {
            int n = repository.getAllBlocking().size();
            main.post(() -> {
                if (isAdded()) entryCount.setText(getResources()
                        .getQuantityString(R.plurals.settings_entry_count, n, n));
            });
        });
    }

    /** Tabs are kept alive now, so refresh the count when this one comes back. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && entryCount != null) {
            refreshCount();
            renderBackupStatus();
            renderAutofill();
        }
    }

    private void toast(String message) {
        main.post(() -> {
            View root = getView();
            if (isAdded() && root != null) {
                Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        clearPassphrase();
        super.onDestroy();
    }
}

package com.passvault.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.snackbar.Snackbar;
import com.passvault.R;
import com.passvault.data.VaultRepository;

/**
 * Opening an entry and deleting it with Undo, shared by the vault list and the
 * health findings so both behave identically.
 *
 * The detail sheet cannot run the delete itself: it dismisses first, and its own
 * Snackbar would go with it before Undo could be tapped. It posts a fragment
 * result instead, and the host below does the work.
 */
final class EntryActions {

    private static final String TAG_DETAIL = "detail";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Opens the detail sheet, ignoring a second tap while one is already up. */
    static void open(@NonNull Fragment host, long entryId) {
        FragmentManager fm = host.getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_DETAIL) != null) return;
        EntryDetailSheet.newInstance(entryId).show(fm, TAG_DETAIL);
    }

    /** Call from onViewCreated so this host handles delete requests from its sheet. */
    static void listenForDelete(@NonNull Fragment host) {
        host.getChildFragmentManager().setFragmentResultListener(
                EntryDetailSheet.RESULT_DELETE, host.getViewLifecycleOwner(),
                (key, result) -> delete(host, result.getLong(EntryDetailSheet.KEY_ID)));
    }

    private static void delete(Fragment host, long entryId) {
        VaultRepository repository = VaultRepository.get(host.requireContext());
        repository.deleteWithUndo(entryId, deleted -> MAIN.post(() -> {
            View root = host.getView();
            if (!host.isAdded() || root == null) return;
            Snackbar.make(root,
                            host.getString(R.string.vault_deleted, deleted.entry.displayLabel()),
                            Snackbar.LENGTH_LONG)
                    // Action colour comes from the theme's colorPrimaryInverse, which reads on
                    // the Snackbar's inverse surface; plain accent did not.
                    .setAction(R.string.action_undo, v -> repository.restore(deleted))
                    .show();
        }));
    }

    /** Result payload helper, kept here so the sheet and hosts agree on the shape. */
    static Bundle deleteResult(long entryId) {
        Bundle bundle = new Bundle();
        bundle.putLong(EntryDetailSheet.KEY_ID, entryId);
        return bundle;
    }

    private EntryActions() { }
}

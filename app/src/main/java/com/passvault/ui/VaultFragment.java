package com.passvault.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.passvault.R;
import com.passvault.data.AppPrefs;
import com.passvault.data.PasswordEntry;
import com.passvault.data.VaultRepository;

import java.util.List;

public class VaultFragment extends Fragment implements EntryAdapter.Listener {

    private static final String TAG_EDITOR = "editor";

    /** Below this, an unbacked-up vault is not worth interrupting anyone about. */
    private static final int BACKUP_REMINDER_MIN_ENTRIES = 3;

    private VaultRepository repository;
    private AppPrefs prefs;
    private EntryAdapter adapter;
    private LiveData<List<PasswordEntry>> current;

    private RecyclerView list;
    private View emptyState, noResults, backupBanner, autofillCard;
    private TextView noResultsTitle, emptyDetail;

    /** Entries in the unfiltered vault, or -1 before the first load. Drives the backup nudge. */
    private int entryCount = -1;

    /** Set when "Turn on" opened the system screen, so returning can confirm it worked. */
    private boolean awaitingAutofill;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vault, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = VaultRepository.get(requireContext());
        prefs = new AppPrefs(requireContext());
        adapter = new EntryAdapter(this);

        emptyState = view.findViewById(R.id.vaultEmpty);
        noResults = view.findViewById(R.id.vaultNoResults);
        noResultsTitle = view.findViewById(R.id.vaultNoResultsTitle);
        backupBanner = view.findViewById(R.id.vaultBackupBanner);
        autofillCard = view.findViewById(R.id.vaultAutofillCard);
        emptyDetail = view.findViewById(R.id.vaultEmptyDetail);

        view.findViewById(R.id.vaultBackupAction).setOnClickListener(v -> goToSettings());
        view.findViewById(R.id.vaultBackupDismiss).setOnClickListener(v -> {
            prefs.dismissBackupReminder();
            Motion.beginLayoutChange((ViewGroup) backupBanner.getParent());
            renderNudges();
        });

        view.findViewById(R.id.vaultAutofillOn).setOnClickListener(v -> {
            awaitingAutofill = AutofillSetup.open(requireContext());
            if (!awaitingAutofill) {
                Snackbar.make(requireView(), R.string.vault_autofill_unavailable,
                        Snackbar.LENGTH_LONG).show();
            }
        });
        view.findViewById(R.id.vaultAutofillDismiss).setOnClickListener(v -> {
            prefs.dismissAutofillNudge();
            Motion.beginLayoutChange((ViewGroup) autofillCard.getParent());
            renderNudges();
        });

        list = view.findViewById(R.id.vaultList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.vaultAdd).setOnClickListener(v -> openEditor());
        view.findViewById(R.id.vaultEmptyAdd).setOnClickListener(v -> openEditor());
        view.findViewById(R.id.vaultEmptyRestore).setOnClickListener(v -> goToSettings());

        EditText search = view.findViewById(R.id.vaultSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                observe(s.toString().trim());
            }
        });

        EntryActions.listenForDelete(this);

        observe(search.getText().toString().trim());
        renderBreachBanner();
        renderNudges();
    }

    /** Autofill is switched on in a system screen, so re-check every time we come back. */
    @Override
    public void onResume() {
        super.onResume();
        if (awaitingAutofill && AutofillSetup.isEnabled(requireContext())) {
            awaitingAutofill = false;
            Motion.beginLayoutChange((ViewGroup) autofillCard.getParent());
            Snackbar.make(requireView(), R.string.vault_autofill_now_on, Snackbar.LENGTH_LONG).show();
        }
        renderNudges();
    }

    private void observe(String query) {
        if (current != null) current.removeObservers(getViewLifecycleOwner());

        // Filtering runs per keystroke: no item animations while a query is typed.
        Motion.setSearching(list, query);

        current = query.isEmpty() ? repository.observeAll() : repository.search(query);
        current.observe(getViewLifecycleOwner(), entries -> {
            adapter.submitList(entries);
            renderEmpty(entries.isEmpty(), query);
            // Only count against the unfiltered list; a search result says nothing
            // about how much there is to lose.
            if (query.isEmpty()) {
                entryCount = entries.size();
                renderNudges();
            }
        });
    }

    /**
     * Empty vault gets the invitation with Add and Restore; an empty search gets its own view
     * that names the query. Showing the vault one for a search would say the vault is empty.
     */
    private void renderEmpty(boolean empty, String query) {
        boolean searching = !query.isEmpty();
        emptyState.setVisibility(empty && !searching ? View.VISIBLE : View.GONE);
        noResults.setVisibility(empty && searching ? View.VISIBLE : View.GONE);
        if (empty && searching) {
            noResultsTitle.setText(getString(R.string.vault_no_results_title, query));
        }
    }

    /** Count from the last breach check, which the Health tab saves. Hidden at 0 or unchecked. */
    private void renderBreachBanner() {
        View root = getView();
        if (root == null) return;
        int breached = Math.max(0, HealthFragment.lastBreachedCount(requireContext()));
        BreachBanner.bind(root, breached, v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).selectTab(R.id.nav_health);
            }
        });
    }

    private void goToSettings() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).selectTab(R.id.nav_settings);
        }
    }

    /**
     * The two nudges above the list, one at a time. Autofill comes first: until it is on, Tago
     * is a notebook you copy from. The backup reminder waits until autofill is on or dismissed,
     * and only appears once the vault holds enough to be worth losing.
     */
    private void renderNudges() {
        if (getView() == null) return;
        boolean autofillOn = AutofillSetup.isEnabled(requireContext());
        boolean askAutofill = AutofillSetup.isSupported(requireContext())
                && !autofillOn && !prefs.autofillNudgeDismissed();
        boolean askBackup = !askAutofill
                && prefs.lastBackupAt() == 0L
                && !prefs.backupReminderDismissed()
                && entryCount >= BACKUP_REMINDER_MIN_ENTRIES;

        autofillCard.setVisibility(askAutofill ? View.VISIBLE : View.GONE);
        backupBanner.setVisibility(askBackup ? View.VISIBLE : View.GONE);

        // With autofill on, the quickest way to fill an empty vault is to sign in somewhere.
        emptyDetail.setText(autofillOn ? R.string.vault_empty_detail_autofill : R.string.vault_empty_detail);
    }

    /**
     * Coming back from Settings after a backup should clear the reminder, and coming back
     * from Health after a breach check should update the alert.
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden || getView() == null) return;
        renderNudges();
        renderBreachBanner();
    }

    private void openEditor() {
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentByTag(TAG_EDITOR) != null) return;
        EntryEditorDialog.newInstance(0L).show(fm, TAG_EDITOR);
    }

    // -------------------------------------------------------- adapter callbacks

    @Override
    public void onOpen(PasswordEntry entry) {
        EntryActions.open(this, entry.id);
    }

    @Override
    public void onCopy(PasswordEntry entry) {
        ClipboardHelper.copySecret(requireView(), entry.password, R.string.vault_copied);
    }
}

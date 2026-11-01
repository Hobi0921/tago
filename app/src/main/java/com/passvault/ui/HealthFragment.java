package com.passvault.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.passvault.R;
import com.passvault.audit.VaultAudit;
import com.passvault.audit.VaultIssue;
import com.passvault.data.PasswordEntry;
import com.passvault.data.VaultRepository;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vault health. Offline checks run on open; the breach check is explicit because
 * it makes network calls and the person should choose when that happens.
 */
public class HealthFragment extends Fragment {

    private VaultRepository repository;
    private TextView verdict, offline;
    private LinearLayout findings;
    private Button checkBreaches;
    private View root;

    private final Handler main = new Handler(Looper.getMainLooper());

    /** Beyond this the chip rows stop being scannable, so the rest become a count. */
    private static final int MAX_CHIPS = 8;

    /** True while the displayed report includes breach results. */
    private boolean breachChecked;

    /**
     * Breach results from the last explicit check, kept while the vault is edited.
     * An offline re-audit (after an edit) has no breach data, so without this the
     * Compromised stat would flip back to "–" every time you fix something.
     */
    private int lastBreachedCount = -1;

    /** The ring sweeps and counts up on the first render of this view only. */
    private boolean animateNext;

    /** First finding row per kind, so tapping a stat can scroll to it. */
    private final Map<VaultIssue.Kind, View> firstRowByKind = new EnumMap<>(VaultIssue.Kind.class);

    private static final String PREFS = "health";
    private static final String KEY_BREACHED = "last_breached_count";

    /** For the vault's breach alert card: -1 if no breach check has ever completed. */
    public static int lastBreachedCount(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BREACHED, -1);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_health, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = VaultRepository.get(requireContext());
        root = view;
        animateNext = true;
        lastBreachedCount = lastBreachedCount(requireContext());

        verdict = view.findViewById(R.id.healthVerdict);
        offline = view.findViewById(R.id.healthOffline);
        findings = view.findViewById(R.id.healthFindings);
        checkBreaches = view.findViewById(R.id.healthCheckBreaches);

        checkBreaches.setOnClickListener(v -> runAudit(true));

        // A number tells you a problem exists; a tap takes you to the thing you must fix.
        HealthHeader.onStatClick(view, R.id.healthCellCompromised, v -> {
            if (firstRowByKind.containsKey(VaultIssue.Kind.BREACHED)) {
                scrollTo(VaultIssue.Kind.BREACHED);
            } else if (checkBreaches.isEnabled()) {
                // Not checked yet, or the shown report is an offline re-audit with no breach
                // rows in it: the tap means "show me", so run the check (it's user-initiated).
                runAudit(true);
            }
        });
        HealthHeader.onStatClick(view, R.id.healthCellWeak, v -> scrollTo(VaultIssue.Kind.WEAK));
        HealthHeader.onStatClick(view, R.id.healthCellReused, v -> scrollTo(VaultIssue.Kind.REUSED));
        View safeCell = view.findViewById(R.id.healthCellSafe);
        safeCell.setClickable(false);
        safeCell.setBackground(null);   // nothing to fix there, so no ripple promising an action

        // Deleting from a finding's detail sheet needs the same Undo path the
        // vault list uses.
        EntryActions.listenForDelete(this);

        // Re-audit whenever the vault changes, instead of only on first open. Edits
        // made in the vault tab, or from a finding's own detail sheet, show up here
        // without the user having to come back twice.
        try {
            repository.observeAll().observe(getViewLifecycleOwner(), entries -> runAudit(false));
        } catch (IllegalStateException locked) {
            // Vault locked; the activity is already on its way to the unlock screen.
        }
    }

    private void runAudit(boolean includeBreachCheck) {
        // An offline run produces a report with no breach data in it, so the
        // button has to come back rather than claim the last results still hold.
        if (!includeBreachCheck) breachChecked = false;

        if (includeBreachCheck) {
            checkBreaches.setEnabled(false);
            checkBreaches.setText(R.string.health_checking);
        }

        repository.runOnIo(() -> {
            List<PasswordEntry> entries = repository.getAllBlocking();
            VaultAudit.Report report = VaultAudit.analyse(entries, includeBreachCheck);

            main.post(() -> {
                if (!isAdded()) return;
                if (includeBreachCheck && !report.breachCheckFailed) {
                    breachChecked = true;
                    lastBreachedCount = distinctEntries(report, VaultIssue.Kind.BREACHED);
                    requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putInt(KEY_BREACHED, lastBreachedCount).apply();
                }
                render(report);
                checkBreaches.setEnabled(true);
                checkBreaches.setText(R.string.health_check_breaches);
            });
        });
    }

    private void render(VaultAudit.Report report) {
        // Header: ring + score + the four stats. Counts are distinct entries, so one login
        // that is both weak and reused counts once in each, never twice in the same stat.
        int weak = distinctEntries(report, VaultIssue.Kind.WEAK);
        int reused = distinctEntries(report, VaultIssue.Kind.REUSED);
        int compromised = breachChecked
                ? distinctEntries(report, VaultIssue.Kind.BREACHED)
                : lastBreachedCount;               // -1 shows "–" until a check has run
        int safe = Math.max(0, report.totalEntries - entriesWithAnyIssue(report));

        HealthHeader.bind(root, report.healthScore, compromised, weak, reused, safe,
                colorFor(report.healthScore), animateNext);
        animateNext = false;

        int offlineVisibility = report.breachCheckFailed ? View.VISIBLE : View.GONE;
        if (offline.getVisibility() != offlineVisibility) {
            Motion.beginLayoutChange((ViewGroup) offline.getParent());
        }
        offline.setVisibility(offlineVisibility);

        findings.removeAllViews();
        firstRowByKind.clear();

        if (report.totalEntries == 0) {
            verdict.setText(R.string.health_empty);
            return;
        }
        if (report.isClean()) {
            verdict.setText(R.string.health_clean);
            return;
        }

        int issueCount = report.issues.size();
        verdict.setText(getString(R.string.health_issue_summary,
                getResources().getQuantityString(R.plurals.health_issue_count, issueCount, issueCount),
                getResources().getQuantityString(R.plurals.health_entry_count,
                        report.totalEntries, report.totalEntries)));

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (VaultIssue issue : report.issues) {
            View row = inflater.inflate(R.layout.item_finding, findings, false);

            ((TextView) row.findViewById(R.id.findingTitle)).setText(issue.kind.label);
            ((TextView) row.findViewById(R.id.findingDetail)).setText(issue.detail);

            row.findViewById(R.id.findingSeverity)
                    .setBackgroundColor(severityColor(issue.kind));

            renderAffected(row.findViewById(R.id.findingEntries),
                    row.findViewById(R.id.findingMore), issue);

            findings.addView(row);
            if (!firstRowByKind.containsKey(issue.kind)) firstRowByKind.put(issue.kind, row);
        }
    }

    /** Smooth-scroll the page so the first finding of this kind sits near the top. */
    private void scrollTo(VaultIssue.Kind kind) {
        View row = firstRowByKind.get(kind);
        if (row == null || !(root instanceof ScrollView)) return;
        int margin = (int) (12 * getResources().getDisplayMetrics().density);
        ((ScrollView) root).smoothScrollTo(0, Math.max(0, findings.getTop() + row.getTop() - margin));
    }

    private static int distinctEntries(VaultAudit.Report report, VaultIssue.Kind kind) {
        Set<Object> ids = new HashSet<>();
        for (VaultIssue issue : report.issues) {
            if (issue.kind != kind) continue;
            for (PasswordEntry e : issue.entries) ids.add(e.id);
        }
        return ids.size();
    }

    private static int entriesWithAnyIssue(VaultAudit.Report report) {
        Set<Object> ids = new HashSet<>();
        for (VaultIssue issue : report.issues) {
            for (PasswordEntry e : issue.entries) ids.add(e.id);
        }
        return ids.size();
    }

    /**
     * Names the affected entries, each one a chip that opens it. A count alone
     * tells you a problem exists but not which account to go and fix.
     */
    private void renderAffected(ChipGroup group, TextView more, VaultIssue issue) {
        group.removeAllViews();

        int shown = Math.min(issue.entries.size(), MAX_CHIPS);
        for (int i = 0; i < shown; i++) {
            PasswordEntry entry = issue.entries.get(i);
            Chip chip = (Chip) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_finding_chip, group, false);
            chip.setText(entry.displayLabel());
            chip.setOnClickListener(v -> EntryActions.open(this, entry.id));
            group.addView(chip);
        }

        int remaining = issue.entries.size() - shown;
        more.setVisibility(remaining > 0 ? View.VISIBLE : View.GONE);
        if (remaining > 0) more.setText(getString(R.string.health_and_more, remaining));
    }

    private int severityColor(VaultIssue.Kind kind) {
        switch (kind) {
            case BREACHED: return color(R.color.danger);
            case REUSED:   return color(R.color.warning);
            case WEAK:     return color(R.color.caution);
            default:       return color(R.color.text_muted);
        }
    }

    /** Ring colour by score band. The number itself stays dark, as in the shot. */
    private int colorFor(int score) {
        if (score >= 85) return color(R.color.good);
        if (score >= 65) return color(R.color.caution);
        if (score >= 40) return color(R.color.action);
        return color(R.color.danger);
    }

    private int color(int resId) {
        return ContextCompat.getColor(requireContext(), resId);
    }
}

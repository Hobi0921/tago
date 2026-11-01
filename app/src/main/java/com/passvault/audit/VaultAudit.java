package com.passvault.audit;

import com.passvault.data.PasswordEntry;
import com.passvault.net.BreachChecker;
import com.passvault.util.PasswordStrengthChecker;
import com.passvault.util.StrengthResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vault health analysis.
 *
 * Ordering reflects real-world risk rather than how easy each check is. A breached
 * password is compromised right now. A reused one turns any single site's breach
 * into a breach of every account sharing it — which is why reuse outranks weakness
 * here, even though a weak password scores worse on entropy.
 */
public final class VaultAudit {

    public static class Report {
        public final List<VaultIssue> issues = new ArrayList<>();
        public int totalEntries;
        public int healthScore;      // 0-100
        public boolean breachCheckRan;
        public boolean breachCheckFailed;

        public boolean isClean() {
            return issues.isEmpty();
        }
    }

    private static final long ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000;
    private static final double WEAK_BITS = 50.0;

    /** Offline checks only. Fast, safe to run on every screen open. */
    public static Report analyse(List<PasswordEntry> entries) {
        return analyse(entries, false);
    }

    public static Report analyse(List<PasswordEntry> entries, boolean includeBreachCheck) {
        Report report = new Report();
        report.totalEntries = entries.size();

        if (entries.isEmpty()) {
            report.healthScore = 100;
            return report;
        }

        // ---- reuse: group by exact password ----
        Map<String, List<PasswordEntry>> byPassword = new LinkedHashMap<>();
        for (PasswordEntry entry : entries) {
            byPassword.computeIfAbsent(entry.password, k -> new ArrayList<>()).add(entry);
        }

        int reusedCount = 0;
        for (Map.Entry<String, List<PasswordEntry>> group : byPassword.entrySet()) {
            if (group.getValue().size() > 1) {
                reusedCount += group.getValue().size();
                report.issues.add(new VaultIssue(
                        VaultIssue.Kind.REUSED,
                        group.getValue(),
                        "Used on " + group.getValue().size() + " accounts"));
            }
        }

        // ---- weak ----
        List<PasswordEntry> weak = new ArrayList<>();
        for (PasswordEntry entry : entries) {
            StrengthResult result = PasswordStrengthChecker.analyse(entry.password);
            if (result.entropyBits < WEAK_BITS) weak.add(entry);
        }
        if (!weak.isEmpty()) {
            report.issues.add(new VaultIssue(
                    VaultIssue.Kind.WEAK, weak,
                    weak.size() + (weak.size() == 1 ? " password is" : " passwords are")
                            + " easy to guess"));
        }

        // ---- stale ----
        long cutoff = System.currentTimeMillis() - ONE_YEAR_MS;
        List<PasswordEntry> stale = new ArrayList<>();
        for (PasswordEntry entry : entries) {
            if (entry.passwordUpdatedAt > 0 && entry.passwordUpdatedAt < cutoff) {
                stale.add(entry);
            }
        }
        if (!stale.isEmpty()) {
            report.issues.add(new VaultIssue(
                    VaultIssue.Kind.STALE, stale,
                    "Last changed over a year ago"));
        }

        // ---- breached (network) ----
        int breachedCount = 0;
        if (includeBreachCheck) {
            report.breachCheckRan = true;
            List<PasswordEntry> breached = new ArrayList<>();
            boolean anyFailed = false;

            for (String password : byPassword.keySet()) {
                int hits = BreachChecker.check(password);
                if (hits < 0) {
                    anyFailed = true;   // offline or rate-limited: NOT the same as clean
                } else if (hits > 0) {
                    breached.addAll(byPassword.get(password));
                }
            }

            report.breachCheckFailed = anyFailed;
            breachedCount = breached.size();

            if (!breached.isEmpty()) {
                report.issues.add(new VaultIssue(
                        VaultIssue.Kind.BREACHED, breached,
                        "Appears in known password breaches"));
            }
        }

        Collections.sort(report.issues, (a, b) -> a.kind.severity - b.kind.severity);

        report.healthScore = score(entries.size(), breachedCount, reusedCount, weak.size(), stale.size());
        return report;
    }

    /**
     * Weighted by how much each problem actually costs you. A breached password is
     * already known to attackers, so it is weighted heaviest; staleness on its own
     * is barely a problem, so it barely moves the number.
     */
    private static int score(int total, int breached, int reused, int weak, int stale) {
        if (total == 0) return 100;

        double penalty =
                (breached / (double) total) * 55
                        + (reused / (double) total) * 25
                        + (weak / (double) total) * 15
                        + (stale / (double) total) * 5;

        return (int) Math.max(0, Math.round(100 - penalty));
    }

    private VaultAudit() { }
}

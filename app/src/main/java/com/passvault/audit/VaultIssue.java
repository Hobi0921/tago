package com.passvault.audit;

import com.passvault.data.PasswordEntry;

import java.util.List;

/** One problem found during an audit, with the entries it affects. */
public class VaultIssue {

    public enum Kind {
        BREACHED(0, "Found in a breach"),
        REUSED(1, "Reused password"),
        WEAK(2, "Weak password"),
        STALE(3, "Not changed in over a year");

        public final int severity; // lower sorts first
        public final String label;

        Kind(int severity, String label) {
            this.severity = severity;
            this.label = label;
        }
    }

    public final Kind kind;
    public final List<PasswordEntry> entries;
    public final String detail;

    public VaultIssue(Kind kind, List<PasswordEntry> entries, String detail) {
        this.kind = kind;
        this.entries = entries;
        this.detail = detail;
    }
}

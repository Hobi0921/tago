---
target: the Android UI/UX
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Users\\rosal\\AndroidStudioProjects\\PassVault\\app\\src\\main\\res\\layout"
timestamp: 2026-11-01T06-46-09Z
slug: app-src-main-res-layout
---
Method: design review only (detector not applicable, native platform)
Run notes: no emulator or device; reviewed from layouts, styles and Java, plus renders made earlier in the session. No PRODUCT.md.

## Design Health Score
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Good copy/breach/offline/autofill feedback; first launch drops into the biometric prompt with no context |
| 2 | Match System / Real World | 3 | "N bits" entropy; "Tools" hides Generate/Check |
| 3 | User Control and Freedom | 3 | Undo delete, discard confirm, recovery confirm; "I have a backup" goes nowhere |
| 4 | Consistency and Standards | 2 | 12 hand-picked text sizes; Unlock button off-style; old padlock illustration vs new identity |
| 5 | Error Prevention | 3 | Masked by default, inline validation, guarded recovery |
| 6 | Recognition Rather Than Recall | 3 | Labelled nav; generator nested behind a toggle |
| 7 | Flexibility and Efficiency | 2 | No sorting, launcher shortcuts or username copy from the list |
| 8 | Aesthetic and Minimalist Design | 3 | Clean; strength label on every row is noise |
| 9 | Error Recovery | 3 | Errors at the field, input kept, Try again on unlock |
| 10 | Help and Documentation | 2 | No help at the autofill moment |
| Total | | 27/40 | Acceptable |

## Design Specificity Verdict
Structure is the stock password-manager template (list + FAB, pill bottom nav, score ring, breach banner), traced from a reference shot. The Tago identity (nine-dot grid, trace, indigo + coral) stops at the unlock screen; the empty state still shows the old vault/key/padlock/verified-badge illustration.

## Priority Issues
- [P1] First-run screen built but never shown: OnboardingActivity is not in the manifest and nothing launches it; EXTRA_RESTORE is never read. Fix: register, route from UnlockActivity when no key and not seen, carry restore flag into EXTRA_OPEN_RESTORE. Command: /impeccable onboard
- [P1] Autofill buried in Settings: never offered at setup, empty vault or first save. Fix: last first-run step + dismissible vault card until enabled. Command: /impeccable onboard
- [P2] New identity stops at the unlock screen: replace ill_onboarding.xml with grid/trace art; motif in empty/no-results states and a short generator trace; keep main screens restrained. Command: /impeccable delight
- [P2] Type-scale drift: 12 inline sp sizes; map to Material 3 roles via TextAppearance.PassVault.*. Command: /impeccable typeset
- [P2] Most frequent action out of thumb reach, no shortcuts: launcher shortcuts (Search, New login, Generate), long-press Copy username, sorting past ~50 entries. Command: /impeccable adapt

## Persona Red Flags
- Jordan: cold biometric "Set up Tago" prompt; no autofill guidance; "bits" jargon.
- Casey: search at top of a tall phone; no launcher shortcut; username copy takes three taps.
- Alex: no sort/recent/grouping; no shortcuts or bulk actions; learned autofill links invisible.

## Minor Observations
Strength label on every row; Unlock button off-style; predictive back not enabled; no navigation rail on tablets; strength labels hard-coded English; "Tools" label vague.

## Questions to Consider
First run ending with autofill on? Generator tracing its password on the grid? Score ring vs one sentence? Dynamic color vs fixed indigo, decided on purpose?

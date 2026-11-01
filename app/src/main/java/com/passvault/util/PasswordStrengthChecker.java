package com.passvault.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entropy-based strength analysis.
 *
 * The desktop version counted length tiers and character classes, which rates
 * "Password123!" as Very Strong — it has upper, lower, digit, symbol and 12
 * characters. That scoring rewards shape rather than unpredictability.
 *
 * This version estimates raw entropy from the character pool, then subtracts bits
 * for the patterns that make a password guessable: dictionary words (after leet
 * de-substitution), keyboard runs, character sequences, repeats, dates, and the
 * "capitalise the first letter, put the digit and symbol at the end" template that
 * almost every human password follows.
 */
public final class PasswordStrengthChecker {

    // A real build would ship a 10k-entry list as a compressed asset. This covers
    // the cases that matter most for immediate feedback.
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
            "password", "passwort", "passord", "123456", "12345678", "123456789",
            "1234567890", "qwerty", "qwertyuiop", "abc123", "monkey", "letmein",
            "dragon", "111111", "baseball", "iloveyou", "trustno1", "sunshine",
            "master", "welcome", "shadow", "ashley", "football", "jesus", "michael",
            "ninja", "mustang", "access", "batman", "superman", "starwars",
            "hello", "freedom", "whatever", "qazwsx", "admin", "administrator",
            "root", "login", "guest", "test", "user", "changeme", "secret",
            "pokemon", "computer", "internet", "samsung", "google", "facebook",
            "princess", "flower", "hottie", "loveme", "zaq12wsx", "asdfgh",
            "zxcvbnm", "1q2w3e4r", "qwe123", "121212", "000000", "654321",
            "manila", "philippines", "pinoy", "basketball", "chocolate",
            "troubadour", "troubador", "summer", "winter", "spring", "autumn",
            "january", "february", "december", "matrix", "phoenix", "diamond",
            "soccer", "orange", "purple", "yellow", "silver", "golden", "tigers",
            "eagles", "cowboys", "lakers", "yankees", "liverpool", "arsenal",
            "chelsea", "barcelona", "madrid", "united", "school", "teacher",
            "student", "family", "mother", "father", "sister", "brother",
            "birthday", "holiday", "vacation", "weekend", "friday", "monday"
    ));

    private static final String[] KEYBOARD_ROWS = {
            "qwertyuiop", "asdfghjkl", "zxcvbnm",
            "1234567890", "!@#$%^&*()"
    };

    private static final String SPECIALS = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\\ ";

    /** Guesses per second assumed for an offline attack on a fast GPU rig. */
    private static final double GUESSES_PER_SECOND = 1e11;

    public static StrengthResult analyse(String password) {
        List<String> advice = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            advice.add("Type a password to check it.");
            return new StrengthResult(StrengthResult.Level.VERY_WEAK, 0, 0, advice, "instantly");
        }

        // Character-based estimate, then the word-based estimate for anything that
        // looks like a passphrase. An attacker uses whichever is cheaper, so the
        // honest number is the smaller of the two.
        double entropy = Math.min(rawEntropy(password), passphraseEntropy(password));
        entropy -= patternPenalty(password, advice);

        // "Password123!" survives the penalties with ~42 bits, but a cracker running
        // dictionary + append rules finds it in seconds. When a common word makes up
        // most of the password, the decoration around it is worth a fixed handful of
        // bits, not a per-character bonus.
        entropy = Math.min(entropy, dictionaryCap(password));

        if (entropy < 0) entropy = 0;

        collectAdvice(password, advice);

        int score = (int) Math.round(Math.min(100, (entropy / 90.0) * 100));
        StrengthResult.Level level = levelFor(entropy);

        return new StrengthResult(level, score, entropy, advice, crackTime(entropy));
    }

    /** Convenience shim so existing calling code reads the same as before. */
    public static String checkStrength(String password) {
        return analyse(password).level.label;
    }

    // ------------------------------------------------------------ raw entropy

    private static double rawEntropy(String password) {
        boolean lower = false, upper = false, digit = false, special = false, extended = false;

        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else if (SPECIALS.indexOf(c) >= 0) special = true;
            else extended = true;
        }

        int pool = 0;
        if (lower) pool += 26;
        if (upper) pool += 26;
        if (digit) pool += 10;
        if (special) pool += SPECIALS.length();
        if (extended) pool += 100; // rough allowance for unicode

        if (pool == 0) return 0;
        return password.length() * (Math.log(pool) / Math.log(2));
    }

    // ------------------------------------------------------- dictionary cap

    /**
     * Hard ceiling for passwords that are mostly one common word. Returns
     * MAX_VALUE when no common word dominates, leaving the estimate untouched.
     */
    private static double dictionaryCap(String password) {
        if (password.isEmpty()) return Double.MAX_VALUE;

        String deleet = deLeet(password.toLowerCase());
        int longest = 0;

        for (String word : COMMON) {
            if (word.length() >= 4 && deleet.contains(word)) {
                longest = Math.max(longest, word.length());
            }
        }
        if (longest == 0) return Double.MAX_VALUE;

        double coverage = (double) longest / password.length();
        if (coverage < 0.5) return Double.MAX_VALUE;

        // The word itself is ~1 guess. What is left is a short suffix or prefix
        // plus capitalisation, which rule-based crackers enumerate cheaply.
        int remaining = password.length() - longest;
        return 6 + remaining * 4.0;
    }

    // ------------------------------------------------------ passphrase entropy

    /**
     * Word-based estimate for passphrases.
     *
     * Counting characters massively overrates them: "correct-horse-battery-staple"
     * scores 164 bits per character but an attacker guessing whole words only needs
     * about 4 x log2(dictionary). Returns MAX_VALUE when the password does not look
     * like words, so the caller's Math.min leaves the character estimate alone.
     */
    private static double passphraseEntropy(String password) {
        // Split on separators AND on camelCase boundaries, so "MyDogAteTheHomework"
        // is seen as five words rather than one unguessable 19-character blob.
        String separated = password
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z]+", " ");

        String[] chunks = separated.trim().split(" +");

        int wordCount = 0;
        for (String chunk : chunks) {
            if (chunk.length() >= 2) wordCount++;
            else if (!chunk.isEmpty()) return Double.MAX_VALUE; // stray single letters
        }

        if (wordCount < 3) return Double.MAX_VALUE;

        // Bail out if the "words" contain the digit-and-symbol soup of a random
        // password rather than clean alphabetic runs.
        int alphabetic = 0;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) alphabetic++;
        }
        if (alphabetic < password.length() * 0.6) return Double.MAX_VALUE;

        // Assume a 20k-word attacker dictionary — generous to the user, since real
        // passphrase crackers work from much smaller frequency-ranked lists.
        double bitsPerWord = Math.log(20000) / Math.log(2);
        double entropy = wordCount * bitsPerWord;

        // Credit the separators and any trailing digits, but only a little.
        if (password.matches(".*\\d.*")) entropy += 6;
        if (password.matches(".*[A-Z].*")) entropy += 2;

        return entropy;
    }

    // --------------------------------------------------------- pattern penalty

    private static double patternPenalty(String password, List<String> advice) {
        double penalty = 0;
        String lower = password.toLowerCase();
        String deleet = deLeet(lower);

        // Whole password is a known common password.
        if (COMMON.contains(lower) || COMMON.contains(deleet)) {
            advice.add("This is one of the most-guessed passwords in existence.");
            return 1000; // floor it
        }

        // Contains a common word — the rest of the password carries the weight.
        for (String word : COMMON) {
            if (word.length() >= 4 && deleet.contains(word)) {
                penalty += word.length() * 3.0;
                advice.add("Contains the common word \"" + word + "\". "
                        + "Swapping letters for numbers does not help — crackers try that first.");
                break;
            }
        }

        // Keyboard runs: qwerty, asdf, 1234 typed in a row.
        int kb = longestKeyboardRun(lower);
        if (kb >= 4) {
            penalty += kb * 3.0;
            advice.add("Contains a straight run of keys off the keyboard.");
        }

        // Alphabetic or numeric sequences: abcde, 98765.
        int seq = longestSequence(lower);
        if (seq >= 4) {
            penalty += seq * 3.0;
            advice.add("Contains a predictable sequence like \"abcd\" or \"1234\".");
        }

        // Repeats: aaaa, abab, xyzxyzxyz.
        int rep = repeatedBlockLength(lower);
        if (rep >= 4) {
            penalty += rep * 2.5;
            advice.add("Contains a repeated block of characters.");
        }

        // Years and dates.
        if (lower.matches(".*(19[5-9]\\d|20[0-4]\\d).*")) {
            penalty += 8;
            advice.add("Contains a year. Birth years and graduation years are guessed early.");
        }

        // The universal human template: Capital + word + digits + symbol at the end.
        if (password.matches("^[A-Z][a-z]+\\d{1,4}[!@#$%^&*.]?$")) {
            penalty += 12;
            advice.add("Follows the most common password shape there is: "
                    + "capital, word, numbers, symbol. Move the variety into the middle.");
        }

        return penalty;
    }

    /** Undo common character substitutions so "P@ssw0rd" is seen as "password". */
    private static String deLeet(String s) {
        return s.replace('@', 'a').replace('4', 'a')
                .replace('3', 'e').replace('1', 'l')
                .replace('!', 'i').replace('0', 'o')
                .replace('$', 's').replace('5', 's')
                .replace('7', 't').replace('+', 't');
    }

    private static int longestKeyboardRun(String s) {
        int best = 0;
        for (String row : KEYBOARD_ROWS) {
            String reversed = new StringBuilder(row).reverse().toString();
            best = Math.max(best, longestCommonRun(s, row));
            best = Math.max(best, longestCommonRun(s, reversed));
        }
        return best;
    }

    private static int longestCommonRun(String s, String row) {
        int best = 0;
        for (int len = row.length(); len >= 3; len--) {
            for (int i = 0; i + len <= row.length(); i++) {
                if (s.contains(row.substring(i, i + len))) return len;
            }
        }
        return best;
    }

    private static int longestSequence(String s) {
        int best = 1, run = 1;
        for (int i = 1; i < s.length(); i++) {
            int diff = s.charAt(i) - s.charAt(i - 1);
            if (diff == 1 || diff == -1) {
                run++;
                best = Math.max(best, run);
            } else {
                run = 1;
            }
        }
        return best;
    }

    private static int repeatedBlockLength(String s) {
        int best = 0;
        for (int block = 1; block <= s.length() / 2; block++) {
            for (int i = 0; i + block * 2 <= s.length(); i++) {
                String unit = s.substring(i, i + block);
                int reps = 1;
                int j = i + block;
                while (j + block <= s.length() && s.startsWith(unit, j)) {
                    reps++;
                    j += block;
                }
                if (reps >= 2) best = Math.max(best, block * reps);
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ advice

    private static void collectAdvice(String password, List<String> advice) {
        if (password.length() < 12) {
            advice.add("Aim for at least 12 characters. Length beats complexity.");
        }
        if (!password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*")) {
            advice.add("Mix upper and lower case.");
        }
        if (!password.matches(".*\\d.*")) {
            advice.add("Add a number somewhere other than the end.");
        }
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (SPECIALS.indexOf(c) >= 0) { hasSpecial = true; break; }
        }
        if (!hasSpecial) advice.add("Add a symbol.");

        if (advice.isEmpty()) {
            advice.add("Nothing to improve here. Make sure it is not reused anywhere else.");
        }
    }

    // ------------------------------------------------------------------ levels

    private static StrengthResult.Level levelFor(double entropy) {
        if (entropy < 28) return StrengthResult.Level.VERY_WEAK;
        if (entropy < 40) return StrengthResult.Level.WEAK;
        if (entropy < 56) return StrengthResult.Level.FAIR;
        if (entropy < 76) return StrengthResult.Level.STRONG;
        return StrengthResult.Level.VERY_STRONG;
    }

    private static String crackTime(double entropy) {
        double guesses = Math.pow(2, entropy) / 2.0; // expect to find it halfway through
        double seconds = guesses / GUESSES_PER_SECOND;

        if (seconds < 1) return "instantly";
        if (seconds < 60) return Math.round(seconds) + " seconds";
        if (seconds < 3600) return Math.round(seconds / 60) + " minutes";
        if (seconds < 86400) return Math.round(seconds / 3600) + " hours";
        if (seconds < 2592000) return Math.round(seconds / 86400) + " days";
        if (seconds < 31536000) return Math.round(seconds / 2592000) + " months";

        double years = seconds / 31536000.0;
        if (years < 1000) return Math.round(years) + " years";
        if (years < 1e6) return Math.round(years / 1000) + " thousand years";
        if (years < 1e9) return Math.round(years / 1e6) + " million years";
        return "longer than the universe has existed";
    }

    private PasswordStrengthChecker() { }
}

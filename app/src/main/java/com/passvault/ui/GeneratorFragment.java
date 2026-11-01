package com.passvault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.passvault.R;
import com.passvault.data.AppPrefs;
import com.passvault.util.PasswordGenerator;
import com.passvault.util.PasswordStrengthChecker;
import com.passvault.util.StrengthResult;

/**
 * Password and passphrase generation.
 *
 * Settings persist, and the entry editor's Generate button reads the same ones, so
 * the two places cannot disagree about what a generated password looks like.
 */
public class GeneratorFragment extends Fragment {

    /** Passphrase word counts. PasswordGenerator refuses fewer than four. */
    private static final float MIN_WORDS = 4f;
    private static final float MAX_WORDS = 10f;
    private static final float MIN_CHARS = 8f;
    private static final float MAX_CHARS = 64f;

    private AppPrefs prefs;

    private TextView output;
    private TextView rating;
    private Slider lengthSlider;
    private TextView lengthLabel;
    private MaterialButtonToggleGroup mode;
    private View charOptions, wordOptions;
    private CheckBox useUpper, useLower, useDigits, useSymbols, avoidAmbiguous, appendNumber;

    private boolean useWords;
    /** Set while restoring saved state, so listeners do not regenerate mid-setup. */
    private boolean binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        prefs = new AppPrefs(requireContext());

        output = view.findViewById(R.id.genOutput);
        rating = view.findViewById(R.id.genRating);
        lengthSlider = view.findViewById(R.id.genLength);
        lengthLabel = view.findViewById(R.id.genLengthLabel);
        mode = view.findViewById(R.id.genMode);
        charOptions = view.findViewById(R.id.genCharOptions);
        wordOptions = view.findViewById(R.id.genWordOptions);

        useUpper = view.findViewById(R.id.genUpper);
        useLower = view.findViewById(R.id.genLower);
        useDigits = view.findViewById(R.id.genDigits);
        useSymbols = view.findViewById(R.id.genSymbols);
        avoidAmbiguous = view.findViewById(R.id.genAmbiguous);
        appendNumber = view.findViewById(R.id.genWordsNumber);

        restoreSettings();

        mode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (binding || !isChecked) return;
            useWords = checkedId == R.id.genModeWords;
            applyMode();
            persistAndRegenerate();
        });

        lengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (binding) return;
            renderLengthLabel((int) value);
            persistAndRegenerate();
        });

        View.OnClickListener onToggle = v -> {
            if (binding) return;
            enforceOneCharacterSet(v);
            persistAndRegenerate();
        };
        useUpper.setOnClickListener(onToggle);
        useLower.setOnClickListener(onToggle);
        useDigits.setOnClickListener(onToggle);
        useSymbols.setOnClickListener(onToggle);
        avoidAmbiguous.setOnClickListener(onToggle);
        appendNumber.setOnClickListener(onToggle);

        // Only deliberate generation replays the trace: the first password shown and each tap
        // of Regenerate. The slider regenerates on every step of a drag, where a pattern
        // flickering through shapes would be noise.
        TraceView trace = view.findViewById(R.id.genTrace);
        view.findViewById(R.id.genRegenerate).setOnClickListener(v -> {
            regenerate();
            trace.traceNew();
        });
        view.findViewById(R.id.genCopy).setOnClickListener(v -> copyToClipboard());
        view.findViewById(R.id.genSave).setOnClickListener(v -> saveToVault());

        regenerate();
        trace.traceNew();
    }

    // ----------------------------------------------------------------- settings

    private void restoreSettings() {
        binding = true;

        useWords = prefs.generatorUsesWords();
        PasswordGenerator.Options saved = prefs.generatorOptions();

        useLower.setChecked(saved.useLower);
        useUpper.setChecked(saved.useUpper);
        useDigits.setChecked(saved.useDigits);
        useSymbols.setChecked(saved.useSymbols);
        avoidAmbiguous.setChecked(saved.avoidAmbiguous);
        appendNumber.setChecked(prefs.appendNumber());

        mode.check(useWords ? R.id.genModeWords : R.id.genModeChars);
        applyMode();

        binding = false;
    }

    /** The one slider serves both modes, so its range and label change with the mode. */
    private void applyMode() {
        boolean wasBinding = binding;
        binding = true;

        // Animate a mode the user picked; restoring the saved mode on open stays instant.
        if (!wasBinding) Motion.beginLayoutChange((ViewGroup) charOptions.getParent());
        charOptions.setVisibility(useWords ? View.GONE : View.VISIBLE);
        wordOptions.setVisibility(useWords ? View.VISIBLE : View.GONE);

        // Widen the range before moving the thumb: Slider throws if the current
        // value ever sits outside valueFrom..valueTo while they are reassigned.
        lengthSlider.setValueFrom(Math.min(MIN_WORDS, MIN_CHARS));
        lengthSlider.setValueTo(Math.max(MAX_WORDS, MAX_CHARS));
        setSliderValue(useWords ? prefs.wordCount() : prefs.generatorOptions().length);
        lengthSlider.setValueFrom(useWords ? MIN_WORDS : MIN_CHARS);
        lengthSlider.setValueTo(useWords ? MAX_WORDS : MAX_CHARS);

        renderLengthLabel((int) lengthSlider.getValue());
        binding = wasBinding;
    }

    private void setSliderValue(int value) {
        float min = useWords ? MIN_WORDS : MIN_CHARS;
        float max = useWords ? MAX_WORDS : MAX_CHARS;
        lengthSlider.setValue(Math.max(min, Math.min(max, value)));
    }

    private void renderLengthLabel(int value) {
        lengthLabel.setText(getString(useWords ? R.string.gen_words : R.string.gen_length, value));
    }

    /**
     * Turning every character class off made the generator quietly fall back to
     * lowercase while the UI showed nothing checked. The last one on now stays on,
     * and says why when it is tapped.
     */
    private void enforceOneCharacterSet(View tapped) {
        if (useWords) return;
        if (useLower.isChecked() || useUpper.isChecked()
                || useDigits.isChecked() || useSymbols.isChecked()) {
            return;
        }
        if (tapped instanceof CheckBox) ((CheckBox) tapped).setChecked(true);
        Snackbar.make(requireView(), R.string.gen_last_set, Snackbar.LENGTH_SHORT).show();
    }

    private PasswordGenerator.Options currentOptions() {
        PasswordGenerator.Options options = new PasswordGenerator.Options();
        options.length = useWords ? prefs.generatorOptions().length : (int) lengthSlider.getValue();
        options.useUpper = useUpper.isChecked();
        options.useLower = useLower.isChecked();
        options.useDigits = useDigits.isChecked();
        options.useSymbols = useSymbols.isChecked();
        options.avoidAmbiguous = avoidAmbiguous.isChecked();
        return options;
    }

    private void persistAndRegenerate() {
        int words = useWords ? (int) lengthSlider.getValue() : prefs.wordCount();
        prefs.saveGenerator(useWords, currentOptions(), words, appendNumber.isChecked());
        regenerate();
    }

    // --------------------------------------------------------------- generation

    private void regenerate() {
        show(useWords
                ? PasswordGenerator.generatePassphrase(
                        (int) lengthSlider.getValue(), appendNumber.isChecked())
                : PasswordGenerator.generate(currentOptions()));
    }

    private void show(String password) {
        output.setText(password);
        StrengthResult result = PasswordStrengthChecker.analyse(password);
        rating.setText(getString(R.string.gen_rating, result.level.label, result.crackTime));
        rating.setTextColor(result.level.color(requireContext()));
    }

    private void copyToClipboard() {
        // Shared helper: marks the clip sensitive and really does clear it after
        // 45 seconds, which the old copy here claimed but never did.
        ClipboardHelper.copySecret(requireView(), output.getText().toString(), R.string.gen_copied);
    }

    private void saveToVault() {
        EntryEditorDialog.newInstanceWithPassword(output.getText().toString())
                .show(getParentFragmentManager(), "editor");
    }
}

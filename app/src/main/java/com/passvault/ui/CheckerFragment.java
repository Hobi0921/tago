package com.passvault.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.res.ColorStateList;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.passvault.R;
import com.passvault.util.PasswordStrengthChecker;
import com.passvault.util.StrengthResult;

/** Live strength analysis. Same idea as the desktop tab, with real entropy behind it. */
public class CheckerFragment extends Fragment {

    private ProgressBar bar;
    private TextView label;
    private TextView crackTime;
    private TextView entropy;
    private LinearLayout adviceList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_checker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bar = view.findViewById(R.id.checkerBar);
        label = view.findViewById(R.id.checkerLabel);
        crackTime = view.findViewById(R.id.checkerCrackTime);
        entropy = view.findViewById(R.id.checkerEntropy);
        adviceList = view.findViewById(R.id.checkerAdvice);

        TextInputEditText input = view.findViewById(R.id.checkerInput);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                render(PasswordStrengthChecker.analyse(s.toString()));
            }
        });

        render(PasswordStrengthChecker.analyse(""));
    }

    private void render(StrengthResult result) {
        // 150ms ease-out, retargeted from wherever the bar is, so it never lags the typing.
        Motion.tweenProgress(bar, result.score);
        // Tint only the filled portion; setTint on the whole drawable would
        // repaint the track as well and the bar would look permanently full.
        int color = result.level.color(requireContext());
        bar.setProgressTintList(ColorStateList.valueOf(color));

        label.setText(result.level.label);
        label.setTextColor(color);

        crackTime.setText(getString(R.string.checker_crack_time, result.crackTime));
        entropy.setText(getString(R.string.checker_entropy, (int) Math.round(result.entropyBits)));

        adviceList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (String tip : result.advice) {
            TextView row = (TextView) inflater.inflate(
                    R.layout.item_advice, adviceList, false);
            row.setText(tip);
            adviceList.addView(row);
        }
    }
}

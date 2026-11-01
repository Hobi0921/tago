package com.passvault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.passvault.R;

/**
 * Generate and Check under one tab. They answer the same question from two sides
 * ("is this password good?"), and five bottom tabs was the Material maximum.
 */
public class ToolsFragment extends Fragment {

    private static final String TAG_GENERATE = "tool_generate";
    private static final String TAG_CHECK = "tool_check";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tools, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toolsToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) show(checkedId == R.id.toolsCheck ? TAG_CHECK : TAG_GENERATE);
        });

        if (savedInstanceState == null) show(TAG_GENERATE);
    }

    private void show(String tag) {
        FragmentManager fm = getChildFragmentManager();
        String otherTag = TAG_GENERATE.equals(tag) ? TAG_CHECK : TAG_GENERATE;

        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        Fragment other = fm.findFragmentByTag(otherTag);
        if (other != null && !other.isHidden()) tx.hide(other);

        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            tx.add(R.id.toolsContent,
                    TAG_CHECK.equals(tag) ? new CheckerFragment() : new GeneratorFragment(), tag);
        } else {
            tx.show(target);
        }
        tx.commit();
    }
}

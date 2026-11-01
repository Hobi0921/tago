package com.passvault.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.passvault.R;
import com.passvault.data.PasswordEntry;
import com.passvault.util.PasswordStrengthChecker;
import com.passvault.util.StrengthResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EntryAdapter extends ListAdapter<PasswordEntry, EntryAdapter.EntryHolder> {

    public interface Listener {
        /** Row tapped: show the detail sheet. */
        void onOpen(PasswordEntry entry);
        /** Copy button: the one-tap path for the most common job. */
        void onCopy(PasswordEntry entry);
    }

    private final Listener listener;

    /**
     * Rating per entry id, reused while the password is unchanged. analyse() scans the
     * common-password list twice and checks keyboard runs, which is too much to repeat on
     * every bind while flinging a long list. Only a hash of the password is kept here.
     */
    private final Map<Long, Rating> ratings = new HashMap<>();

    private static final class Rating {
        final int passwordHash;
        final StrengthResult.Level level;

        Rating(int passwordHash, StrengthResult.Level level) {
            this.passwordHash = passwordHash;
            this.level = level;
        }
    }

    public EntryAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<PasswordEntry> DIFF =
            new DiffUtil.ItemCallback<PasswordEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull PasswordEntry a, @NonNull PasswordEntry b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull PasswordEntry a, @NonNull PasswordEntry b) {
                    // favorite is compared explicitly: starring does not move updatedAt.
                    return a.siteName.equals(b.siteName)
                            && a.username.equals(b.username)
                            && a.password.equals(b.password)
                            && a.favorite == b.favorite
                            && Objects.equals(a.webDomain, b.webDomain)
                            && a.updatedAt == b.updatedAt;
                }
            };

    @NonNull
    @Override
    public EntryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_entry, parent, false);
        return new EntryHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryHolder holder, int position) {
        PasswordEntry entry = getItem(position);
        holder.bind(entry, levelFor(entry), listener);
    }

    /** Strength is derived live, so improving the scorer re-rates old entries. */
    private StrengthResult.Level levelFor(PasswordEntry entry) {
        int hash = entry.password.hashCode();
        Rating cached = ratings.get(entry.id);
        if (cached == null || cached.passwordHash != hash) {
            cached = new Rating(hash, PasswordStrengthChecker.analyse(entry.password).level);
            ratings.put(entry.id, cached);
        }
        return cached.level;
    }

    static class EntryHolder extends RecyclerView.ViewHolder {

        private final TextView site;
        private final TextView username;
        private final TextView strength;
        private final TextView initial;
        private final ImageView favorite;
        private final ImageButton copyButton;
        final View avatar;
        final ImageView icon;

        EntryHolder(@NonNull View itemView) {
            super(itemView);
            site = itemView.findViewById(R.id.entrySite);
            username = itemView.findViewById(R.id.entryUsername);
            strength = itemView.findViewById(R.id.entryStrength);
            initial = itemView.findViewById(R.id.entryInitial);
            favorite = itemView.findViewById(R.id.entryFavorite);
            copyButton = itemView.findViewById(R.id.entryCopy);
            avatar = itemView.findViewById(R.id.entryAvatar);
            icon = itemView.findViewById(R.id.entryIcon);
        }

        void bind(PasswordEntry entry, StrengthResult.Level level, Listener listener) {
            String label = entry.displayLabel();
            site.setText(label);
            username.setText(entry.username);
            username.setVisibility(entry.username.isEmpty() ? View.GONE : View.VISIBLE);
            Avatars.bind(avatar, initial, icon, entry.webDomain, label);
            favorite.setVisibility(entry.favorite ? View.VISIBLE : View.GONE);

            strength.setText(level.label);
            strength.setTextColor(level.color(itemView.getContext()));

            itemView.setOnClickListener(v -> listener.onOpen(entry));
            copyButton.setOnClickListener(v -> listener.onCopy(entry));
        }
    }
}

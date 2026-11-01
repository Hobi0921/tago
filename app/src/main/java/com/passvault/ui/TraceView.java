package com.passvault.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.passvault.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A small nine-dot grid that traces a fresh pattern each time the generator deliberately makes
 * a password: Tago's own way of saying "a new secret was just made". The pattern is random, not
 * derived from the password, so it reveals nothing about it.
 *
 * Proportions follow the logo: line about a third of the dot spacing, node diameter 1.6x the
 * line, unused dots half a node. 400 ms on Material's standard curve, so it reads as drawn; a
 * new trace interrupts the running one. With animations off, the finished pattern shows at once.
 */
public class TraceView extends View {

    private static final long DURATION_MS = 400;

    private final Paint idlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint startPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    /** Grid indices 0..8 (row-major) in trace order; empty until the first trace. */
    private int[] pattern = new int[0];
    /** 0..1 share of the pattern drawn so far. */
    private float progress = 1f;
    private ValueAnimator animator;

    public TraceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        idlePaint.setColor(ContextCompat.getColor(context, R.color.border_strong));
        int accent = ContextCompat.getColor(context, R.color.accent);
        linePaint.setColor(accent);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        nodePaint.setColor(accent);
        startPaint.setColor(ContextCompat.getColor(context, R.color.action));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Traces a new random pattern, interrupting any trace still running. */
    public void traceNew() {
        pattern = randomPattern();
        if (animator != null) animator.cancel();
        if (!Motion.enabled()) {
            progress = 1f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(Motion.TRACE);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /**
     * Four or five nodes, each a neighbour (diagonals included) of the last and never revisited,
     * the way a real unlock pattern is drawn.
     */
    private int[] randomPattern() {
        int length = 4 + random.nextInt(2);
        List<Integer> path = new ArrayList<>();
        path.add(random.nextInt(9));
        while (path.size() < length) {
            int last = path.get(path.size() - 1);
            List<Integer> next = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                if (path.contains(i)) continue;
                if (Math.abs(i / 3 - last / 3) <= 1 && Math.abs(i % 3 - last % 3) <= 1) next.add(i);
            }
            if (next.isEmpty()) break;
            path.add(next.get(random.nextInt(next.size())));
        }
        int[] out = new int[path.size()];
        for (int i = 0; i < out.length; i++) out[i] = path.get(i);
        return out;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cell = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom()) / 3f;
        float left = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - 3 * cell) / 2f;
        float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - 3 * cell) / 2f;
        float line = cell * 0.32f, node = line * 0.8f, idle = node * 0.5f;
        linePaint.setStrokeWidth(line);

        for (int i = 0; i < 9; i++) {
            canvas.drawCircle(x(i, left, cell), y(i, top, cell), idle, idlePaint);
        }
        if (pattern.length == 0) return;

        // How far along the pattern we are, in segments: e.g. 2.4 = two full, 40% of the third.
        float reach = progress * (pattern.length - 1);
        for (int s = 0; s < pattern.length - 1 && s < reach; s++) {
            float share = Math.min(1f, reach - s);
            int a = pattern[s], b = pattern[s + 1];
            float ax = x(a, left, cell), ay = y(a, top, cell);
            float bx = ax + (x(b, left, cell) - ax) * share, by = ay + (y(b, top, cell) - ay) * share;
            canvas.drawLine(ax, ay, bx, by, linePaint);
        }
        for (int s = 1; s < pattern.length && s <= reach + 1e-3f; s++) {
            canvas.drawCircle(x(pattern[s], left, cell), y(pattern[s], top, cell), node, nodePaint);
        }
        // The start node, coral as in the logo, is there from the first frame.
        canvas.drawCircle(x(pattern[0], left, cell), y(pattern[0], top, cell), node, startPaint);
    }

    private static float x(int index, float left, float cell) {
        return left + (index % 3 + 0.5f) * cell;
    }

    private static float y(int index, float top, float cell) {
        return top + (index / 3 + 0.5f) * cell;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}

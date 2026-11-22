package com.planesnomad.gui.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.HashMap;
import java.util.Map;

/**
 * Stacks children top to bottom, giving each a share of the height proportional to its
 * weight (the constraint passed to {@code add}). Every child gets the full width.
 */
public final class WeightedColumn implements LayoutManager2 {

    private final Map<Component, Double> weights = new HashMap<>();

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        weights.put(comp, constraints instanceof Number n ? n.doubleValue() : 1.0);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        weights.put(comp, 1.0);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        weights.remove(comp);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        int width = parent.getWidth() - in.left - in.right;
        int height = parent.getHeight() - in.top - in.bottom;
        double total = 0;
        for (Component c : parent.getComponents()) {
            if (c.isVisible()) {
                total += weights.getOrDefault(c, 1.0);
            }
        }
        int y = in.top;
        double used = 0;
        for (Component c : parent.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            used += weights.getOrDefault(c, 1.0);
            int bottom = in.top + (int) Math.round(height * used / total);
            c.setBounds(in.left, y, width, bottom - y);
            y = bottom;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return new Dimension(Theme.px(600), Theme.px(400));
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(0, 0);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) { }
}

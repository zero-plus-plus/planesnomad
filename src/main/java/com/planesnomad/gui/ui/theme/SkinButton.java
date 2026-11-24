package com.planesnomad.gui.ui.theme;

import com.planesnomad.gui.ui.Theme;

import javax.swing.JButton;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A button drawn from the theme: elements "button", "button.hover", "button.pressed" and
 * "button.disabled" (each can be a picture or fill/border; text color from "button.text" etc.).
 */
public class SkinButton extends JButton {

    public SkinButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setOpaque(false);
    }

    private String state() {
        if (!isEnabled()) {
            return "button.disabled";
        }
        if (getModel().isPressed() && getModel().isArmed()) {
            return "button.pressed";
        }
        return getModel().isRollover() ? "button.hover" : "button";
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(getText()) + Theme.px(22);
        int h = fm.getHeight() + Theme.px(8);
        return new Dimension(w, h);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String element = state();
        Skin.paint(g, element, 0, 0, getWidth(), getHeight());
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Skin.text(element, isEnabled() ? Skin.text("button", Theme.TEXT) : Theme.TEXT_DIM));
        String t = getText();
        g.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }
}

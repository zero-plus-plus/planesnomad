package com.planesnomad.gui.ui.theme;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/** A panel whose background is a themed element (see {@link Skin}), e.g. "panel" or "column". */
public class SkinPanel extends JPanel {

    private final String element;

    public SkinPanel(String element, LayoutManager layout) {
        super(layout);
        this.element = element;
        setOpaque(true);
    }

    public String element() {
        return element;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Skin.paint((Graphics2D) g, element, 0, 0, getWidth(), getHeight());
    }
}

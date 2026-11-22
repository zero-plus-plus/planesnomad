package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** A small art thumbnail usable as a Swing icon (e.g. the top card of a graveyard). */
public final class ArtIcon implements Icon {

    private final CardSnapshot card;
    private final int width;
    private final int height;

    public ArtIcon(CardSnapshot card, int width, int height) {
        this.card = card;
        this.width = width;
        this.height = height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        CardPainter.paintArt((Graphics2D) g, card, x, y, width, height);
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }
}

package com.planesnomad.gui.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.FlowLayout;

/**
 * "How many?" dialog for a pile of identical cards, e.g. choosing 37 of 200 goblins to attack.
 * A number box and a slider kept in step, plus quick buttons (none, 1, half, all but one, all).
 */
public final class PileCountPicker {

    /**
     * @return the chosen number (0..count), or null if cancelled
     */
    public static Integer ask(Component parent, String cardName, int count, int current) {
        SpinnerNumberModel model = new SpinnerNumberModel(Math.max(0, Math.min(current, count)), 0, count, 1);
        JSpinner spinner = new JSpinner(model);
        JSlider slider = new JSlider(0, count, model.getNumber().intValue());
        spinner.addChangeListener(e -> slider.setValue(model.getNumber().intValue()));
        slider.addChangeListener(e -> model.setValue(slider.getValue()));

        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        int[] values = {0, 1, count / 2, count - 1, count};
        String[] labels = {"None", "1", "Half (" + count / 2 + ")", "All but one", "All (" + count + ")"};
        for (int i = 0; i < values.length; i++) {
            int v = values[i];
            if (i > 0 && values[i] == values[i - 1]) {
                continue;   // small piles: skip duplicate buttons
            }
            JButton b = new JButton(labels[i]);
            b.addActionListener(e -> model.setValue(v));
            quick.add(b);
        }

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(spinner);
        row.add(new JLabel("of " + count));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JLabel title = new JLabel("How many " + cardName + "?");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        quick.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(row);
        panel.add(slider);
        panel.add(quick);

        int answer = JOptionPane.showConfirmDialog(parent, panel, "Select cards", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        try {
            spinner.commitEdit();   // a number typed but not yet confirmed with Enter
        } catch (java.text.ParseException ignored) {
            // keep the last valid value
        }
        return model.getNumber().intValue();
    }

    private PileCountPicker() { }
}

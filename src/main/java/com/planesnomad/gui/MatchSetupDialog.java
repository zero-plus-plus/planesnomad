package com.planesnomad.gui;

import com.planesnomad.gui.model.PlayerLook;
import com.planesnomad.match.DeckLibrary;
import com.planesnomad.match.MatchSetup;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Developer controls: edit the match setup (both sides' decks, who plays, starting life,
 * relics / effects, looks) and start a match with it. A tool for testing, not part of the game's
 * own screens: in the game these come from the run (your deck and relics) and the encounter
 * (the enemy).
 */
public final class MatchSetupDialog extends JDialog {

    private final SideEditor you;
    private final SideEditor enemy;
    private MatchSetup result;

    /**
     * @param cardExists checks card names typed for portraits, playmats and featured cards
     */
    public MatchSetupDialog(JFrame owner, MatchSetup current, DeckLibrary decks, List<String> commandCards,
                            Predicate<String> cardExists) {
        super(owner, "Dev: match setup", true);
        you = new SideEditor("You (bottom)", current.you(), decks, commandCards, true);
        enemy = new SideEditor("Enemy (top)", current.enemy(), decks, commandCards, false);
        JPanel sides = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        sides.add(you.panel);
        sides.add(enemy.panel);
        sides.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));

        JButton reload = new JButton("Reload decks folder");
        reload.setToolTipText("Reads .dck files from " + DeckLibrary.folder().toAbsolutePath().normalize());
        reload.addActionListener(e -> {
            decks.reload();
            you.refreshDecks(decks);
            enemy.refreshDecks(decks);
            if (!decks.problems().isEmpty()) {
                JOptionPane.showMessageDialog(this, String.join("\n", decks.problems()), "Decks", JOptionPane.WARNING_MESSAGE);
            }
        });
        JButton start = new JButton("Start match");
        start.addActionListener(e -> {
            // Who goes first and each side's start (preset hand, bonus permanents) are kept as they were.
            MatchSetup s = new MatchSetup(you.read(), enemy.read(), current.firstPlayer());
            List<String> problems = new ArrayList<>();
            for (MatchSetup.Side side : List.of(s.you(), s.enemy())) {
                for (String n : namesIn(side.look())) {
                    if (!n.isBlank() && !cardExists.test(n)) {
                        problems.add(side.name() + ": no card named \"" + n + "\"");
                    }
                }
            }
            if (s.you().name().equals(s.enemy().name())) {
                problems.add("The two players need different names");
            }
            if (!problems.isEmpty()) {
                JOptionPane.showMessageDialog(this, String.join("\n", problems), "Match setup", JOptionPane.WARNING_MESSAGE);
                return;
            }
            result = s;
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(new JLabel("Decks folder: " + DeckLibrary.folder().toAbsolutePath().normalize() + "   "));
        buttons.add(reload);
        buttons.add(cancel);
        buttons.add(start);
        getContentPane().add(sides, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(start);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the new setup, or null if cancelled. */
    public MatchSetup ask() {
        setVisible(true);
        return result;
    }

    private static List<String> namesIn(PlayerLook look) {
        List<String> n = new ArrayList<>();
        if (look.portraitCard() != null) n.add(look.portraitCard());
        if (look.playmatCard() != null) n.add(look.playmatCard());
        n.addAll(look.featuredCards());
        return n;
    }

    /** The fields for one side. */
    private static final class SideEditor {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JTextField name = new JTextField(16);
        final JComboBox<String> controller = new JComboBox<>(new String[] {"You play it", "AI"});
        final JComboBox<String> deck = new JComboBox<>();
        final JSpinner life = new JSpinner(new SpinnerNumberModel(20, 1, 999, 1));
        final DefaultListModel<String> commandModel = new DefaultListModel<>();
        final JList<String> command = new JList<>(commandModel);
        final JTextField subtitle = new JTextField(22);
        final JTextField portrait = new JTextField(22);
        final JTextField playmat = new JTextField(22);
        final JTextField featured = new JTextField(22);
        private final MatchSetup.Side original;

        SideEditor(String title, MatchSetup.Side s, DeckLibrary decks, List<String> commandCards, boolean bottom) {
            original = s;
            panel.setBorder(BorderFactory.createTitledBorder(title));
            name.setText(s.name());
            controller.setSelectedIndex(s.controller() == MatchSetup.Controller.PERSON ? 0 : 1);
            if (!bottom) {
                controller.setSelectedIndex(1);
                controller.setEnabled(false);   // the enemy is always the AI
            }
            refreshDecks(decks);
            deck.setSelectedItem(s.deck());
            life.setValue(s.startingLife());
            commandCards.forEach(commandModel::addElement);
            command.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            command.setVisibleRowCount(6);
            List<Integer> picked = new ArrayList<>();
            for (int i = 0; i < commandModel.size(); i++) {
                if (s.commandCards().contains(commandModel.get(i))) {
                    picked.add(i);
                }
            }
            command.setSelectedIndices(picked.stream().mapToInt(Integer::intValue).toArray());
            subtitle.setText(s.look().subtitle());
            portrait.setText(s.look().portraitCard() == null ? "" : s.look().portraitCard());
            playmat.setText(s.look().playmatCard() == null ? "" : s.look().playmatCard());
            featured.setText(String.join(", ", s.look().featuredCards()));

            int row = 0;
            row = add("Name", name, row);
            row = add("Played by", controller, row);
            row = add("Deck", deck, row);
            row = add("Starting life", life, row);
            row = add(bottom ? "Relics & consumables" : "Effects", new JScrollPane(command), row);
            row = add("Theme text", subtitle, row);
            row = add("Portrait (card name)", portrait, row);
            row = add("Playmat (card name)", playmat, row);
            add(bottom ? "Featured cards" : "Guaranteed cards (display)", featured, row);
            command.setToolTipText("Ctrl+click to choose several; these start in the command zone");
            featured.setToolTipText("Card names, separated by commas");
        }

        private int add(String label, java.awt.Component field, int row) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row;
            c.insets = new Insets(3, 4, 3, 4);
            c.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel(label), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            panel.add(field, c);
            return row + 1;
        }

        void refreshDecks(DeckLibrary decks) {
            Object was = deck.getSelectedItem();
            deck.removeAllItems();
            decks.names().forEach(deck::addItem);
            if (was != null) {
                deck.setSelectedItem(was);
            }
        }

        MatchSetup.Side read() {
            List<String> feat = Arrays.stream(featured.getText().split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
            PlayerLook look = new PlayerLook(subtitle.getText().trim(), blankToNull(portrait.getText()),
                blankToNull(playmat.getText()), feat);
            return new MatchSetup.Side(name.getText().trim().isEmpty() ? original.name() : name.getText().trim(),
                controller.getSelectedIndex() == 0 ? MatchSetup.Controller.PERSON : MatchSetup.Controller.AI,
                (String) deck.getSelectedItem(), (Integer) life.getValue(), command.getSelectedValuesList(), look,
                original.start());
        }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s.trim();
        }
    }
}

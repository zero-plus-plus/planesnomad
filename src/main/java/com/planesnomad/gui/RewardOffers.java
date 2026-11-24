package com.planesnomad.gui;

import com.planesnomad.encounter.Rewards;
import com.planesnomad.gui.engine.SnapshotBuilder;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;
import com.planesnomad.gui.ui.RewardScreen;
import com.planesnomad.relic.RelicCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Turns an encounter's {@link Rewards} into the rows of the reward screen (rolling the card offer). */
public final class RewardOffers {

    public static List<RewardScreen.Reward> roll(Rewards rewards, Random random) {
        List<RewardScreen.Reward> out = new ArrayList<>();
        if (rewards.gold() > 0) {
            out.add(RewardScreen.Reward.gold(rewards.gold()));
        }
        List<CardSnapshot> offer = new ArrayList<>();
        for (String name : rewards.rollCards(random)) {
            CardSnapshot c = SnapshotBuilder.named(name);
            if (c != null) {
                offer.add(c);
            }
        }
        if (!offer.isEmpty()) {
            out.add(RewardScreen.Reward.cards(offer));
        }
        for (String name : rewards.relics()) {
            RelicSnapshot r = relic(name);
            if (r != null) {
                out.add(RewardScreen.Reward.relic(r));
            }
        }
        return out;
    }

    /** A relic as shown on screens outside a match (name, description from its card, icon). */
    public static RelicSnapshot relic(String name) {
        var info = RelicCatalog.find(name).orElse(null);
        if (info == null) {
            return null;
        }
        CardSnapshot card = SnapshotBuilder.named(name);
        String text = info.description() != null ? info.description() : card == null ? "" : card.rulesText();
        return new RelicSnapshot(name, text, info.kind(), RelicSnapshot.State.IDLE, info.color(), info.glyph(), info.icon());
    }

    private RewardOffers() { }
}

package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Graven Cairns
 * Land
 *
 * {T}: Add {C}.
 * {B/R}, {T}: Add {B}{B}, {B}{R}, or {R}{R}.
 *
 * Filter land: the colored ability's cost is the hybrid symbol {B/R} (payable with either color —
 * 2007-05-01 ruling: "{(b/r)}, {T} is the same as saying {B}, {T} or {R}, {T}") plus tap, and
 * filters that one mana into two of the player's choice among the three listed combinations. Each
 * combination is its own activated ability — mirroring how Karplusan Forest's "Add {R} or {G}"
 * is already split into fixed-output abilities in this codebase — so the player picks by clicking
 * rather than resolving a runtime choice. `Costs.Mana("{B/R}")` (already exercised by an activated
 * ability on Trostani, Three Whispers) charges the hybrid symbol as part of activation, same as any
 * other ability cost.
 */
val GravenCairns = card("Graven Cairns") {
    typeLine = "Land"
    colorIdentity = "BR"
    oracleText = "{T}: Add {C}.\n{B/R}, {T}: Add {B}{B}, {B}{R}, or {R}{R}."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{B/R}"), Costs.Tap)
        effect = Effects.AddMana(Color.BLACK, 2)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{B/R}"), Costs.Tap)
        effect = Effects.AddMana(Color.BLACK).then(Effects.AddMana(Color.RED))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{B/R}"), Costs.Tap)
        effect = Effects.AddMana(Color.RED, 2)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "175"
        artist = "Anthony S. Waters"
        flavorText = "Shamans of Kar-Sengir claim that their sun sets because it can no longer bear " +
            "the gaze of those pain-carved cliffs."
        imageUri = "https://cards.scryfall.io/normal/front/8/1/81e7d329-ef6a-45f3-82b6-37a3606c00bc.jpg?1783943089"
    }
}

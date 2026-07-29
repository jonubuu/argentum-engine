package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Horizon Canopy
 * Land
 *
 * {T}, Pay 1 life: Add {G} or {W}.
 * {1}, {T}, Sacrifice this land: Draw a card.
 *
 * "Horizon" land: two fixed-output mana abilities (color choice split like Karplusan Forest) plus a
 * non-mana sacrifice ability that draws a card, mirroring Mana Confluence's `Costs.PayLife(1)` pattern.
 */
val HorizonCanopy = card("Horizon Canopy") {
    typeLine = "Land"
    colorIdentity = "GW"
    oracleText = "{T}, Pay 1 life: Add {G} or {W}.\n{1}, {T}, Sacrifice this land: Draw a card."

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.PayLife(1))
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.PayLife(1))
        effect = Effects.AddMana(Color.WHITE)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap, Costs.SacrificeSelf)
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "177"
        artist = "Michael Komarck"
        flavorText = "The great leaves are resilient underfoot. Heavy steps do not bruise them, but " +
            "release a sweet and spicy scent."
        imageUri = "https://cards.scryfall.io/normal/front/d/5/d5dfc25d-a17b-4ead-9484-e8a18b8fa176.jpg?1783943088"
    }
}

package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Grove of the Burnwillows
 * Land
 *
 * {T}: Add {C}.
 * {T}: Add {R} or {G}. Each opponent gains 1 life.
 *
 * Painland shape (Karplusan Forest sibling) with a life-gain rider instead of self-damage: the
 * combined "Add {R} or {G}" oracle line is split into two separate mana abilities, one fixed
 * color each, so the player picks a color by clicking the ability rather than resolving a
 * runtime color choice. Neither the colorless nor colored ability targets, so both stay legal
 * mana abilities (CR 605.1a) despite the `EachOpponent` life-gain rider.
 */
val GroveOfTheBurnwillows = card("Grove of the Burnwillows") {
    typeLine = "Land"
    colorIdentity = "RG"
    oracleText = "{T}: Add {C}.\n{T}: Add {R} or {G}. Each opponent gains 1 life."

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.RED)
            .then(Effects.GainLife(1, EffectTarget.PlayerRef(Player.EachOpponent)))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.GREEN)
            .then(Effects.GainLife(1, EffectTarget.PlayerRef(Player.EachOpponent)))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "176"
        artist = "David Hudnut"
        flavorText = "Spring is the most beautiful season in the grove, when the new leaves open " +
            "from their ember-buds in a race of leaping flames."
        imageUri = "https://cards.scryfall.io/normal/front/e/0/e0b4d4b1-6e25-4c4b-a21a-1b7b1c1d6452.jpg?1783943089"
    }
}

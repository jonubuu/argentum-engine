package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Street Wraith
 * {3}{B}{B}
 * Creature — Wraith
 * 3/4
 *
 * Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
 * Cycling—Pay 2 life. (Pay 2 life, Discard this card: Draw a card.)
 *
 * Cycling paid with life instead of mana — [KeywordAbility.cyclingPayLife] generalizes the
 * Cycling keyword's cost from a bare mana cost to an [com.wingedsheep.sdk.scripting.AbilityCost],
 * reusing the same `CostAtom.PayLife` vocabulary every activated-ability life cost already uses
 * (Mana Confluence, Horizon Canopy) rather than approximating it as a mana cost of {0}.
 */
val StreetWraith = card("Street Wraith") {
    manaCost = "{3}{B}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Wraith"
    oracleText = "Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)\n" +
        "Cycling—Pay 2 life. (Pay 2 life, Discard this card: Draw a card.)"
    power = 3
    toughness = 4

    keywords(Keyword.SWAMPWALK)
    keywordAbility(KeywordAbility.cyclingPayLife(2))

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "90"
        artist = "Cyril Van Der Haegen"
        flavorText = "The lamps on Wyndmoor Street snuff themselves at midnight and refuse to relight, " +
            "afraid to illuminate what lies in the darkness."
        imageUri = "https://cards.scryfall.io/normal/front/6/7/672e2815-bbcc-4338-a8ba-9aa97142ea69.jpg?1783943108"
    }
}

package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.Aggregation
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Tarmogoyf
 * {1}{G}
 * Creature — Lhurgoyf
 * Power/toughness: star, 1 + star
 *
 * Tarmogoyf's power is equal to the number of card types among cards in all graveyards and its
 * toughness is equal to that number plus 1.
 *
 * Characteristic-defining power/toughness (CR 604.3 — applies in every zone, not just the
 * battlefield): `dynamicStats` over `DynamicAmount.AggregateZone(Player.Each, Zone.GRAVEYARD,
 * aggregation = Aggregation.DISTINCT_TYPES)`, the exact construct documented as this card's
 * canonical example in `CardBuilder.dynamicStats`. `Player.Each` unions both graveyards into one
 * count (the ability reads "all graveyards", not "your graveyard"); `DISTINCT_TYPES` counts each
 * of the nine card types at most once no matter how many matching cards share it (2021-03-19
 * ruling). No `excludeSelf` is needed — `AggregateZone` scans the zone's live contents, so
 * Tarmogoyf counts itself once it's actually sitting in a graveyard, matching the ruling that its
 * ability works in all zones.
 */
val Tarmogoyf = card("Tarmogoyf") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Lhurgoyf"
    dynamicStats(
        DynamicAmount.AggregateZone(Player.Each, Zone.GRAVEYARD, aggregation = Aggregation.DISTINCT_TYPES),
        toughnessOffset = 1,
    )
    oracleText = "Tarmogoyf's power is equal to the number of card types among cards in all " +
        "graveyards and its toughness is equal to that number plus 1."

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "153"
        artist = "Justin Murray"
        imageUri = "https://cards.scryfall.io/normal/front/b/6/b6876d9e-0908-43ac-8542-09c7aa02b5ba.jpg?1783943094"
        ruling(
            "2021-03-19",
            "The ability that defines Tarmogoyf's power and toughness works in all zones, not just " +
                "the battlefield. If Tarmogoyf is in your graveyard, it will count itself.",
        )
        ruling(
            "2021-03-19",
            "Tarmogoyf counts card types, not cards. If the only card in all graveyards is a single " +
                "artifact creature, Tarmogoyf will be 2/3. If the only cards in all graveyards are " +
                "ten artifact creatures, Tarmogoyf will still be 2/3.",
        )
        ruling(
            "2021-03-19",
            "The card types that can appear on cards in a graveyard are artifact, battle, creature, " +
                "enchantment, instant, kindred, land, planeswalker, and sorcery. Legendary, basic, " +
                "and snow are supertypes, not card types.",
        )
        ruling(
            "2021-03-19",
            "If an instant or sorcery spell deals damage to Tarmogoyf or lowers its toughness, that " +
                "spell is put into its owner's graveyard before state-based actions are performed. " +
                "If that card is the first of its type to enter a graveyard, it will raise " +
                "Tarmogoyf's toughness before the game checks to see if Tarmogoyf dies.",
        )
    }
}

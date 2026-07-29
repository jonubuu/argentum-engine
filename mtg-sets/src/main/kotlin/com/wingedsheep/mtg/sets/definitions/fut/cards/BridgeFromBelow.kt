package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern.ZoneChangeEvent
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggerSpec
import com.wingedsheep.sdk.scripting.effects.Gate
import com.wingedsheep.sdk.scripting.effects.GatedEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Bridge from Below
 * {B}{B}{B}
 * Enchantment
 *
 * Whenever a nontoken creature is put into your graveyard from the battlefield, if this card is
 * in your graveyard, create a 2/2 black Zombie creature token.
 * When a creature is put into an opponent's graveyard from the battlefield, if this card is in
 * your graveyard, exile this card.
 *
 * Both abilities are `triggerZone = Zone.GRAVEYARD` (functions only from the graveyard — on the
 * battlefield it does nothing, per ruling). "Your"/"an opponent's" graveyard is about which
 * player *owns* the dying creature, not who controlled it (ruling: "Neither ability cares who
 * controlled the creature that died, only which graveyard it was put into"), hence
 * `.ownedByYou()` / `.ownedByOpponent()` rather than a control filter.
 *
 * The printed "if this card is in your graveyard" on both abilities is not redundant with
 * `triggerZone` — that only gates *detection*; nothing re-checks a triggered ability's source
 * once it's on the stack (CR 112.7a). Since the two abilities can trigger off the same
 * simultaneous deaths and the second one exiles this card, the ruling ("you choose the order...
 * you can create a token before you exile Bridge from Below") only holds if resolving the exile
 * first stops the token from being created — hence `Gate.WhenCondition(Conditions.SourceInZone
 * (Zone.GRAVEYARD))` wraps both payoffs as the CR 603.4a resolution-time recheck.
 */
val BridgeFromBelow = card("Bridge from Below") {
    manaCost = "{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Enchantment"
    oracleText = "Whenever a nontoken creature is put into your graveyard from the battlefield, " +
        "if this card is in your graveyard, create a 2/2 black Zombie creature token.\n" +
        "When a creature is put into an opponent's graveyard from the battlefield, if this card " +
        "is in your graveyard, exile this card."

    triggeredAbility {
        triggerZone = Zone.GRAVEYARD
        trigger = TriggerSpec(
            ZoneChangeEvent(
                filter = GameObjectFilter.Creature.nontoken().ownedByYou(),
                from = Zone.BATTLEFIELD,
                to = Zone.GRAVEYARD
            ),
            TriggerBinding.ANY
        )
        effect = GatedEffect(
            gate = Gate.WhenCondition(Conditions.SourceInZone(Zone.GRAVEYARD)),
            then = Effects.CreateToken(
                power = 2,
                toughness = 2,
                colors = setOf(Color.BLACK),
                creatureTypes = setOf("Zombie"),
                imageUri = "https://cards.scryfall.io/normal/front/1/7/17f001ab-514b-49e7-a657-b2872ad7a1de.jpg?1783904333"
            )
        )
        description = "Whenever a nontoken creature is put into your graveyard from the " +
            "battlefield, if this card is in your graveyard, create a 2/2 black Zombie creature token."
    }

    triggeredAbility {
        triggerZone = Zone.GRAVEYARD
        trigger = TriggerSpec(
            ZoneChangeEvent(
                filter = GameObjectFilter.Creature.ownedByOpponent(),
                from = Zone.BATTLEFIELD,
                to = Zone.GRAVEYARD
            ),
            TriggerBinding.ANY
        )
        effect = GatedEffect(
            gate = Gate.WhenCondition(Conditions.SourceInZone(Zone.GRAVEYARD)),
            then = Effects.Move(EffectTarget.Self, Zone.EXILE)
        )
        description = "When a creature is put into an opponent's graveyard from the battlefield, " +
            "if this card is in your graveyard, exile this card."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "81"
        artist = "Greg Hildebrandt & Tim Hildebrandt"
        imageUri = "https://cards.scryfall.io/normal/front/5/2/52c44610-6d4b-4c14-839f-2c085badec90.jpg?1783943111"
    }
}

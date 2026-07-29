package com.wingedsheep.engine.legalactions.enumerators

import com.wingedsheep.engine.core.CycleCard
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.legalactions.ActionEnumerator
import com.wingedsheep.engine.legalactions.EnumerationContext
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom

/**
 * Enumerates cycling and typecycling actions for cards in hand.
 *
 * CastSpell actions for these cards are emitted by [CastSpellEnumerator]; when timing
 * prevents a cast, the client renders a greyed-out "Cast" entry alongside the cycle
 * option so the player always sees both choices.
 */
class CyclingEnumerator : ActionEnumerator {

    override fun enumerate(context: EnumerationContext): List<LegalAction> {
        val result = mutableListOf<LegalAction>()
        val state = context.state
        val playerId = context.playerId
        if (context.cyclingPrevented) return result

        val hand = state.getHand(playerId)
        for (cardId in hand) {
            val cardComponent = state.getEntity(cardId)?.get<CardComponent>() ?: continue
            val cardDef = context.cardRegistry.getCard(cardComponent.name) ?: continue

            val cyclingAbilities = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Cycling>()
            val plainCycling = cyclingAbilities.firstOrNull { it.searchFilter == null }
            val typedCycling = cyclingAbilities.firstOrNull { it.searchFilter != null }

            if (plainCycling != null) {
                val manaCost = manaCostOf(plainCycling.cost)
                val lifeCost = payLifeAmountOf(plainCycling.cost)
                val (affordable, manaCostString, autoTapPreview) = when {
                    manaCost != null -> Triple(
                        context.manaSolver.canPay(state, playerId, manaCost, precomputedSources = context.availableManaSources),
                        manaCost.toString(),
                        if (context.skipAutoTapPreview) null else {
                            context.manaSolver.solve(state, playerId, manaCost, precomputedSources = context.availableManaSources)
                                ?.sources?.map { it.entityId }
                        }
                    )
                    lifeCost != null -> Triple(state.lifeTotal(playerId) >= lifeCost, null, null)
                    else -> Triple(false, null, null)
                }
                result.add(
                    LegalAction(
                        actionType = "CycleCard",
                        description = "Cycle ${cardComponent.name}",
                        action = CycleCard(playerId, cardId),
                        affordable = affordable,
                        manaCostString = manaCostString,
                        autoTapPreview = autoTapPreview
                    )
                )
            }

            if (typedCycling != null) {
                // Every printed typecycling ability (Swampcycling, Slivercycling, ...) is a mana
                // cost; a non-mana Cycling.cost here means this variant isn't offered.
                val cost = manaCostOf(typedCycling.cost)
                if (cost != null) {
                    val description = "${typedCycling.displayPrefix} ${cardComponent.name}"
                    val canAfford = context.manaSolver.canPay(state, playerId, cost, precomputedSources = context.availableManaSources)
                    val autoTapPreview = if (context.skipAutoTapPreview) null else {
                        context.manaSolver.solve(state, playerId, cost, precomputedSources = context.availableManaSources)
                            ?.sources?.map { it.entityId }
                    }
                    result.add(
                        LegalAction(
                            actionType = "TypecycleCard",
                            description = description,
                            action = TypecycleCard(playerId, cardId),
                            affordable = canAfford,
                            manaCostString = cost.toString(),
                            autoTapPreview = autoTapPreview
                        )
                    )
                }
            }
        }

        return result
    }

    /** Every printed mana-cost cycling ability is a bare [CostAtom.Mana] atom (e.g. "Cycling {2}"). */
    private fun manaCostOf(cost: AbilityCost) =
        (cost as? AbilityCost.Atom)?.atom?.let { it as? CostAtom.Mana }?.cost

    /** Life-paid cycling (Street Wraith: "Cycling—Pay 2 life") is a bare [CostAtom.PayLife] atom. */
    private fun payLifeAmountOf(cost: AbilityCost) =
        (cost as? AbilityCost.Atom)?.atom?.let { it as? CostAtom.PayLife }?.amount
}

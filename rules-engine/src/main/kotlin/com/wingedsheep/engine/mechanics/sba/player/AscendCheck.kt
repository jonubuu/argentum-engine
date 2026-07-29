package com.wingedsheep.engine.mechanics.sba.player

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.mechanics.blessing.CitysBlessingService
import com.wingedsheep.engine.mechanics.sba.SbaOrder
import com.wingedsheep.engine.mechanics.sba.StateBasedActionCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId

/**
 * CR 702.131b — Ascend on a permanent represents a static ability: "Any time you control ten or
 * more permanents and you don't have the city's blessing, you get the city's blessing for the rest
 * of the game." (The instant/sorcery form is CR 702.131a instead — a one-shot resolution-time
 * check, handled by [com.wingedsheep.engine.handlers.effects.player.GainCitysBlessingExecutor] and
 * unrelated to this check.)
 *
 * Modeled as a state-based-action-shaped continuous recheck, following the exact precedent
 * [StartYourEnginesCheck] set for translating a "continuously true static ability" into this
 * engine's SBA loop — not a per-card triggered ability, because crossing ten permanents has no
 * event of its own to hang a trigger on, and it can happen well after the Ascend permanent's own
 * entry (more lands, more creatures cast later, even a token this same permanent's own end-step
 * ability creates). A one-shot `Triggers.EntersBattlefield` check — which is what every Ascend card
 * in this codebase used before this — misses all of that.
 *
 * Being an SBA rather than a trigger is load-bearing the same way it is for
 * [StartYourEnginesCheck]:
 *
 * - Gaining control of an opponent's Ascend permanent (or that permanent being granted Ascend at
 *   runtime) grants *your* blessing once you cross ten, with no trigger to write for either case —
 *   the keyword is read from projected state (Layer 6), not the printed card.
 * - It's idempotent ([CitysBlessingService.grant] no-ops once a player already has the blessing)
 *   and runs to fixpoint inside the SBA loop, so several Ascend permanents under one controller
 *   still grant the blessing exactly once.
 * - CR 702.131d ("continuous effects are reapplied before the game checks... trigger conditions")
 *   falls out for free: this engine re-derives `state.projectedState` from base state on demand, so
 *   any `ConditionalStaticAbility` gated on `Conditions.YouHaveCitysBlessing` (Tendershoot Dryad's
 *   Saproling anthem) sees the new [PlayerCitysBlessingComponent] the moment it's set — there's no
 *   separate "reapply continuous effects" step to sequence relative to this SBA.
 */
class AscendCheck : StateBasedActionCheck {
    override val name = "702.131b Ascend"
    override val order = SbaOrder.ASCEND

    override fun check(state: GameState): ExecutionResult {
        var newState = state
        val events = mutableListOf<GameEvent>()

        for (playerId in playersControllingAscend(state)) {
            if (newState.getEntity(playerId)?.has<PlayerCitysBlessingComponent>() == true) continue
            if (countPermanentsControlledBy(newState, playerId) < 10) continue

            val (updated, blessingEvents) = CitysBlessingService.grant(
                state = newState,
                playerId = playerId,
                sourceName = Keyword.ASCEND.displayName
            )
            newState = updated
            events.addAll(blessingEvents)
        }

        return ExecutionResult.success(newState, events)
    }

    /**
     * Controllers of battlefield permanents with Ascend, read from projected state so a *granted*
     * Ascend (or a control-changing effect) is honored the same as a printed one.
     *
     * A set, not a list: several Ascend permanents under one controller check the threshold once.
     */
    private fun playersControllingAscend(state: GameState): Set<EntityId> {
        val projected = state.projectedState
        val controllers = mutableSetOf<EntityId>()
        for (entityId in state.getBattlefield()) {
            if (!projected.hasKeyword(entityId, Keyword.ASCEND)) continue
            projected.getController(entityId)?.let { controllers.add(it) }
        }
        return controllers
    }

    /** Total permanents (any type) [playerId] currently controls, per projected state. */
    private fun countPermanentsControlledBy(state: GameState, playerId: EntityId): Int {
        val projected = state.projectedState
        return state.getBattlefield().count { projected.getController(it) == playerId }
    }
}

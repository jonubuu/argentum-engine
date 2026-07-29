package com.wingedsheep.engine.mechanics.blessing

import com.wingedsheep.engine.core.CitysBlessingGainedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Grants the city's blessing (CR 702.131c) to a player.
 *
 * Idempotent: a player who already has it is unaffected and no event fires — CR 702.131c makes the
 * blessing a permanent designation for the rest of the game, so granting it again is a no-op, not a
 * re-trigger.
 *
 * Shared by the two ways a player can get it:
 *  - [com.wingedsheep.engine.handlers.effects.player.GainCitysBlessingExecutor] — the instant/sorcery
 *    spell-ability form (CR 702.131a), a one-shot resolution-time check.
 *  - [com.wingedsheep.engine.mechanics.sba.player.AscendCheck] — the permanent static-ability form
 *    (CR 702.131b), a continuous "any time" check run every state-based-action pass.
 *
 * so both paths agree on exactly one notion of "gaining the blessing" (mirrors
 * [com.wingedsheep.engine.mechanics.speed.SpeedService], shared the same way between
 * `ChangeSpeedExecutor` and `StartYourEnginesCheck`).
 */
object CitysBlessingService {
    fun grant(state: GameState, playerId: EntityId, sourceName: String): Pair<GameState, List<GameEvent>> {
        val playerContainer = state.getEntity(playerId) ?: return state to emptyList()
        if (playerContainer.has<PlayerCitysBlessingComponent>()) return state to emptyList()

        val newState = state.updateEntity(playerId) { it.with(PlayerCitysBlessingComponent) }
        val playerName = playerContainer.get<PlayerComponent>()?.name ?: "Player"
        return newState to listOf(CitysBlessingGainedEvent(playerId, playerName, sourceName))
    }
}

package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.blessing.CitysBlessingService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.GainCitysBlessingEffect
import kotlin.reflect.KClass

/**
 * Resolves [GainCitysBlessingEffect] — the instant/sorcery spell-ability form of Ascend
 * (CR 702.131a), a one-shot resolution-time check. [CitysBlessingService] does the actual granting
 * (idempotent per CR 702.131c) and is shared with the permanent static-ability form
 * (CR 702.131b, [com.wingedsheep.engine.mechanics.sba.player.AscendCheck]).
 */
class GainCitysBlessingExecutor : EffectExecutor<GainCitysBlessingEffect> {

    override val effectType: KClass<GainCitysBlessingEffect> = GainCitysBlessingEffect::class

    override fun execute(
        state: GameState,
        effect: GainCitysBlessingEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid target for city's blessing grant")

        if (!state.turnOrder.contains(targetId)) {
            return EffectResult.error(state, "City's blessing target must be a player")
        }

        if (state.getEntity(targetId) == null) {
            return EffectResult.error(state, "Target player no longer exists")
        }

        val sourceName = context.sourceId?.let {
            state.getEntity(it)?.get<CardComponent>()?.name
        } ?: "Unknown"

        val (newState, events) = CitysBlessingService.grant(state, targetId, sourceName)
        return EffectResult.success(newState, events)
    }
}

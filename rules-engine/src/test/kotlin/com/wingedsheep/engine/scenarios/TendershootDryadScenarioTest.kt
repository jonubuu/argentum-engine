package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Tendershoot Dryad (BLC #242) — {4}{G} Creature — Dryad, 2/2.
 *
 * "Ascend (If you control ten or more permanents, you get the city's blessing for the rest of the
 * game.)
 * At the beginning of each upkeep, create a 1/1 green Saproling creature token.
 * Saprolings you control get +2/+2 as long as you have the city's blessing."
 *
 * Scoped to the Ascend / city's blessing / anthem interaction — the part [AscendCheck] (CR 702.131b)
 * fixed — not the upkeep token trigger, which is an unrelated plain `Triggers.EachUpkeep` (a
 * separate test Saproling stands in here, so the anthem is exercised without needing to navigate
 * through an upkeep step just to get one onto the battlefield). Proves: the same continuous check
 * that fixed Ocelot Pride also powers a *different* card's static ability correctly, and that CR
 * 702.131d's ordering (continuous effects reapply immediately once the blessing is granted) holds
 * with no special-case code — this engine re-derives `state.projectedState` from base state on
 * demand, so the anthem sees the new [PlayerCitysBlessingComponent] the instant it's set.
 */
class TendershootDryadScenarioTest : ScenarioTestBase() {

    // A plain Saproling to stand in for the token Tendershoot Dryad's upkeep trigger would create —
    // isolates the anthem/blessing interaction from that unrelated trigger.
    private val testSaproling = card("Test Saproling") {
        manaCost = "{G}"
        typeLine = "Creature — Saproling"
        power = 1
        toughness = 1
    }

    // Cheap filler spell used only to force the engine's next SBA-check poll point (SBAs run
    // "after a spell or ability resolves" in this engine, not after every single game action — see
    // AscendCheck's / StartYourEnginesCheck's own doc comments, and OcelotPrideScenarioTest's
    // equivalent test).
    private val fillerSpell = card("Test Filler") {
        manaCost = "{G}"
        typeLine = "Sorcery"
        spell { effect = Effects.GainLife(0) }
    }

    init {
        cardRegistry.register(testSaproling)
        cardRegistry.register(fillerSpell)

        context("Ascend-powered Saproling anthem") {

            test("without the city's blessing, Saprolings are the plain 1/1") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tendershoot Dryad", summoningSickness = false)
                    .withCardOnBattlefield(1, "Test Saproling")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val saproling = game.findPermanent("Test Saproling")!!
                withClue("no blessing -> the anthem doesn't apply, Saproling stays 1/1") {
                    game.state.projectedState.getPower(saproling) shouldBe 1
                    game.state.projectedState.getToughness(saproling) shouldBe 1
                }
            }

            test("crossing ten permanents grants the blessing and immediately pumps the existing Saproling") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tendershoot Dryad", summoningSickness = false)
                    .withCardOnBattlefield(1, "Test Saproling")
                    .withLandsOnBattlefield(1, "Forest", 7) // + Dryad + Saproling = 9 permanents
                    .withCardInHand(1, "Forest") // the 10th permanent
                    .withCardInHand(1, "Test Filler")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val saproling = game.findPermanent("Test Saproling")!!
                withClue("9 permanents — no blessing, Saproling still 1/1") {
                    game.state.getEntity(game.player1Id)?.has<PlayerCitysBlessingComponent>() shouldBe false
                    game.state.projectedState.getPower(saproling) shouldBe 1
                    game.state.projectedState.getToughness(saproling) shouldBe 1
                }

                val tenthLand = game.state.getHand(game.player1Id).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Forest"
                }
                game.execute(PlayLand(game.player1Id, tenthLand)).error shouldBe null

                val cast = game.castSpell(1, "Test Filler")
                withClue("Casting the filler spell should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("10 permanents -> blessing granted, and the SAME pre-existing Saproling is now 3/3") {
                    game.state.getEntity(game.player1Id)?.has<PlayerCitysBlessingComponent>() shouldBe true
                    game.state.projectedState.getPower(saproling) shouldBe 3
                    game.state.projectedState.getToughness(saproling) shouldBe 3
                }
            }
        }
    }
}

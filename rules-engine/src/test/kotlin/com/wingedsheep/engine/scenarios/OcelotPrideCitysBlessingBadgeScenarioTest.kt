package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Ocelot Pride (MH3 #38) — client visibility of the city's blessing.
 *
 * The city's blessing (CR 702.131c) is a player-level *designation*, not a permanent or token — it
 * has no battlefield presence at all (see [OcelotPrideScenarioTest]'s Ascend coverage). The only way
 * a player can actually see they have it is `ClientStateTransformer` surfacing
 * `PlayerCitysBlessingComponent` as a `ClientPlayerEffect` badge (`effectId = "citys_blessing"`) on
 * that player, mirroring the same pattern `AkawalliDescendBadgeScenarioTest` uses for a
 * card-level restriction badge — here at the *player* level instead of the card level. This pins
 * that the badge is absent below the Ascend threshold and appears once it's crossed, is shown with
 * the expected player-facing text, and doesn't leak onto the other player.
 */
class OcelotPrideCitysBlessingBadgeScenarioTest : ScenarioTestBase() {

    init {
        context("Ocelot Pride Ascend — city's blessing badge") {

            test("no badge before Ascend triggers (fewer than ten permanents)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ocelot Pride")
                    .withLandsOnBattlefield(1, "Plains", 8) // + Ocelot Pride itself = 9 permanents
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                withClue("no badge before Ocelot Pride even resolves") {
                    hasCitysBlessingBadge(game, 1) shouldBe false
                }

                val cast = game.castSpell(1, "Ocelot Pride")
                withClue("Casting Ocelot Pride should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("9 permanents total — below the Ascend threshold, still no badge") {
                    hasCitysBlessingBadge(game, 1) shouldBe false
                }
            }

            test("badge appears once Ascend grants the city's blessing (ten or more permanents)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ocelot Pride")
                    .withLandsOnBattlefield(1, "Plains", 9) // + Ocelot Pride itself = 10 permanents
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Ocelot Pride")
                withClue("Casting Ocelot Pride should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("10 permanents met the Ascend threshold — the badge is now shown") {
                    hasCitysBlessingBadge(game, 1) shouldBe true
                }

                val badge = game.getClientState(1).players.first { it.playerId == game.player1Id }
                    .activeEffects.first { it.effectId == "citys_blessing" }
                withClue("badge carries the player-facing name and description") {
                    badge.name shouldBe "City's Blessing"
                    badge.description shouldBe "You have the city's blessing for the rest of the game"
                }

                withClue("the opponent — who doesn't have the blessing — shows no badge") {
                    hasCitysBlessingBadge(game, 2) shouldBe false
                }
            }
        }
    }
}

private fun hasCitysBlessingBadge(game: ScenarioTestBase.TestGame, forPlayerNumber: Int): Boolean {
    val playerId = if (forPlayerNumber == 1) game.player1Id else game.player2Id
    return game.getClientState(1).players.first { it.playerId == playerId }
        .activeEffects.any { it.effectId == "citys_blessing" }
}

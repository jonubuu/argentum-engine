package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Street Wraith — Cycling—Pay 2 life (Future Sight). The cost is life, not mana (CR 702.29a), which
 * is why [com.wingedsheep.sdk.scripting.KeywordAbility.Cycling]'s cost was generalized from a bare
 * `ManaCost` to an `AbilityCost`: `CycleCardHandler` now charges a `CostAtom.PayLife` atom directly
 * instead of ever touching the mana pool or solver.
 */
class StreetWraithScenarioTest : ScenarioTestBase() {

    init {
        test("cycling pays 2 life, discards Street Wraith, and draws a card — no mana involved") {
            val game = wraithGame(startingLife = 20)
            val handBefore = game.handSize(1)

            val cycle = game.cycleCard(1, "Street Wraith")
            withClue("Cycling should succeed paying only life: ${cycle.error}") {
                cycle.error shouldBe null
            }

            withClue("Exactly 2 life paid") {
                game.getLifeTotal(1) shouldBe 18
            }
            withClue("Street Wraith discarded into the graveyard") {
                game.isInGraveyard(1, "Street Wraith") shouldBe true
            }
            withClue("A replacement card was drawn") {
                game.handSize(1) shouldBe handBefore
            }
        }

        test("cycling is illegal below the required life total") {
            val game = wraithGame(startingLife = 1)

            val cycle = game.cycleCard(1, "Street Wraith")
            withClue("1 life can't pay a 2-life cost") {
                (cycle.error != null) shouldBe true
            }
        }

        test("paying life down to exactly 0 is legal (CR 119.4)") {
            val game = wraithGame(startingLife = 2)

            val cycle = game.cycleCard(1, "Street Wraith")
            withClue("Paying exactly down to 0 is a legal payment: ${cycle.error}") {
                cycle.error shouldBe null
            }
            game.getLifeTotal(1) shouldBe 0
        }
    }

    private fun wraithGame(startingLife: Int): TestGame =
        scenario()
            .withPlayers("Player1", "Player2")
            .withCardInHand(1, "Street Wraith")
            .withCardInLibrary(1, "Grizzly Bears")
            .withLifeTotal(1, startingLife)
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            .build()
}

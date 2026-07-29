package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.fut.cards.DakmorSalvage
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Dakmor Salvage — Land, {T}: Add {B}, Dredge 2 (Future Sight).
 *
 * The land's own abilities (enters tapped, tap for black) are built from existing primitives and
 * covered by the snapshot/lint nets, so this file is the primary coverage for the Dredge mechanic
 * itself (CR 702.52), exercised through Dakmor Salvage and a second dredge card for the
 * multiple-eligible-cards and multi-draw-sequence rulings.
 *
 * Rules pinned here (Scryfall rulings on Stinkweed Imp, 2024-01-12):
 *  - CR 702.52a: mill N and return to hand *instead of* drawing — not itself a draw.
 *  - CR 702.52b: can't attempt to dredge without at least N cards in library.
 *  - "One card draw can't be replaced by multiple dredge abilities" — one offer per draw.
 *  - "Dredge can replace any card draw, not only the one during your draw step."
 *  - "If you're drawing multiple cards, each draw is performed one at a time... another card
 *    with a dredge ability (including one that was milled by the first dredge ability) may be
 *    used to replace the second draw."
 */
class DakmorSalvageScenarioTest : ScenarioTestBase() {

    /** A second dredge card (different N) for the "multiple eligible cards" scenarios. */
    private val testDredgeThree = card("Test Dredge Three") {
        manaCost = "{0}"
        typeLine = "Artifact"
        oracleText = "Dredge 3"
        keywordAbility(KeywordAbility.dredge(3))
    }

    /** A free "draw a card" spell for the single-draw scenarios. */
    private val testDrawOne = card("Test Draw One") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Draw a card."
        spell {
            effect = Effects.DrawCards(1)
        }
    }

    /** A free "draw two cards" spell to exercise the multi-draw-sequence ruling in isolation. */
    private val testDrawTwo = card("Test Draw Two") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Draw two cards."
        spell {
            effect = Effects.DrawCards(2)
        }
    }

    private fun ScenarioBuilder.withLibraryCards(playerNumber: Int, cardName: String, count: Int): ScenarioBuilder {
        repeat(count) { withCardInLibrary(playerNumber, cardName) }
        return this
    }

    init {
        cardRegistry.register(DakmorSalvage)
        cardRegistry.register(testDredgeThree)
        cardRegistry.register(testDrawOne)
        cardRegistry.register(testDrawTwo)

        test("choosing to dredge mills N, returns the card to hand, and does not draw") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Dakmor Salvage")
                .withCardInHand(1, "Test Draw One")
                .withLibraryCards(1, "Grizzly Bears", 10)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            val libraryBefore = game.librarySize(1)

            val cast = game.castSpell(1, "Test Draw One")
            withClue("Casting a free draw spell should succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            val decision = game.state.pendingDecision.shouldBeChooseOption()
            val dredgeIdx = decision.options.indexOfFirst { it.contains("Dakmor Salvage") }
            withClue("Dredge must be offered as an option") { (dredgeIdx >= 0) shouldBe true }

            game.submitDecision(OptionChosenResponse(decision.id, dredgeIdx))

            withClue("2 cards milled from library to graveyard") {
                game.librarySize(1) shouldBe libraryBefore - 2
            }
            withClue("Dakmor Salvage returned to hand instead of a card being drawn") {
                game.isInHand(1, "Dakmor Salvage") shouldBe true
                game.isInGraveyard(1, "Dakmor Salvage") shouldBe false
            }
            withClue("Hand grew by exactly 1 (the dredged card, not a draw)") {
                game.handSize(1) shouldBe handBefore
            }
        }

        test("declining dredge draws a card normally and leaves the dredge card in the graveyard") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Dakmor Salvage")
                .withCardInHand(1, "Test Draw One")
                .withLibraryCards(1, "Grizzly Bears", 10)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            val libraryBefore = game.librarySize(1)

            game.castSpell(1, "Test Draw One")
            game.resolveStack()

            val decision = game.state.pendingDecision.shouldBeChooseOption()
            val drawIdx = decision.options.indexOfFirst { it == "Draw a card" }

            game.submitDecision(OptionChosenResponse(decision.id, drawIdx))

            withClue("No cards milled") { game.librarySize(1) shouldBe libraryBefore - 1 }
            withClue("Dakmor Salvage stays in the graveyard") {
                game.isInGraveyard(1, "Dakmor Salvage") shouldBe true
            }
            withClue("Hand is back to its starting size: -1 for casting Test Draw One, +1 from the normal draw") {
                game.handSize(1) shouldBe handBefore
            }
        }

        test("CR 702.52b: dredge isn't offered without at least N cards in library") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Dakmor Salvage")
                .withCardInHand(1, "Test Draw One")
                .withLibraryCards(1, "Grizzly Bears", 1) // fewer than Dredge 2's required 2
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)

            val cast = game.castSpell(1, "Test Draw One")
            withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("No decision — library too small to dredge, so the draw just happens") {
                game.hasPendingDecision() shouldBe false
            }
            withClue("The draw happened normally — library too small to dredge") {
                game.isInGraveyard(1, "Dakmor Salvage") shouldBe true
                game.handSize(1) shouldBe handBefore
            }
        }

        test("multiple eligible dredge cards are offered in a single decision") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Dakmor Salvage")
                .withCardInGraveyard(1, "Test Dredge Three")
                .withCardInHand(1, "Test Draw One")
                .withLibraryCards(1, "Grizzly Bears", 10)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Test Draw One")
            game.resolveStack()

            val decision = game.state.pendingDecision.shouldBeChooseOption()
            withClue("Draw + both dredge cards offered as one combined decision") {
                decision.options.size shouldBe 3
            }
            val salvageIdx = decision.options.indexOfFirst { it.contains("Dakmor Salvage") }
            val threeIdx = decision.options.indexOfFirst { it.contains("Test Dredge Three") }

            game.submitDecision(OptionChosenResponse(decision.id, salvageIdx))

            withClue("Dredging one leaves the other untouched in the graveyard") {
                game.isInHand(1, "Dakmor Salvage") shouldBe true
                game.isInGraveyard(1, "Test Dredge Three") shouldBe true
            }
            (threeIdx >= 0) shouldBe true
        }

        test("CR 121.6b/rulings: a card milled by the first dredge can replace the second draw in the same instruction") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Dakmor Salvage")
                .withCardInHand(1, "Test Draw Two")
                // Library ordered so the mill from dredging Dakmor Salvage puts Test Dredge Three
                // into the graveyard, making it eligible for the second draw of the instruction.
                .withCardInLibrary(1, "Test Dredge Three")
                .withLibraryCards(1, "Grizzly Bears", 10)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Test Draw Two")
            game.resolveStack()

            // First draw: dredge Dakmor Salvage, milling Test Dredge Three into the graveyard.
            val first = game.state.pendingDecision.shouldBeChooseOption()
            val firstDredgeIdx = first.options.indexOfFirst { it.contains("Dakmor Salvage") }
            game.submitDecision(OptionChosenResponse(first.id, firstDredgeIdx))

            withClue("Test Dredge Three was milled by the first dredge") {
                game.isInGraveyard(1, "Test Dredge Three") shouldBe true
            }

            // Second draw of the same "draw two" instruction: the freshly-milled card is now offered.
            val second = game.state.pendingDecision.shouldBeChooseOption()
            val secondDredgeIdx = second.options.indexOfFirst { it.contains("Test Dredge Three") }
            withClue("The card milled a moment ago is offered for the second draw") {
                (secondDredgeIdx >= 0) shouldBe true
            }
            game.submitDecision(OptionChosenResponse(second.id, secondDredgeIdx))

            withClue("Test Dredge Three ends up in hand, not drawn") {
                game.isInHand(1, "Test Dredge Three") shouldBe true
            }
            withClue("No card was ever actually drawn — both instruction draws were replaced") {
                game.isInHand(1, "Dakmor Salvage") shouldBe true
            }
        }
    }

    private fun Any?.shouldBeChooseOption(): ChooseOptionDecision {
        withClue("Expected a pending ChooseOptionDecision for the dredge offer") {
            (this is ChooseOptionDecision) shouldBe true
        }
        return this as ChooseOptionDecision
    }
}

/**
 * Draw-step coverage lives in a separate [GameTestDriver]-based spec: the dredge dispatcher runs
 * identically for `isDrawStep = true`, but only a live turn structure exercises that path (vs the
 * spell/ability path exercised above through Test Draw Two).
 */
class DakmorSalvageDrawStepScenarioTest : io.kotest.core.spec.style.FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(DakmorSalvage))
        return driver
    }

    test("dredge is offered on the active player's own draw-step draw") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40))

        // CR 103.7a: the starting player skips the draw step of their very first turn, so this
        // targets turn 2's draw (the other player's) where the draw actually happens.
        val turn1Player = driver.activePlayer!!
        val opponent = driver.getOpponent(turn1Player)
        driver.putCardInGraveyard(opponent, "Dakmor Salvage")

        driver.passPriorityUntil(Step.DRAW, maxPasses = 200)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN, maxPasses = 200)
        driver.passPriorityUntil(Step.DRAW, maxPasses = 200)

        withClue("Turn 2's active player is the opponent") {
            driver.activePlayer shouldBe opponent
        }

        val decision = driver.pendingDecision
        withClue("Draw step draw pauses for the dredge offer") {
            (decision is ChooseOptionDecision) shouldBe true
        }
        val choice = decision as ChooseOptionDecision
        val dredgeIdx = choice.options.indexOfFirst { it.contains("Dakmor Salvage") }
        (dredgeIdx >= 0) shouldBe true

        driver.submitDecision(opponent, OptionChosenResponse(choice.id, dredgeIdx))

        withClue("Dakmor Salvage is back in hand from the draw-step dredge") {
            driver.getHand(opponent).any { id ->
                driver.state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Dakmor Salvage"
            } shouldBe true
        }
    }
})

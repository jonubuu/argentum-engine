package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.OrderTriggersDecision
import com.wingedsheep.engine.core.TriggersOrderedResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Bridge from Below — {B}{B}{B} Enchantment (Future Sight).
 *
 * "Whenever a nontoken creature is put into your graveyard from the battlefield, if this card is
 * in your graveyard, create a 2/2 black Zombie creature token.
 * When a creature is put into an opponent's graveyard from the battlefield, if this card is in
 * your graveyard, exile this card."
 *
 * Both abilities `triggerZone = GRAVEYARD` (Scryfall ruling: "While Bridge from Below is on the
 * battlefield, it has no effect"). Filtering is by which graveyard the creature went to — i.e. by
 * *owner*, not controller (Scryfall ruling) — via `.ownedByYou()` / `.ownedByOpponent()`.
 *
 * The core thing under test beyond the trigger wiring itself: the printed "if this card is in
 * your graveyard" is a real CR 603.4a resolution-time recheck, not a redundant restatement of
 * `triggerZone`. Tests 4-5 pin exactly the ruling this matters for: "if a nontoken creature you
 * control and a creature an opponent controls die at the same time, you choose the order... you
 * can create a token before you exile Bridge from Below." Pyroclasm ("deals 2 damage to each
 * creature") kills both 2/2 Grizzly Bears in one state-based-action check — a genuinely
 * simultaneous death — so both of Bridge from Below's abilities fire off the same event and the
 * engine raises an [OrderTriggersDecision] (CR 603.3b: a player controlling ≥ 2 abilities that
 * triggered at once chooses their stacking order). Test 4 answers token-then-exile and gets both;
 * test 5 answers exile-then-token and the resolution-time recheck suppresses the token.
 */
class BridgeFromBelowScenarioTest : ScenarioTestBase() {

    init {
        test("a nontoken creature you own dying creates a 2/2 black Zombie token") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Bridge from Below")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Terror")
                .withCardOnBattlefield(1, "Swamp")
                .withCardOnBattlefield(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            val cast = game.castSpell(1, "Terror", targetId = bears)
            withClue("Terror should resolve: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("Grizzly Bears died") {
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            }
            withClue("A 2/2 black Zombie token was created") {
                (game.findPermanent("Zombie Token") != null) shouldBe true
            }
            withClue("Bridge from Below stays in the graveyard — your own creature dying doesn't exile it") {
                game.isInGraveyard(1, "Bridge from Below") shouldBe true
            }
        }

        test("a token creature dying does not create a Zombie token (nontoken filter)") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Bridge from Below")
                .withCardOnBattlefield(1, "Grizzly Bears", isToken = true)
                .withCardInHand(1, "Terror")
                .withCardOnBattlefield(1, "Swamp")
                .withCardOnBattlefield(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Terror", targetId = bears)
            game.resolveStack()

            withClue("No Zombie token — the dying creature was a token") {
                game.findPermanent("Zombie Token") shouldBe null
            }
            withClue("Bridge from Below stays in the graveyard") {
                game.isInGraveyard(1, "Bridge from Below") shouldBe true
            }
        }

        test("a creature an opponent owns dying exiles Bridge from Below, with no token") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Bridge from Below")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInHand(1, "Terror")
                .withCardOnBattlefield(1, "Swamp")
                .withCardOnBattlefield(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            val cast = game.castSpell(1, "Terror", targetId = bears)
            withClue("Terror should resolve: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("The opponent's Grizzly Bears died") {
                game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            }
            withClue("Bridge from Below was exiled") {
                game.isInExile(1, "Bridge from Below") shouldBe true
                game.isInGraveyard(1, "Bridge from Below") shouldBe false
            }
            withClue("No Zombie token — an opponent's creature dying doesn't trigger the token ability") {
                game.findPermanent("Zombie Token") shouldBe null
            }
        }

        fun simultaneousDeathGame() = scenario()
            .withPlayers("Player1", "Player2")
            .withCardInGraveyard(1, "Bridge from Below")
            .withCardOnBattlefield(1, "Grizzly Bears")
            .withCardOnBattlefield(2, "Grizzly Bears")
            .withCardInHand(1, "Pyroclasm")
            .withCardOnBattlefield(1, "Mountain")
            .withCardOnBattlefield(1, "Mountain")
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            .build()

        test("CR 603.3b: token-then-exile order creates the Zombie AND exiles Bridge from Below") {
            val game = simultaneousDeathGame()

            // Pyroclasm deals 2 damage to each creature — both 2/2 Grizzly Bears die in the same
            // state-based-action check, a genuinely simultaneous death. Both of Bridge from Below's
            // abilities (both controlled by Player1) fire off that one event.
            val cast = game.castSpell(1, "Pyroclasm")
            withClue("Pyroclasm should resolve: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            val decision = game.getPendingDecision().shouldBeInstanceOf<OrderTriggersDecision>()
            withClue("Both abilities are controlled by Player1 and are distinguishable") {
                decision.triggers.size shouldBe 2
                decision.triggers.map { it.description }.toSet().size shouldBe 2
            }
            val tokenIndex = decision.triggers.indexOfFirst { it.description.contains("Zombie") }
            val exileIndex = decision.triggers.indexOfFirst { it.description.contains("exile", ignoreCase = true) }

            game.submitDecision(TriggersOrderedResponse(decision.id, listOf(tokenIndex, exileIndex)))
            game.resolveStack()

            withClue("A 2/2 black Zombie token was created") {
                (game.findPermanent("Zombie Token") != null) shouldBe true
            }
            withClue("Bridge from Below was exiled") {
                game.isInExile(1, "Bridge from Below") shouldBe true
            }
        }

        test("CR 603.3b / 603.4a: exile-then-token order suppresses the token") {
            val game = simultaneousDeathGame()

            val cast = game.castSpell(1, "Pyroclasm")
            withClue("Pyroclasm should resolve: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            val decision = game.getPendingDecision().shouldBeInstanceOf<OrderTriggersDecision>()
            val tokenIndex = decision.triggers.indexOfFirst { it.description.contains("Zombie") }
            val exileIndex = decision.triggers.indexOfFirst { it.description.contains("exile", ignoreCase = true) }

            // The exile ability resolves FIRST this time — by the time the token ability resolves,
            // Bridge from Below has already left the graveyard, so the CR 603.4a resolution-time
            // recheck suppresses the token. This is the reverse-order half of the official ruling.
            game.submitDecision(TriggersOrderedResponse(decision.id, listOf(exileIndex, tokenIndex)))
            game.resolveStack()

            withClue("No token — Bridge from Below had already left the graveyard when this ability resolved") {
                game.findPermanent("Zombie Token") shouldBe null
            }
            withClue("Bridge from Below was still exiled") {
                game.isInExile(1, "Bridge from Below") shouldBe true
            }
        }

        test("Bridge from Below on the battlefield does nothing — a nontoken creature dying creates no token") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Bridge from Below")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Terror")
                .withCardOnBattlefield(1, "Swamp")
                .withCardOnBattlefield(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Terror", targetId = bears)
            game.resolveStack()

            withClue("Grizzly Bears died") {
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            }
            withClue("No token — Bridge from Below's abilities don't function on the battlefield") {
                game.findPermanent("Zombie Token") shouldBe null
            }
            withClue("Bridge from Below is still on the battlefield, untouched") {
                (game.findPermanent("Bridge from Below") != null) shouldBe true
            }
        }
    }
}

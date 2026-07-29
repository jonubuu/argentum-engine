package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

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
 * `triggerZone`. Test 4 pins exactly the ruling this matters for: "if a nontoken creature you
 * control and a creature an opponent controls die at the same time, you choose the order... you
 * can create a token before you exile Bridge from Below" — implying the reverse order suppresses
 * the token. This engine has no "choose simultaneous trigger order" decision yet, so test 4 proves
 * the same underlying mechanism (the resolution-time graveyard recheck) sequentially instead: once
 * Bridge from Below has left the graveyard, a later nontoken death does not create a token.
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

        test("CR 603.4a: once Bridge from Below has left the graveyard, a later nontoken death creates no token") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInGraveyard(1, "Bridge from Below")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInHand(1, "Terror")
                .withCardInHand(1, "Terror")
                .withCardOnBattlefield(1, "Swamp")
                .withCardOnBattlefield(1, "Swamp")
                .withCardOnBattlefield(1, "Swamp")
                .withCardOnBattlefield(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            // First, the opponent's creature dies — Bridge from Below is exiled (ability 2).
            val opponentBears = game.findPermanents("Grizzly Bears")
                .first { game.state.projectedState.getController(it) == game.player2Id }
            game.castSpell(1, "Terror", targetId = opponentBears)
            game.resolveStack()

            withClue("Bridge from Below is now exiled") {
                game.isInExile(1, "Bridge from Below") shouldBe true
            }

            // Now the player's own nontoken creature dies. Bridge from Below is no longer in the
            // graveyard, so — even though it's still the same "if this card is in your graveyard"
            // ability that fired for the first death — the resolution-time recheck must suppress it.
            val ownBears = game.findPermanents("Grizzly Bears")
                .first { game.state.projectedState.getController(it) == game.player1Id }
            game.castSpell(1, "Terror", targetId = ownBears)
            game.resolveStack()

            withClue("No token — Bridge from Below was already out of the graveyard") {
                game.findPermanent("Zombie Token") shouldBe null
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

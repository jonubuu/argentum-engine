package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Thraben Charm (MH3, {1}{W} Instant) — modal charm:
 * "Choose one —
 * • Thraben Charm deals damage equal to twice the number of creatures you control to target creature.
 * • Destroy target enchantment.
 * • Exile any number of target players' graveyards."
 *
 * Built entirely from existing primitives (modal charm, DynamicAmount.Multiply, Destroy, and the
 * Hollow Marauder per-target gather/move idiom), so per the add-card skill this wasn't required to
 * carry its own scenario test — the snapshot/lint nets already cover a card with no new SDK
 * vocabulary. This file exists to pin the two things worth proving explicitly: mode 1's damage
 * actually scales with the caster's creature count (not a hardcoded constant), and mode 3's "any
 * number of target players" genuinely supports zero and multiple targets in the same cast.
 */
class ThrabenCharmScenarioTest : ScenarioTestBase() {

    init {
        cardRegistry.register(
            CardDefinition.enchantment(
                name = "Test Enchantment",
                manaCost = ManaCost.parse("{1}"),
                oracleText = ""
            )
        )

        test("mode 1: two creatures you control deals 4 damage, lethal to a 2-toughness creature") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Thraben Charm")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val targetBear = game.findPermanents("Grizzly Bears")
                .first { game.state.projectedState.getController(it) == game.player2Id }

            val cast = game.castSpellWithMode(1, "Thraben Charm", modeIndex = 0, targetId = targetBear)
            withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("2 creatures you control -> 4 damage, lethal to a 2/2 Grizzly Bears") {
                game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            }
        }

        test("mode 1: one creature you control deals only 2 damage, not lethal to a 3-toughness creature") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Thraben Charm")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val giant = game.findPermanent("Hill Giant")!!

            val cast = game.castSpellWithMode(1, "Thraben Charm", modeIndex = 0, targetId = giant)
            withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("1 creature you control -> only 2 damage; Hill Giant (3 toughness) survives") {
                game.isInGraveyard(2, "Hill Giant") shouldBe false
                (game.findPermanent("Hill Giant") != null) shouldBe true
            }
        }

        test("mode 2: destroys target enchantment") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Thraben Charm")
                .withCardOnBattlefield(2, "Test Enchantment")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val enchantment = game.findPermanent("Test Enchantment")!!

            val cast = game.castSpellWithMode(1, "Thraben Charm", modeIndex = 1, targetId = enchantment)
            withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("the enchantment is destroyed") {
                game.isInGraveyard(2, "Test Enchantment") shouldBe true
            }
        }

        test("mode 3: choosing both players' graveyards exiles both") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Thraben Charm")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(2, "Hill Giant")
                .withCardInGraveyard(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            withClue("both graveyards start non-empty") {
                game.graveyardSize(1) shouldBe 1
                game.graveyardSize(2) shouldBe 2
            }

            val spell = game.state.getHand(game.player1Id).first {
                game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Thraben Charm"
            }
            val cast = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = spell,
                    chosenModes = listOf(2),
                    modeTargetsOrdered = listOf(
                        listOf(ChosenTarget.Player(game.player1Id), ChosenTarget.Player(game.player2Id))
                    )
                )
            )
            withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("both original graveyard contents were exiled (Thraben Charm itself lands in " +
                "the caster's graveyard after resolving, so Player1's count is 1, not 0)") {
                game.graveyardSize(1) shouldBe 1
                game.graveyardSize(2) shouldBe 0
            }
            withClue("the original cards are in exile, not gone entirely") {
                game.isInExile(1, "Grizzly Bears") shouldBe true
                game.isInExile(2, "Hill Giant") shouldBe true
                game.isInExile(2, "Grizzly Bears") shouldBe true
            }
            withClue("Thraben Charm resolved normally and sits in its caster's graveyard") {
                game.isInGraveyard(1, "Thraben Charm") shouldBe true
            }
        }

        test("mode 3: choosing zero players (\"any number\" allows none) exiles nothing") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Thraben Charm")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(2, "Hill Giant")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spell = game.state.getHand(game.player1Id).first {
                game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Thraben Charm"
            }
            val cast = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = spell,
                    chosenModes = listOf(2),
                    modeTargetsOrdered = listOf(emptyList())
                )
            )
            withClue("Cast with zero targets should still succeed: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("neither original graveyard card was touched — the +1 on Player1 is Thraben " +
                "Charm itself landing in its caster's graveyard after resolving, not an exile") {
                game.graveyardSize(1) shouldBe 2
                game.graveyardSize(2) shouldBe 1
            }
            withClue("both original cards are still in their owners' graveyards, not exiled") {
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                game.isInGraveyard(2, "Hill Giant") shouldBe true
            }
        }
    }
}

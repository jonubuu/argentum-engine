package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.TimingRule
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the TDM "group A" batch:
 *  - Alesha's Legacy (#72): {1}{B} Instant — target creature you control gains deathtouch and
 *    indestructible until end of turn.
 *  - Bloomvine Regent // Claim Territory (#136): {3}{G}{G} Dragon 4/5 Flying; ETB (this or another
 *    Dragon you control) you gain 3 life. Omen Claim Territory searches up to two basic Forests,
 *    one to battlefield tapped and one to hand.
 *  - Cori Mountain Monastery (#252): Land — enters tapped unless you control a Plains or Island;
 *    {T}: Add {R}; {3}{R},{T}: impulse the top card of your library.
 *  - Death Begets Life (#176): {5}{B}{G}{U} Sorcery — destroy all creatures and enchantments,
 *    draw a card for each permanent destroyed this way.
 *  - Dragonbroods' Relic (#140): {1}{G} Artifact — mana ability; {3}{W}{U}{B}{R}{G}, Sacrifice:
 *    create the all-colors Reliquary Dragon token that deals 3 damage to any target on ETB.
 */
class TdmCardsGroupAScenarioTest : ScenarioTestBase() {

    init {
        context("Alesha's Legacy") {
            test("grants deathtouch and indestructible until end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Alesha's Legacy")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!

                withClue("Casting Alesha's Legacy targeting the Bear should succeed") {
                    game.castSpell(1, "Alesha's Legacy", targetId = bear).error shouldBe null
                }
                game.resolveStackAutoOrder()

                withClue("Bear has deathtouch and indestructible until end of turn") {
                    game.state.projectedState.hasKeyword(bear, Keyword.DEATHTOUCH) shouldBe true
                    game.state.projectedState.hasKeyword(bear, Keyword.INDESTRUCTIBLE) shouldBe true
                }
            }
        }

        context("Bloomvine Regent") {
            test("enters as a 4/5 flier and gains its controller 3 life on its own ETB") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Bloomvine Regent")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingLife = game.getLifeTotal(1)

                withClue("Casting the creature face should succeed") {
                    game.castSpell(1, "Bloomvine Regent").error shouldBe null
                }
                game.resolveStackAutoOrder() // resolve the creature
                game.resolveStackAutoOrder() // resolve the ETB life-gain trigger

                val regent = game.findPermanent("Bloomvine Regent")!!
                withClue("Bloomvine Regent is a 4/5 with flying") {
                    game.state.projectedState.getPower(regent) shouldBe 4
                    game.state.projectedState.getToughness(regent) shouldBe 5
                    game.state.projectedState.hasKeyword(regent, Keyword.FLYING) shouldBe true
                }
                withClue("Controller gained 3 life from its own ETB") {
                    game.getLifeTotal(1) shouldBe startingLife + 3
                }
            }

            test("ETB also triggers when another Dragon you control enters") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bloomvine Regent")
                    .withCardInHand(1, "Sagu Wildling") // another Dragon
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startingLife = game.getLifeTotal(1)

                withClue("Casting Sagu Wildling should succeed") {
                    game.castSpell(1, "Sagu Wildling").error shouldBe null
                }
                game.resolveStackAutoOrder() // Sagu Wildling enters
                game.resolveStackAutoOrder() // resolve triggers (Sagu's own +3, Bloomvine's +3)

                withClue("Both Dragon ETB triggers gained 3 life each (Sagu's own + Bloomvine's)") {
                    game.getLifeTotal(1) shouldBe startingLife + 6
                }
            }
        }

        context("Cori Mountain Monastery") {
            test("enters tapped when you control no Plains or Island") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Cori Mountain Monastery")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val landCard = game.findCardsInHand(1, "Cori Mountain Monastery").first()
                withClue("Playing the land should succeed") {
                    game.execute(PlayLand(game.player1Id, landCard)).error shouldBe null
                }

                val land = game.findPermanent("Cori Mountain Monastery")!!
                withClue("It enters tapped with no Plains/Island in play") {
                    game.state.getEntity(land)?.get<TappedComponent>() shouldNotBe null
                }
            }

            test("enters untapped when you control a Plains") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Cori Mountain Monastery")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val landCard = game.findCardsInHand(1, "Cori Mountain Monastery").first()
                withClue("Playing the land should succeed") {
                    game.execute(PlayLand(game.player1Id, landCard)).error shouldBe null
                }

                val land = game.findPermanent("Cori Mountain Monastery")!!
                withClue("It enters untapped because a Plains is controlled") {
                    game.state.getEntity(land)?.get<TappedComponent>() shouldBe null
                }
            }
        }

        context("Death Begets Life") {
            test("destroys all creatures and enchantments and draws one card per permanent destroyed") {
                // Three creatures + one global enchantment = four permanents destroyed; the
                // shared GameObjectFilter.CreatureOrEnchantment covers the "and enchantments"
                // clause (Divine Presence is a plain static Enchantment with no ETB).
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Death Begets Life")
                    .withLandsOnBattlefield(1, "Swamp", 6)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardOnBattlefield(1, "Grizzly Bears")     // creature
                    .withCardOnBattlefield(2, "Grizzly Bears")     // creature
                    .withCardOnBattlefield(2, "Grizzly Bears")     // creature
                    .withCardOnBattlefield(1, "Divine Presence")   // global enchantment
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                withClue("Casting Death Begets Life should succeed") {
                    game.castSpell(1, "Death Begets Life").error shouldBe null
                }
                game.resolveStackAutoOrder()

                withClue("All creatures were destroyed") {
                    game.findPermanents("Grizzly Bears").size shouldBe 0
                }
                withClue("The enchantment was destroyed") {
                    game.findPermanent("Divine Presence") shouldBe null
                }
                val destroyed = 4 // 3 Grizzly Bears + 1 Divine Presence
                withClue("Drew a card for each permanent destroyed this way") {
                    game.handSize(1) shouldBe (handBefore - 1 /* the spell */ + destroyed)
                }
            }
        }

        context("Dragonbroods' Relic") {
            test("sacrifice ability creates the all-colors Reliquary Dragon that deals 3 damage on ETB") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Dragonbroods' Relic")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val relic = game.findPermanent("Dragonbroods' Relic")!!
                val sacAbility = cardRegistry.getCard("Dragonbroods' Relic")!!
                    .activatedAbilities.first { it.timing is TimingRule.SorcerySpeed }

                val opponentStartLife = game.getLifeTotal(2)

                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = relic,
                        abilityId = sacAbility.id,
                    )
                )
                withClue("Activating the sacrifice ability should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }
                game.resolveStackAutoOrder() // ability resolves → token created → ETB trigger goes on stack

                withClue("Reliquary Dragon token was created") {
                    game.findPermanent("Reliquary Dragon") shouldNotBe null
                }
                val token = game.findPermanent("Reliquary Dragon")!!
                withClue("Token is a 4/4 with flying and lifelink") {
                    game.state.projectedState.getPower(token) shouldBe 4
                    game.state.projectedState.getToughness(token) shouldBe 4
                    game.state.projectedState.hasKeyword(token, Keyword.FLYING) shouldBe true
                    game.state.projectedState.hasKeyword(token, Keyword.LIFELINK) shouldBe true
                }

                // The ETB trigger needs a target — aim 3 damage at the opponent.
                game.selectTargets(listOf(game.player2Id))
                game.resolveStackAutoOrder()

                withClue("Reliquary Dragon dealt 3 damage to the opponent on ETB") {
                    game.getLifeTotal(2) shouldBe opponentStartLife - 3
                }
                withClue("The relic was sacrificed") {
                    game.findPermanent("Dragonbroods' Relic") shouldBe null
                }
            }
        }
    }
}

package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Ocelot Pride (MH3 #38) — {W} Creature — Cat, 1/1, First strike, lifelink.
 *
 * "Ascend (If you control ten or more permanents, you get the city's blessing for the rest of the
 * game.)
 * At the beginning of your end step, if you gained life this turn, create a 1/1 white Cat creature
 * token. Then if you have the city's blessing, for each token you control that entered this turn,
 * create a token that's a copy of it."
 *
 * Covers: Ascend's own 10-permanent threshold, the end-step trigger's intervening-if on gained
 * life, and — the genuinely tricky part per the card's own doc comment — that without the city's
 * blessing only the plain Cat token appears, while with it the *same* Cat token created earlier in
 * this resolution is itself copied (2024-06-07 ruling), and the doubling doesn't runs away within
 * one resolution (ForEachInGroup snapshots before creating copies).
 */
class OcelotPrideScenarioTest : ScenarioTestBase() {

    // Minimal sorcery that gains the caster life, used to drive the "gained life this turn" tracker
    // (mirrors ResplendentAngelScenarioTest's approach for the same intervening-if shape).
    private val gainOneLife = card("Test Gain One") {
        manaCost = "{W}"
        typeLine = "Sorcery"
        spell { effect = Effects.GainLife(1) }
    }

    // Makes an UNRELATED token (a Bird, not a Cat) so the doubling test below can prove the ability
    // copies "each token you control that entered this turn" in general, not just the Cat token it
    // creates itself.
    private val makeBirdToken = card("Test Make Bird") {
        manaCost = "{1}"
        typeLine = "Sorcery"
        spell {
            effect = Effects.CreateToken(
                power = 1,
                toughness = 1,
                colors = setOf(com.wingedsheep.sdk.core.Color.BLUE),
                creatureTypes = setOf("Bird"),
            )
        }
    }

    init {
        cardRegistry.register(gainOneLife)
        cardRegistry.register(makeBirdToken)

        context("Ascend") {
            test("fewer than ten permanents when it enters grants no city's blessing") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ocelot Pride")
                    .withLandsOnBattlefield(1, "Plains", 8) // + Ocelot Pride itself = 9 permanents
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Ocelot Pride")
                withClue("Casting Ocelot Pride should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("9 permanents total — below the Ascend threshold") {
                    game.state.getEntity(game.player1Id)?.has<PlayerCitysBlessingComponent>() shouldBe false
                }
            }

            test("ten or more permanents when it enters grants the city's blessing") {
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

                withClue("10 permanents total — meets the Ascend threshold") {
                    game.state.getEntity(game.player1Id)?.has<PlayerCitysBlessingComponent>() shouldBe true
                }
            }

            test("already having the blessing doesn't re-grant or re-emit the event (CR 702.131c: permanent designation)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ocelot Pride")
                    .withCardInHand(1, "Test Gain One")
                    .withLandsOnBattlefield(1, "Plains", 10) // + Ocelot Pride itself = 11 permanents
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Ocelot Pride").error shouldBe null
                game.resolveStack()
                withClue("the blessing was granted") {
                    game.state.getEntity(game.player1Id)?.has<PlayerCitysBlessingComponent>() shouldBe true
                }

                // Still 11+ permanents, so AscendCheck's condition is still true on every later SBA
                // pass — resolving a second, unrelated spell forces another pass.
                game.castSpell(1, "Test Gain One").error shouldBe null
                val results = game.resolveStack()

                withClue("no second CitysBlessingGainedEvent — already blessed is a no-op, not a re-trigger") {
                    results.flatMap { it.events }
                        .filterIsInstance<com.wingedsheep.engine.core.CitysBlessingGainedEvent>()
                        .size shouldBe 0
                }
            }

            test("crossing ten permanents AFTER it enters still grants the blessing (CR 702.131b: " +
                "a continuous check, not a one-shot at ETB)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Ocelot Pride", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Plains", 8) // + Ocelot Pride = 9 permanents, below threshold
                    .withCardInHand(1, "Plains") // the 10th permanent, played later this same turn
                    .withCardInHand(1, "Test Gain One")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                withClue("9 permanents — no blessing yet") {
                    game.state.getEntity(game.player1Id)?.has<PlayerCitysBlessingComponent>() shouldBe false
                }

                val tenthLand = game.state.getHand(game.player1Id).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Plains"
                }
                val play = game.execute(PlayLand(game.player1Id, tenthLand))
                withClue("Playing the tenth land should succeed: ${play.error}") { play.error shouldBe null }

                // Force the engine's next SBA-check poll point by resolving a spell. This engine's
                // state-based-action checks run "after a spell or ability resolves" (see
                // AscendCheck / StartYourEnginesCheck's own doc comments) rather than after every
                // single game action, so playing a land alone doesn't immediately re-poll — exactly
                // the same documented cadence gap StartYourEnginesCheck already calls out for a land
                // drop. Casting and resolving anything reaches the next real poll point.
                val cast = game.castSpell(1, "Test Gain One")
                withClue("Casting the filler spell should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue(
                    "Reaching ten permanents by playing a land — nothing to do with Ocelot Pride's " +
                        "own entry — still grants the blessing, because Ascend on a permanent is a " +
                        "continuously-checked static ability (CR 702.131b), not an ETB trigger"
                ) {
                    game.state.getEntity(game.player1Id)?.has<PlayerCitysBlessingComponent>() shouldBe true
                }
            }
        }

        context("end step token creation") {
            test("no life gained this turn creates no token (intervening-if)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Ocelot Pride", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val tokensBefore = catTokens(game.state, game.player1Id)

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                val newTokens = catTokens(game.state, game.player1Id) - tokensBefore
                withClue("No life gained -> the trigger's intervening-if fails, no token") {
                    newTokens.size shouldBe 0
                }
            }

            test("life gained this turn creates a 1/1 white Cat token") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Ocelot Pride", summoningSickness = false)
                    .withCardInHand(1, "Test Gain One")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val tokensBefore = catTokens(game.state, game.player1Id)

                val cast = game.castSpell(1, "Test Gain One")
                withClue("Casting the life-gain spell should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                val newTokens = catTokens(game.state, game.player1Id) - tokensBefore
                withClue("Gained life -> exactly one Cat token, no city's blessing to double it") {
                    newTokens.size shouldBe 1
                }
                val token = newTokens.first()
                withClue("Token is a 1/1 white Cat") {
                    game.state.projectedState.getPower(token) shouldBe 1
                    game.state.projectedState.getToughness(token) shouldBe 1
                    game.state.getEntity(token)?.get<CardComponent>()?.typeLine?.subtypes
                        ?.map { it.value } shouldBe listOf("Cat")
                }
            }

            test("with the city's blessing, the Cat token created this resolution is also copied once") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Ocelot Pride", summoningSickness = false)
                    .withCardInHand(1, "Test Gain One")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Grant the city's blessing directly, isolating the doubling logic from Ascend's own
                // 10-permanent trigger (already covered above).
                game.state = game.state.updateEntity(game.player1Id) { it.with(PlayerCitysBlessingComponent) }

                val tokensBefore = catTokens(game.state, game.player1Id)

                val cast = game.castSpell(1, "Test Gain One")
                withClue("Casting the life-gain spell should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                val newTokens = catTokens(game.state, game.player1Id) - tokensBefore
                withClue(
                    "With the blessing: the base Cat token, PLUS a copy of it (2024-06-07 ruling — it " +
                        "has already entered this turn by the time the doubling clause is reached), " +
                        "and no more (ForEachInGroup snapshots before creating copies, so this doesn't " +
                        "run away within one resolution)"
                ) {
                    newTokens.size shouldBe 2
                }
                withClue("Both new permanents are 1/1 white Cats") {
                    newTokens.forEach { token ->
                        game.state.projectedState.getPower(token) shouldBe 1
                        game.state.projectedState.getToughness(token) shouldBe 1
                    }
                }
            }

            test("with the city's blessing, an unrelated token that already entered this turn is also copied") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Ocelot Pride", summoningSickness = false)
                    .withCardInHand(1, "Test Gain One")
                    .withCardInHand(1, "Test Make Bird")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.state = game.state.updateEntity(game.player1Id) { it.with(PlayerCitysBlessingComponent) }

                // Make an unrelated Bird token earlier this same turn, then gain life — both happen
                // before the end step, so both the Bird and (once created) the Cat "entered this turn".
                val birdCast = game.castSpell(1, "Test Make Bird")
                withClue("Casting the token-making spell should succeed: ${birdCast.error}") {
                    birdCast.error shouldBe null
                }
                game.resolveStack()

                val tokensBefore = allTokens(game.state, game.player1Id)
                withClue("exactly one Bird token exists before the end step") { tokensBefore.size shouldBe 1 }

                val gainCast = game.castSpell(1, "Test Gain One")
                withClue("Casting the life-gain spell should succeed: ${gainCast.error}") {
                    gainCast.error shouldBe null
                }
                game.resolveStack()

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                val newTokens = allTokens(game.state, game.player1Id) - tokensBefore
                withClue(
                    "Three new tokens: the ability's own base Cat, a copy of that Cat (self-referential " +
                        "doubling, already covered above), AND a copy of the pre-existing Bird — proving " +
                        "\"each token you control that entered this turn\" isn't limited to tokens this " +
                        "ability itself created"
                ) {
                    newTokens.size shouldBe 3
                }

                withClue("two new Cats (the base token + its copy) and one new Bird (the copy of the pre-existing token)") {
                    newTokens.count { t ->
                        game.state.getEntity(t)?.get<CardComponent>()?.typeLine?.subtypes
                            ?.any { s -> s.value == "Cat" } == true
                    } shouldBe 2
                    newTokens.count { t ->
                        game.state.getEntity(t)?.get<CardComponent>()?.typeLine?.subtypes
                            ?.any { s -> s.value == "Bird" } == true
                    } shouldBe 1
                }
            }
        }
    }
}

private fun catTokens(state: GameState, player: EntityId): Set<EntityId> =
    state.getBattlefield().filter {
        val e = state.getEntity(it) ?: return@filter false
        e.has<TokenComponent>() &&
            e.get<ControllerComponent>()?.playerId == player &&
            e.get<CardComponent>()?.typeLine?.subtypes?.any { s -> s.value == "Cat" } == true
    }.toSet()

private fun allTokens(state: GameState, player: EntityId): Set<EntityId> =
    state.getBattlefield().filter {
        val e = state.getEntity(it) ?: return@filter false
        e.has<TokenComponent>() && e.get<ControllerComponent>()?.playerId == player
    }.toSet()

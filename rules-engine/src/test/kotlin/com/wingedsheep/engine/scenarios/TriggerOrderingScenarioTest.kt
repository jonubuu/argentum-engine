package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.BatchYesNoDecision
import com.wingedsheep.engine.core.OrderTriggersDecision
import com.wingedsheep.engine.core.TriggersOrderedResponse
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Gate
import com.wingedsheep.sdk.scripting.effects.GatedEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * CR 603.3b: "First, each player, in APNAP order, puts each triggered ability they control with a
 * trigger condition that isn't another ability triggering on the stack in any order they choose."
 *
 * Two abilities controlled by the *same* player, triggered by the same event, must let that player
 * choose their relative stacking order — the engine must not silently impose a fixed order. This is
 * the general mechanic behind [BridgeFromBelowScenarioTest] (which exercises it through a real
 * printed card); this file proves the mechanism itself, plus its two carve-outs:
 *  - a genuinely distinguishable tie raises [OrderTriggersDecision] and the chosen order changes the
 *    outcome (mirrors Bridge from Below's "you choose the order… before you exile it" ruling, via the
 *    same [Conditions.SourceInZone] resolution-time recheck, generalized to Zone.BATTLEFIELD);
 *  - a run of *structurally identical* optional triggers is exempted — order among interchangeable
 *    instances is unobservable, and [BatchYesNoDecision] already covers that case with one prompt.
 */
class TriggerOrderingScenarioTest : FunSpec({

    // Two DIFFERENT abilities on one permanent, both firing off "another creature you control
    // enters" — the same shape as Bridge from Below (token-creation + exile), but self-contained so
    // this test doesn't depend on a specific printed card's oracle text.
    val orderTestEnchantment = card("Order Test Enchantment") {
        manaCost = "{1}"
        typeLine = "Enchantment"
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            effect = Effects.Move(EffectTarget.Self, Zone.EXILE)
            description = "exile this permanent"
        }
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            effect = GatedEffect(
                gate = Gate.WhenCondition(Conditions.SourceInZone(Zone.BATTLEFIELD)),
                then = Effects.DrawCards(1)
            )
            description = "if this permanent is on the battlefield, draw a card"
        }
    }

    val orderTestBear = card("Order Test Bear") {
        manaCost = "{1}"
        typeLine = "Creature — Test"
        power = 2
        toughness = 2
    }

    val batchPinger = card("Order Batch Pinger") {
        manaCost = "{1}"
        typeLine = "Creature — Test"
        power = 1
        toughness = 1
        oracleText = "Whenever another creature you control enters the battlefield, you may have " +
            "this creature deal 1 damage to any target."
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            val t = target("target", com.wingedsheep.sdk.dsl.Targets.Any)
            effect = MayEffect(Effects.DealDamage(1, t))
        }
    }

    fun driverWithEnchantmentAndBear(): Pair<GameTestDriver, EntityId> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(orderTestEnchantment, orderTestBear, batchPinger))
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val player = driver.activePlayer!!
        driver.putPermanentOnBattlefield(player, "Order Test Enchantment")

        // The triggering creature must actually be CAST and resolved — putCreatureOnBattlefield is a
        // raw state splice with no ZoneChangeEvent, so it would never fire "another creature enters"
        // (mirrors BatchMayQuestionTest's setup, which casts Batch Bear for the same reason).
        driver.giveColorlessMana(player, 1)
        val bear = driver.putCardInHand(player, "Order Test Bear")
        driver.castSpell(player, bear).isSuccess shouldBe true
        driver.bothPass()
        return driver to player
    }

    test("two distinct simultaneous triggers from one controller raise an OrderTriggersDecision") {
        val (driver, player) = driverWithEnchantmentAndBear()

        val decision = driver.pendingDecision.shouldBeInstanceOf<OrderTriggersDecision>()
        decision.playerId shouldBe player
        decision.triggers.size shouldBe 2
        // The two abilities are distinguishable by their own description text even though both
        // share one sourceId (the enchantment) — exactly the Bridge from Below shape.
        decision.triggers.map { it.description }.toSet().size shouldBe 2
    }

    test("draw-then-exile order: the draw sees the source still on the battlefield") {
        val (driver, player) = driverWithEnchantmentAndBear()
        val decision = driver.pendingDecision.shouldBeInstanceOf<OrderTriggersDecision>()

        val drawIndex = decision.triggers.indexOfFirst { it.description.contains("draw", ignoreCase = true) }
        val exileIndex = decision.triggers.indexOfFirst { it.description.contains("exile", ignoreCase = true) }
        val handSizeBefore = driver.state.getHand(player).size

        driver.submitDecision(player, TriggersOrderedResponse(decision.id, listOf(drawIndex, exileIndex)))
        driver.bothPass() // draw trigger resolves
        driver.bothPass() // exile trigger resolves

        driver.state.getHand(player).size shouldBe handSizeBefore + 1
        driver.state.getZone(player, Zone.EXILE).any { eid ->
            driver.state.getEntity(eid)?.let { it.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name } == "Order Test Enchantment"
        } shouldBe true
    }

    test("exile-then-draw order: the resolution-time recheck suppresses the draw") {
        val (driver, player) = driverWithEnchantmentAndBear()
        val decision = driver.pendingDecision.shouldBeInstanceOf<OrderTriggersDecision>()

        val drawIndex = decision.triggers.indexOfFirst { it.description.contains("draw", ignoreCase = true) }
        val exileIndex = decision.triggers.indexOfFirst { it.description.contains("exile", ignoreCase = true) }
        val handSizeBefore = driver.state.getHand(player).size

        driver.submitDecision(player, TriggersOrderedResponse(decision.id, listOf(exileIndex, drawIndex)))
        driver.bothPass() // exile trigger resolves first
        driver.bothPass() // draw trigger resolves — but the source has already left the battlefield

        driver.state.getHand(player).size shouldBe handSizeBefore
        driver.state.getZone(player, Zone.EXILE).any { eid ->
            driver.state.getEntity(eid)?.let { it.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name } == "Order Test Enchantment"
        } shouldBe true
    }

    test("a run of structurally identical optional triggers is NOT escalated to an order prompt") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(batchPinger, orderTestBear))
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val player = driver.activePlayer!!
        repeat(2) { driver.putCreatureOnBattlefield(player, "Order Batch Pinger") }

        driver.giveColorlessMana(player, 1)
        val bear = driver.putCardInHand(player, "Order Test Bear")
        driver.castSpell(player, bear).isSuccess shouldBe true
        driver.bothPass()

        // Two IDENTICAL pinger triggers fire off the bear entering. The batch may-question must
        // still collapse them to one prompt — the ordering decision must not preempt it.
        driver.pendingDecision.shouldBeInstanceOf<BatchYesNoDecision>()
    }
})

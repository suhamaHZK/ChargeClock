package com.kmmm_engineering.chargeclock.discord

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * First vs later restore choice used by [ThresholdAlertEvaluator].
 * The evaluator is Android-coupled; this helper is the process gate it calls.
 */
class ProcessThresholdSessionTest {

    private lateinit var session: ProcessThresholdSession
    private lateinit var persisted: AtomicReference<ThresholdAlertSnapshot>

    @Before
    fun setUp() {
        session = ProcessThresholdSession()
        persisted = AtomicReference(deadSnapshot(lastPercent = 50, lastCharging = false))
    }

    @Test
    fun firstRestore_dischargeCrossWhileDead_doesNotFire_andSeedsCurrent() = runBlocking {
        val kind = eval(percent = 42, isCharging = false, dischargeThreshold = 45, chargeThreshold = 80)
        assertNull(kind)
        val saved = persisted.get()
        assertEquals(42, saved.lastPercent)
        assertTrue(saved.seeded)
        assertFalse(saved.dischargeArmed)
    }

    @Test
    fun firstRestore_chargeCrossWhileDead_doesNotFire_andSeedsCurrent() = runBlocking {
        persisted.set(deadSnapshot(lastPercent = 70, lastCharging = true))
        val kind = eval(percent = 85, isCharging = true, dischargeThreshold = 45, chargeThreshold = 80)
        assertNull(kind)
        val saved = persisted.get()
        assertEquals(85, saved.lastPercent)
        assertTrue(saved.seeded)
        assertFalse(saved.chargeArmed)
    }

    @Test
    fun secondEval_dischargeCrossInProcess_fires_withoutResettingGate() = runBlocking {
        // First sample after process start: outside the zone, seed only.
        assertNull(eval(percent = 50, isCharging = false, dischargeThreshold = 45, chargeThreshold = 80))
        // Settings change must not return the gate to "first" (that would swallow this cross).
        assertNull(eval(percent = 50, isCharging = false, dischargeThreshold = 40, chargeThreshold = 80))
        assertEquals(
            "discharge",
            eval(percent = 40, isCharging = false, dischargeThreshold = 40, chargeThreshold = 80),
        )
    }

    @Test
    fun secondEval_chargeCrossInProcess_fires() = runBlocking {
        persisted.set(deadSnapshot(lastPercent = 70, lastCharging = true))
        assertNull(eval(percent = 70, isCharging = true, dischargeThreshold = 45, chargeThreshold = 80))
        assertEquals(
            "charge",
            eval(percent = 85, isCharging = true, dischargeThreshold = 45, chargeThreshold = 80),
        )
    }

    @Test
    fun concurrentDoubleFirst_doesNotDoubleFire() = runBlocking {
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val fires = AtomicInteger(0)
        // firstInProcess to the lastPercent that call actually restored from.
        val seen = ConcurrentLinkedQueue<Pair<Boolean, Int>>()
        val jobs = List(2) {
            launch(Dispatchers.Default) {
                ready.countDown()
                start.await()
                val kind = eval(
                    percent = 42,
                    isCharging = false,
                    dischargeThreshold = 45,
                    chargeThreshold = 80,
                    seen = seen,
                )
                if (kind != null) fires.incrementAndGet()
            }
        }
        ready.await()
        start.countDown()
        jobs.forEach { it.join() }
        assertEquals(0, fires.get())
        // Exactly one quiet first restore of the stale 50, then one later call that
        // sees the seeded 42 — not two crosses against the pre-death snapshot.
        assertEquals(setOf(true to 50, false to 42), seen.toSet())
        assertEquals(42, persisted.get().lastPercent)
        assertTrue(persisted.get().seeded)
        assertFalse(persisted.get().dischargeArmed)
    }

    private suspend fun eval(
        percent: Int,
        isCharging: Boolean,
        dischargeThreshold: Int,
        chargeThreshold: Int,
        seen: ConcurrentLinkedQueue<Pair<Boolean, Int>>? = null,
    ): String? = session.withProcessRestore { firstInProcess ->
        val tracker = ThresholdFireTracker()
        val snapshot = persisted.get()
        seen?.add(firstInProcess to snapshot.lastPercent)
        if (firstInProcess) {
            tracker.restore(snapshot)
        } else {
            tracker.restoreKeepingSeed(snapshot)
        }
        val kind = tracker.check(
            percent = percent,
            isCharging = isCharging,
            dischargeThreshold = dischargeThreshold,
            chargeThreshold = chargeThreshold,
        )
        persisted.set(tracker.snapshot())
        kind
    }

    private fun deadSnapshot(lastPercent: Int, lastCharging: Boolean) = ThresholdAlertSnapshot(
        lastPercent = lastPercent,
        lastCharging = lastCharging,
        dischargeArmed = true,
        chargeArmed = true,
        lastDischargeThreshold = 45,
        lastChargeThreshold = 80,
        seeded = true,
    )
}

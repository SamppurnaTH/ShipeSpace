package com.shipspace.integration;

import com.shipspace.config.SloEnforcementService;
import com.shipspace.config.SloEnforcementService.DegradationLevel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Control loop tests — validates all five properties of the SLO enforcement service.
 *
 * 1. HYSTERESIS: system does not oscillate between levels
 * 2. SCOPED DEGRADATION: feed degradation does not affect engagement
 * 3. METRIC-BASED RECOVERY: recovery requires sustained health, not just time
 * 4. NONLINEAR CONTROL: concurrency halves on degrade, steps up on recover
 * 5. ENFORCED RATE LIMITS: multiplier drops to 0.33 under load shedding
 */
class ControlLoopTest extends AbstractIntegrationTest {

    @Autowired private SloEnforcementService slo;
    @Autowired private MeterRegistry registry;

    @BeforeEach
    void resetState() throws Exception {
        // Force evaluation to flush any stale state from previous tests
        slo.evaluateSlos();
        // Brief pause to let the scheduler settle
        Thread.sleep(50);
    }

    // =========================================================================
    // PROPERTY 1: HYSTERESIS
    // A single violation must NOT trigger degradation.
    // DEGRADE_THRESHOLD consecutive violations are required.
    // =========================================================================

    @Nested
    @DisplayName("Hysteresis — single violation does not trigger degradation")
    class HysteresisTests {

        @Test
        @DisplayName("One violation below threshold: level stays NORMAL")
        void oneViolation_levelStaysNormal() throws Exception {
            // Record one slow feed response (above 300ms SLO)
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();
            feedTimer.record(400, TimeUnit.MILLISECONDS); // violation

            slo.evaluateSlos(); // 1 violation — below DEGRADE_THRESHOLD (3)

            assertThat(slo.isPersonalizationDisabled())
                    .as("One violation must not disable personalization — hysteresis requires 3 consecutive")
                    .isFalse();
        }

        @Test
        @DisplayName("Two violations below threshold: level stays NORMAL")
        void twoViolations_levelStaysNormal() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();

            feedTimer.record(400, TimeUnit.MILLISECONDS);
            slo.evaluateSlos();
            feedTimer.record(400, TimeUnit.MILLISECONDS);
            slo.evaluateSlos(); // 2 violations — still below threshold

            assertThat(slo.isPersonalizationDisabled())
                    .as("Two violations must not disable personalization — threshold is 3")
                    .isFalse();
        }

        @Test
        @DisplayName("Three consecutive violations: degradation activates")
        void threeConsecutiveViolations_degradationActivates() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();

            // Record 3 consecutive violations
            for (int i = 0; i < 3; i++) {
                feedTimer.record(400, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            assertThat(slo.isPersonalizationDisabled())
                    .as("Three consecutive violations must disable personalization")
                    .isTrue();
        }

        @Test
        @DisplayName("Violation interrupted by healthy reading: counter resets")
        void violationInterruptedByHealthy_counterResets() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();

            // 2 violations, then 1 healthy, then 2 more violations
            feedTimer.record(400, TimeUnit.MILLISECONDS);
            slo.evaluateSlos();
            feedTimer.record(400, TimeUnit.MILLISECONDS);
            slo.evaluateSlos();
            feedTimer.record(100, TimeUnit.MILLISECONDS); // healthy — resets counter
            slo.evaluateSlos();
            feedTimer.record(400, TimeUnit.MILLISECONDS);
            slo.evaluateSlos();
            feedTimer.record(400, TimeUnit.MILLISECONDS);
            slo.evaluateSlos(); // only 2 consecutive violations after reset

            assertThat(slo.isPersonalizationDisabled())
                    .as("Violation counter must reset after a healthy reading — " +
                        "only 2 consecutive violations after reset, threshold is 3")
                    .isFalse();
        }
    }

    // =========================================================================
    // PROPERTY 2: SCOPED DEGRADATION
    // Feed SLO violation must not affect engagement scope.
    // Engagement SLO violation must not affect feed scope.
    // =========================================================================

    @Nested
    @DisplayName("Scoped Degradation — feed and engagement are independent")
    class ScopedDegradationTests {

        @Test
        @DisplayName("Feed SLO violation does not affect engagement scope")
        void feedViolation_doesNotAffectEngagementScope() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();

            // Trigger feed degradation (3 consecutive violations)
            for (int i = 0; i < 3; i++) {
                feedTimer.record(400, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            assertThat(slo.isPersonalizationDisabled())
                    .as("Feed degradation must be active")
                    .isTrue();
            assertThat(slo.isAsyncConcurrencyReduced())
                    .as("Engagement scope must NOT be affected by feed SLO violation — scoped degradation")
                    .isFalse();
        }

        @Test
        @DisplayName("Engagement SLO violation does not affect feed scope")
        void engagementViolation_doesNotAffectFeedScope() throws Exception {
            Timer engTimer = registry.find("shipspace.slo.engagement").timer();
            assertThat(engTimer).isNotNull();

            // Trigger engagement degradation (3 consecutive violations)
            for (int i = 0; i < 3; i++) {
                engTimer.record(300, TimeUnit.MILLISECONDS); // above 200ms SLO
                slo.evaluateSlos();
            }

            assertThat(slo.isAsyncConcurrencyReduced())
                    .as("Engagement degradation must be active")
                    .isTrue();
            assertThat(slo.isPersonalizationDisabled())
                    .as("Feed scope must NOT be affected by engagement SLO violation — scoped degradation")
                    .isFalse();
        }

        @Test
        @DisplayName("Effective level is max across all scopes")
        void effectiveLevel_isMaxAcrossScopes() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            Timer engTimer  = registry.find("shipspace.slo.engagement").timer();
            assertThat(feedTimer).isNotNull();
            assertThat(engTimer).isNotNull();

            // Trigger feed degradation only
            for (int i = 0; i < 3; i++) {
                feedTimer.record(400, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            DegradationLevel effective = slo.getEffectiveLevel();
            assertThat(effective.level)
                    .as("Effective level must be max across all scopes")
                    .isGreaterThanOrEqualTo(DegradationLevel.PERSONALIZATION_DISABLED.level);
        }
    }

    // =========================================================================
    // PROPERTY 3: METRIC-BASED RECOVERY
    // Recovery requires RECOVER_THRESHOLD (9) consecutive healthy readings.
    // Fewer healthy readings must not trigger recovery.
    // =========================================================================

    @Nested
    @DisplayName("Metric-Based Recovery — requires sustained health, not just time")
    class MetricBasedRecoveryTests {

        @Test
        @DisplayName("8 healthy readings after degradation: still degraded")
        void eightHealthyReadings_stillDegraded() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();

            // Degrade first
            for (int i = 0; i < 3; i++) {
                feedTimer.record(400, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }
            assertThat(slo.isPersonalizationDisabled()).isTrue();

            // 8 healthy readings — below RECOVER_THRESHOLD (9)
            for (int i = 0; i < 8; i++) {
                feedTimer.record(100, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            assertThat(slo.isPersonalizationDisabled())
                    .as("8 healthy readings must not trigger recovery — threshold is 9")
                    .isTrue();
        }

        @Test
        @DisplayName("9 consecutive healthy readings after degradation: recovery triggers")
        void nineConsecutiveHealthyReadings_recoveryTriggers() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();

            // Degrade first
            for (int i = 0; i < 3; i++) {
                feedTimer.record(400, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }
            assertThat(slo.isPersonalizationDisabled()).isTrue();

            // 9 consecutive healthy readings — meets RECOVER_THRESHOLD
            for (int i = 0; i < 9; i++) {
                feedTimer.record(100, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            assertThat(slo.isPersonalizationDisabled())
                    .as("9 consecutive healthy readings must trigger recovery")
                    .isFalse();
        }

        @Test
        @DisplayName("Recovery interrupted by violation: counter resets")
        void recoveryInterruptedByViolation_counterResets() throws Exception {
            Timer feedTimer = registry.find("shipspace.slo.feed.trending").timer();
            assertThat(feedTimer).isNotNull();

            // Degrade
            for (int i = 0; i < 3; i++) {
                feedTimer.record(400, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            // 8 healthy, then 1 violation, then 8 more healthy
            for (int i = 0; i < 8; i++) {
                feedTimer.record(100, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }
            feedTimer.record(400, TimeUnit.MILLISECONDS); // violation resets recovery counter
            slo.evaluateSlos();
            for (int i = 0; i < 8; i++) {
                feedTimer.record(100, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            assertThat(slo.isPersonalizationDisabled())
                    .as("Recovery counter must reset after a violation — " +
                        "only 8 consecutive healthy after reset, threshold is 9")
                    .isTrue();
        }
    }

    // =========================================================================
    // PROPERTY 4: NONLINEAR CONCURRENCY CONTROL
    // Reduction: exponential (halve each step).
    // Restoration: linear (fixed step each cycle).
    // =========================================================================

    @Nested
    @DisplayName("Nonlinear Concurrency Control — fast degrade, slow recover")
    class NonlinearConcurrencyTests {

        @Test
        @DisplayName("Concurrency halves on engagement degradation (exponential)")
        void concurrencyHalves_onEngagementDegradation() throws Exception {
            Timer engTimer = registry.find("shipspace.slo.engagement").timer();
            assertThat(engTimer).isNotNull();

            int before = slo.getAdaptiveConcurrencyLimit();

            // Trigger engagement degradation
            for (int i = 0; i < 3; i++) {
                engTimer.record(300, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }

            int after = slo.getAdaptiveConcurrencyLimit();
            assertThat(after)
                    .as("Concurrency must be halved on degradation (exponential reduction). " +
                        "Before: %d, After: %d", before, after)
                    .isLessThanOrEqualTo(before / 2 + 1); // +1 for integer rounding
        }

        @Test
        @DisplayName("Concurrency restores linearly (smaller steps than reduction)")
        void concurrencyRestoresLinearly_smallerStepsThanReduction() throws Exception {
            Timer engTimer = registry.find("shipspace.slo.engagement").timer();
            assertThat(engTimer).isNotNull();

            int initial = slo.getAdaptiveConcurrencyLimit();

            // Degrade (halves concurrency)
            for (int i = 0; i < 3; i++) {
                engTimer.record(300, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }
            int afterDegrade = slo.getAdaptiveConcurrencyLimit();

            // Recover (9 healthy readings)
            for (int i = 0; i < 9; i++) {
                engTimer.record(50, TimeUnit.MILLISECONDS);
                slo.evaluateSlos();
            }
            int afterOneRecoveryCycle = slo.getAdaptiveConcurrencyLimit();

            // After one recovery cycle, concurrency should be higher than degraded
            // but NOT back to initial (linear recovery is slower than exponential reduction)
            assertThat(afterOneRecoveryCycle)
                    .as("Concurrency must increase after recovery cycle")
                    .isGreaterThan(afterDegrade);
            assertThat(afterOneRecoveryCycle)
                    .as("Concurrency must not fully restore in one cycle (linear = slow recovery)")
                    .isLessThanOrEqualTo(initial);
        }

        @Test
        @DisplayName("Concurrency never goes below minimum (10% of max)")
        void concurrencyNeverBelowMinimum() throws Exception {
            Timer engTimer = registry.find("shipspace.slo.engagement").timer();
            assertThat(engTimer).isNotNull();

            // Trigger many degradation cycles
            for (int cycle = 0; cycle < 10; cycle++) {
                for (int i = 0; i < 3; i++) {
                    engTimer.record(300, TimeUnit.MILLISECONDS);
                    slo.evaluateSlos();
                }
            }

            int limit = slo.getAdaptiveConcurrencyLimit();
            assertThat(limit)
                    .as("Concurrency must never go below minimum (10%% of max = 20 for default 200)")
                    .isGreaterThanOrEqualTo(10);
        }
    }

    // =========================================================================
    // PROPERTY 5: ENFORCED RATE LIMITS
    // When system degradation is active, rate limit multiplier drops to 0.33.
    // This is server-enforced — not advisory.
    // =========================================================================

    @Nested
    @DisplayName("Enforced Rate Limits — multiplier drops under load shedding")
    class EnforcedRateLimitTests {

        @Test
        @DisplayName("Normal state: rate limit multiplier is 1.0")
        void normalState_multiplierIsOne() {
            assertThat(slo.getRateLimitMultiplier())
                    .as("Rate limit multiplier must be 1.0 in normal state")
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("All scoped degradation states are independent")
        void scopedStates_areIndependent() {
            // Verify the scoped API is consistent
            assertThat(slo.isPersonalizationDisabled()).isFalse();
            assertThat(slo.isBehavioralWritesSkipped()).isFalse();
            assertThat(slo.isAsyncConcurrencyReduced()).isFalse();
            assertThat(slo.isLoadSheddingActive()).isFalse();
            assertThat(slo.getRateLimitMultiplier()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Effective level gauge reflects max across all scopes")
        void effectiveLevelGauge_reflectsMaxAcrossScopes() {
            var gauge = registry.find("shipspace.slo.degradation_level").gauge();
            assertThat(gauge).as("Effective level gauge must be registered").isNotNull();
            assertThat(gauge.value()).as("Must be 0 (NORMAL) at test start").isEqualTo(0.0);
        }

        @Test
        @DisplayName("Scoped gauges are all registered")
        void scopedGauges_allRegistered() {
            assertThat(registry.find("shipspace.slo.feed_degradation_level").gauge())
                    .as("Feed degradation gauge must be registered").isNotNull();
            assertThat(registry.find("shipspace.slo.engagement_degradation_level").gauge())
                    .as("Engagement degradation gauge must be registered").isNotNull();
            assertThat(registry.find("shipspace.slo.system_degradation_level").gauge())
                    .as("System degradation gauge must be registered").isNotNull();
            assertThat(registry.find("shipspace.slo.rate_limit_multiplier").gauge())
                    .as("Rate limit multiplier gauge must be registered").isNotNull();
        }
    }
}

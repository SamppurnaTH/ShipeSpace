package com.shipspace.model;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for RankTier enumeration.
 * 
 * <p>These tests verify the correctness properties of the RankTier enum
 * across a wide range of inputs using property-based testing with jqwik.
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 2.3</a>
 */
class RankTierPropertyTest {

    /**
     * Property 20: RankTier Ordering
     * 
     * **Validates: Requirements 2.3**
     * 
     * For any two RankTier values, their ordinal positions should reflect the progression order:
     * DOCKED < LAUNCHER < ORBITER < NAVIGATOR < COMMANDER < FLEET_ADMIRAL.
     * 
     * This property verifies that:
     * 1. The enum values are ordered correctly by their ordinal() values
     * 2. Any rank tier with a higher ordinal has a higher minimum level
     * 3. The ordering is transitive and consistent
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 20: RankTier Ordering")
    void rankTierOrderingReflectsProgression(
            @ForAll("rankTierPair") RankTierPair pair
    ) {
        RankTier rank1 = pair.rank1;
        RankTier rank2 = pair.rank2;
        
        // If rank1 comes before rank2 in the enum declaration (lower ordinal),
        // then rank1's minimum level should be less than rank2's minimum level
        if (rank1.ordinal() < rank2.ordinal()) {
            assertTrue(rank1.getMinLevel() < rank2.getMinLevel(),
                    String.format("%s (ordinal %d, minLevel %d) should have lower minLevel than %s (ordinal %d, minLevel %d)",
                            rank1, rank1.ordinal(), rank1.getMinLevel(),
                            rank2, rank2.ordinal(), rank2.getMinLevel()));
            
            // Also verify that rank1's max level is less than rank2's min level
            // (no overlap in level ranges)
            assertTrue(rank1.getMaxLevel() < rank2.getMinLevel(),
                    String.format("%s maxLevel (%d) should be less than %s minLevel (%d)",
                            rank1, rank1.getMaxLevel(), rank2, rank2.getMinLevel()));
        }
        
        // If rank2 comes before rank1 in the enum declaration (lower ordinal),
        // then rank2's minimum level should be less than rank1's minimum level
        if (rank2.ordinal() < rank1.ordinal()) {
            assertTrue(rank2.getMinLevel() < rank1.getMinLevel(),
                    String.format("%s (ordinal %d, minLevel %d) should have lower minLevel than %s (ordinal %d, minLevel %d)",
                            rank2, rank2.ordinal(), rank2.getMinLevel(),
                            rank1, rank1.ordinal(), rank1.getMinLevel()));
            
            // Also verify that rank2's max level is less than rank1's min level
            // (no overlap in level ranges)
            assertTrue(rank2.getMaxLevel() < rank1.getMinLevel(),
                    String.format("%s maxLevel (%d) should be less than %s minLevel (%d)",
                            rank2, rank2.getMaxLevel(), rank1, rank1.getMinLevel()));
        }
        
        // If they're the same rank, ordinals and levels should be equal
        if (rank1 == rank2) {
            assertEquals(rank1.ordinal(), rank2.ordinal(),
                    "Same rank should have same ordinal");
            assertEquals(rank1.getMinLevel(), rank2.getMinLevel(),
                    "Same rank should have same minLevel");
            assertEquals(rank1.getMaxLevel(), rank2.getMaxLevel(),
                    "Same rank should have same maxLevel");
        }
    }

    /**
     * Property 20 (Extended): Ordinal ordering matches expected sequence
     * 
     * **Validates: Requirements 2.3**
     * 
     * For any RankTier value, verify that its ordinal matches the expected position
     * in the progression sequence.
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 20: Ordinal matches expected position")
    void ordinalMatchesExpectedPosition(@ForAll("rankTier") RankTier rank) {
        // Verify each rank has the correct ordinal position
        switch (rank) {
            case DOCKED:
                assertEquals(0, rank.ordinal(), "DOCKED should be at position 0");
                break;
            case LAUNCHER:
                assertEquals(1, rank.ordinal(), "LAUNCHER should be at position 1");
                break;
            case ORBITER:
                assertEquals(2, rank.ordinal(), "ORBITER should be at position 2");
                break;
            case NAVIGATOR:
                assertEquals(3, rank.ordinal(), "NAVIGATOR should be at position 3");
                break;
            case COMMANDER:
                assertEquals(4, rank.ordinal(), "COMMANDER should be at position 4");
                break;
            case FLEET_ADMIRAL:
                assertEquals(5, rank.ordinal(), "FLEET_ADMIRAL should be at position 5");
                break;
        }
    }

    /**
     * Property 20 (Extended): Level ranges are contiguous and non-overlapping
     * 
     * **Validates: Requirements 2.3**
     * 
     * For any two consecutive RankTier values in the progression,
     * the level ranges should be contiguous (no gaps) and non-overlapping.
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 20: Level ranges are contiguous")
    void levelRangesAreContiguousAndNonOverlapping(@ForAll("consecutiveRankPair") RankTierPair pair) {
        RankTier lowerRank = pair.rank1;
        RankTier higherRank = pair.rank2;
        
        // Verify that the higher rank's minLevel is exactly one more than the lower rank's maxLevel
        assertEquals(lowerRank.getMaxLevel() + 1, higherRank.getMinLevel(),
                String.format("Level ranges should be contiguous: %s maxLevel (%d) + 1 should equal %s minLevel (%d)",
                        lowerRank, lowerRank.getMaxLevel(), higherRank, higherRank.getMinLevel()));
        
        // Verify no overlap
        assertTrue(lowerRank.getMaxLevel() < higherRank.getMinLevel(),
                String.format("Level ranges should not overlap: %s maxLevel (%d) should be less than %s minLevel (%d)",
                        lowerRank, lowerRank.getMaxLevel(), higherRank, higherRank.getMinLevel()));
    }

    /**
     * Property 20 (Extended): compareTo ordering is consistent with ordinal
     * 
     * **Validates: Requirements 2.3**
     * 
     * For any two RankTier values, the compareTo result should be consistent
     * with their ordinal ordering.
     */
    @Property(tries = 100)
    @Label("Feature: rank-level-progression, Property 20: compareTo consistent with ordinal")
    void compareToConsistentWithOrdinal(@ForAll("rankTierPair") RankTierPair pair) {
        RankTier rank1 = pair.rank1;
        RankTier rank2 = pair.rank2;
        
        int compareResult = rank1.compareTo(rank2);
        int ordinalDiff = rank1.ordinal() - rank2.ordinal();
        
        // compareTo should have the same sign as the ordinal difference
        if (ordinalDiff < 0) {
            assertTrue(compareResult < 0,
                    String.format("%s should compare less than %s", rank1, rank2));
        } else if (ordinalDiff > 0) {
            assertTrue(compareResult > 0,
                    String.format("%s should compare greater than %s", rank1, rank2));
        } else {
            assertEquals(0, compareResult,
                    String.format("%s should compare equal to %s", rank1, rank2));
        }
    }

    // ========== Arbitraries (Generators) ==========

    /**
     * Generates arbitrary RankTier values.
     */
    @Provide
    Arbitrary<RankTier> rankTier() {
        return Arbitraries.of(RankTier.class);
    }

    /**
     * Generates pairs of RankTier values for comparison testing.
     */
    @Provide
    Arbitrary<RankTierPair> rankTierPair() {
        return Combinators.combine(
                Arbitraries.of(RankTier.class),
                Arbitraries.of(RankTier.class)
        ).as(RankTierPair::new);
    }

    /**
     * Generates pairs of consecutive RankTier values in the progression order.
     */
    @Provide
    Arbitrary<RankTierPair> consecutiveRankPair() {
        return Arbitraries.of(
                new RankTierPair(RankTier.DOCKED, RankTier.LAUNCHER),
                new RankTierPair(RankTier.LAUNCHER, RankTier.ORBITER),
                new RankTierPair(RankTier.ORBITER, RankTier.NAVIGATOR),
                new RankTierPair(RankTier.NAVIGATOR, RankTier.COMMANDER),
                new RankTierPair(RankTier.COMMANDER, RankTier.FLEET_ADMIRAL)
        );
    }

    /**
     * Helper class to hold a pair of RankTier values for property testing.
     */
    private static class RankTierPair {
        final RankTier rank1;
        final RankTier rank2;

        RankTierPair(RankTier rank1, RankTier rank2) {
            this.rank1 = rank1;
            this.rank2 = rank2;
        }
    }
}

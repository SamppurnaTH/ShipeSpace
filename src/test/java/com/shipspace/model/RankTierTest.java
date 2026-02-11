package com.shipspace.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RankTier enumeration.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>Each level range maps to the correct rank tier</li>
 *   <li>Boundary values are handled correctly</li>
 *   <li>Invalid inputs throw appropriate exceptions</li>
 * </ul>
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6</a>
 */
class RankTierTest {

    // Test each level range maps to correct rank
    
    @Test
    @DisplayName("Level 1 should map to DOCKED")
    void testLevel1MapsToDocked() {
        assertEquals(RankTier.DOCKED, RankTier.fromLevel(1));
    }
    
    @Test
    @DisplayName("Level 5 should map to DOCKED")
    void testLevel5MapsToDocked() {
        assertEquals(RankTier.DOCKED, RankTier.fromLevel(5));
    }
    
    @Test
    @DisplayName("Level 6 should map to LAUNCHER")
    void testLevel6MapsToLauncher() {
        assertEquals(RankTier.LAUNCHER, RankTier.fromLevel(6));
    }
    
    @Test
    @DisplayName("Level 10 should map to LAUNCHER")
    void testLevel10MapsToLauncher() {
        assertEquals(RankTier.LAUNCHER, RankTier.fromLevel(10));
    }
    
    @Test
    @DisplayName("Level 11 should map to ORBITER")
    void testLevel11MapsToOrbiter() {
        assertEquals(RankTier.ORBITER, RankTier.fromLevel(11));
    }
    
    @Test
    @DisplayName("Level 15 should map to ORBITER")
    void testLevel15MapsToOrbiter() {
        assertEquals(RankTier.ORBITER, RankTier.fromLevel(15));
    }
    
    @Test
    @DisplayName("Level 16 should map to NAVIGATOR")
    void testLevel16MapsToNavigator() {
        assertEquals(RankTier.NAVIGATOR, RankTier.fromLevel(16));
    }
    
    @Test
    @DisplayName("Level 20 should map to NAVIGATOR")
    void testLevel20MapsToNavigator() {
        assertEquals(RankTier.NAVIGATOR, RankTier.fromLevel(20));
    }
    
    @Test
    @DisplayName("Level 21 should map to COMMANDER")
    void testLevel21MapsToCommander() {
        assertEquals(RankTier.COMMANDER, RankTier.fromLevel(21));
    }
    
    @Test
    @DisplayName("Level 25 should map to COMMANDER")
    void testLevel25MapsToCommander() {
        assertEquals(RankTier.COMMANDER, RankTier.fromLevel(25));
    }
    
    @Test
    @DisplayName("Level 26 should map to FLEET_ADMIRAL")
    void testLevel26MapsToFleetAdmiral() {
        assertEquals(RankTier.FLEET_ADMIRAL, RankTier.fromLevel(26));
    }
    
    @Test
    @DisplayName("Level 30 should map to FLEET_ADMIRAL")
    void testLevel30MapsToFleetAdmiral() {
        assertEquals(RankTier.FLEET_ADMIRAL, RankTier.fromLevel(30));
    }
    
    // Test boundary values
    
    @Test
    @DisplayName("Boundary: Level 5 should map to DOCKED, Level 6 to LAUNCHER")
    void testBoundaryBetweenDockedAndLauncher() {
        assertEquals(RankTier.DOCKED, RankTier.fromLevel(5));
        assertEquals(RankTier.LAUNCHER, RankTier.fromLevel(6));
    }
    
    @Test
    @DisplayName("Boundary: Level 10 should map to LAUNCHER, Level 11 to ORBITER")
    void testBoundaryBetweenLauncherAndOrbiter() {
        assertEquals(RankTier.LAUNCHER, RankTier.fromLevel(10));
        assertEquals(RankTier.ORBITER, RankTier.fromLevel(11));
    }
    
    @Test
    @DisplayName("Boundary: Level 15 should map to ORBITER, Level 16 to NAVIGATOR")
    void testBoundaryBetweenOrbiterAndNavigator() {
        assertEquals(RankTier.ORBITER, RankTier.fromLevel(15));
        assertEquals(RankTier.NAVIGATOR, RankTier.fromLevel(16));
    }
    
    @Test
    @DisplayName("Boundary: Level 20 should map to NAVIGATOR, Level 21 to COMMANDER")
    void testBoundaryBetweenNavigatorAndCommander() {
        assertEquals(RankTier.NAVIGATOR, RankTier.fromLevel(20));
        assertEquals(RankTier.COMMANDER, RankTier.fromLevel(21));
    }
    
    @Test
    @DisplayName("Boundary: Level 25 should map to COMMANDER, Level 26 to FLEET_ADMIRAL")
    void testBoundaryBetweenCommanderAndFleetAdmiral() {
        assertEquals(RankTier.COMMANDER, RankTier.fromLevel(25));
        assertEquals(RankTier.FLEET_ADMIRAL, RankTier.fromLevel(26));
    }
    
    // Test invalid inputs
    
    @Test
    @DisplayName("Level 0 should throw IllegalArgumentException")
    void testLevel0ThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> RankTier.fromLevel(0)
        );
        assertTrue(exception.getMessage().contains("Level must be between 1 and 30"));
        assertTrue(exception.getMessage().contains("got: 0"));
    }
    
    @Test
    @DisplayName("Level 31 should throw IllegalArgumentException")
    void testLevel31ThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> RankTier.fromLevel(31)
        );
        assertTrue(exception.getMessage().contains("Level must be between 1 and 30"));
        assertTrue(exception.getMessage().contains("got: 31"));
    }
    
    @Test
    @DisplayName("Negative level should throw IllegalArgumentException")
    void testNegativeLevelThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> RankTier.fromLevel(-5)
        );
        assertTrue(exception.getMessage().contains("Level must be between 1 and 30"));
        assertTrue(exception.getMessage().contains("got: -5"));
    }
    
    // Test enum properties
    
    @Test
    @DisplayName("DOCKED should have correct min and max levels")
    void testDockedLevelRange() {
        assertEquals(1, RankTier.DOCKED.getMinLevel());
        assertEquals(5, RankTier.DOCKED.getMaxLevel());
    }
    
    @Test
    @DisplayName("LAUNCHER should have correct min and max levels")
    void testLauncherLevelRange() {
        assertEquals(6, RankTier.LAUNCHER.getMinLevel());
        assertEquals(10, RankTier.LAUNCHER.getMaxLevel());
    }
    
    @Test
    @DisplayName("ORBITER should have correct min and max levels")
    void testOrbiterLevelRange() {
        assertEquals(11, RankTier.ORBITER.getMinLevel());
        assertEquals(15, RankTier.ORBITER.getMaxLevel());
    }
    
    @Test
    @DisplayName("NAVIGATOR should have correct min and max levels")
    void testNavigatorLevelRange() {
        assertEquals(16, RankTier.NAVIGATOR.getMinLevel());
        assertEquals(20, RankTier.NAVIGATOR.getMaxLevel());
    }
    
    @Test
    @DisplayName("COMMANDER should have correct min and max levels")
    void testCommanderLevelRange() {
        assertEquals(21, RankTier.COMMANDER.getMinLevel());
        assertEquals(25, RankTier.COMMANDER.getMaxLevel());
    }
    
    @Test
    @DisplayName("FLEET_ADMIRAL should have correct min and max levels")
    void testFleetAdmiralLevelRange() {
        assertEquals(26, RankTier.FLEET_ADMIRAL.getMinLevel());
        assertEquals(30, RankTier.FLEET_ADMIRAL.getMaxLevel());
    }
    
    // Test enum ordering
    
    @Test
    @DisplayName("RankTier enum should be ordered from lowest to highest")
    void testEnumOrdering() {
        RankTier[] ranks = RankTier.values();
        assertEquals(6, ranks.length);
        assertEquals(RankTier.DOCKED, ranks[0]);
        assertEquals(RankTier.LAUNCHER, ranks[1]);
        assertEquals(RankTier.ORBITER, ranks[2]);
        assertEquals(RankTier.NAVIGATOR, ranks[3]);
        assertEquals(RankTier.COMMANDER, ranks[4]);
        assertEquals(RankTier.FLEET_ADMIRAL, ranks[5]);
    }
    
    @Test
    @DisplayName("RankTier ordinal values should be in ascending order")
    void testOrdinalOrdering() {
        assertTrue(RankTier.DOCKED.ordinal() < RankTier.LAUNCHER.ordinal());
        assertTrue(RankTier.LAUNCHER.ordinal() < RankTier.ORBITER.ordinal());
        assertTrue(RankTier.ORBITER.ordinal() < RankTier.NAVIGATOR.ordinal());
        assertTrue(RankTier.NAVIGATOR.ordinal() < RankTier.COMMANDER.ordinal());
        assertTrue(RankTier.COMMANDER.ordinal() < RankTier.FLEET_ADMIRAL.ordinal());
    }
}

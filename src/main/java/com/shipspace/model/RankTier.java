package com.shipspace.model;

/**
 * Enumeration representing the rank tiers in the ShipSpace progression system.
 * Each rank tier corresponds to a specific level range.
 * 
 * <p>Rank tiers are ordered from lowest to highest:
 * DOCKED (levels 1-5), LAUNCHER (levels 6-10), ORBITER (levels 11-15),
 * NAVIGATOR (levels 16-20), COMMANDER (levels 21-25), FLEET_ADMIRAL (levels 26-30).
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 2.1, 2.3, 8.1-8.6</a>
 */
public enum RankTier {
    /**
     * Entry-level rank for levels 1-5.
     */
    DOCKED(1, 5),
    
    /**
     * Second rank tier for levels 6-10.
     */
    LAUNCHER(6, 10),
    
    /**
     * Third rank tier for levels 11-15.
     */
    ORBITER(11, 15),
    
    /**
     * Fourth rank tier for levels 16-20.
     */
    NAVIGATOR(16, 20),
    
    /**
     * Fifth rank tier for levels 21-25.
     */
    COMMANDER(21, 25),
    
    /**
     * Highest rank tier for levels 26-30.
     */
    FLEET_ADMIRAL(26, 30);
    
    private final int minLevel;
    private final int maxLevel;
    
    /**
     * Constructs a RankTier with the specified level range.
     * 
     * @param minLevel the minimum level for this rank tier (inclusive)
     * @param maxLevel the maximum level for this rank tier (inclusive)
     */
    RankTier(int minLevel, int maxLevel) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }
    
    /**
     * Returns the minimum level for this rank tier.
     * 
     * @return the minimum level (inclusive)
     */
    public int getMinLevel() {
        return minLevel;
    }
    
    /**
     * Returns the maximum level for this rank tier.
     * 
     * @return the maximum level (inclusive)
     */
    public int getMaxLevel() {
        return maxLevel;
    }
    
    /**
     * Determines the appropriate rank tier for a given level.
     * 
     * <p>This method maps levels to rank tiers according to the following ranges:
     * <ul>
     *   <li>Levels 1-5: DOCKED</li>
     *   <li>Levels 6-10: LAUNCHER</li>
     *   <li>Levels 11-15: ORBITER</li>
     *   <li>Levels 16-20: NAVIGATOR</li>
     *   <li>Levels 21-25: COMMANDER</li>
     *   <li>Levels 26-30: FLEET_ADMIRAL</li>
     * </ul>
     * 
     * @param level the user's level (expected to be between 1 and 30)
     * @return the corresponding RankTier for the given level
     * @throws IllegalArgumentException if level is less than 1 or greater than 30
     */
    public static RankTier fromLevel(int level) {
        if (level < 1 || level > 30) {
            throw new IllegalArgumentException("Level must be between 1 and 30, got: " + level);
        }
        
        for (RankTier rank : values()) {
            if (level >= rank.minLevel && level <= rank.maxLevel) {
                return rank;
            }
        }
        
        // This should never happen if the enum is properly configured
        // and the level is within valid range, but provide a fallback
        return DOCKED;
    }
}

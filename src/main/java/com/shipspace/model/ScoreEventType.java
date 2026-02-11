package com.shipspace.model;

/**
 * Enumeration representing the types of scoring events in the ShipSpace progression system.
 * Each event type has an associated point value that is awarded to users when the event occurs.
 * 
 * <p>Event types and their point values:
 * <ul>
 *   <li>PROJECT_CREATED: 50 points</li>
 *   <li>PROJECT_UPDATED: 20 points (daily cap of 5 events)</li>
 *   <li>COLLAB_ACCEPTED: 10 points</li>
 *   <li>QUALITY_COMMENT: 5 points</li>
 * </ul>
 * 
 * <p>Some event types have daily caps to prevent score farming. Currently, only
 * PROJECT_UPDATED has a daily cap of 5 events per user per calendar day.
 * 
 * @see <a href=".kiro/specs/rank-level-progression/requirements.md">Requirements 2.2, 2.4, 3.1-3.4, 4.1</a>
 */
public enum ScoreEventType {
    /**
     * Event triggered when a user creates a new project.
     * Awards 50 points with no daily cap.
     */
    PROJECT_CREATED(50),
    
    /**
     * Event triggered when a user updates an existing project.
     * Awards 20 points with a daily cap of 5 events per user per day.
     */
    PROJECT_UPDATED(20),
    
    /**
     * Event triggered when a collaboration request is accepted.
     * Awards 10 points with no daily cap.
     */
    COLLAB_ACCEPTED(10),
    
    /**
     * Event triggered when a comment meets qualification criteria
     * (length >= 50 characters and upvotes >= 2).
     * Awards 5 points with no daily cap.
     */
    QUALITY_COMMENT(5);
    
    private final int points;
    
    /**
     * Constructs a ScoreEventType with the specified point value.
     * 
     * @param points the number of points awarded for this event type
     */
    ScoreEventType(int points) {
        this.points = points;
    }
    
    /**
     * Returns the number of points awarded for this event type.
     * 
     * @return the point value for this event type
     */
    public int getPoints() {
        return points;
    }
    
    /**
     * Determines whether this event type has a daily cap on the number
     * of times it can be applied per user per calendar day.
     * 
     * <p>Currently, only PROJECT_UPDATED has a daily cap.
     * 
     * @return true if this event type has a daily cap, false otherwise
     */
    public boolean hasDailyCap() {
        return this == PROJECT_UPDATED;
    }
    
    /**
     * Returns the daily cap for this event type, which is the maximum number
     * of times this event can be applied per user per calendar day.
     * 
     * <p>For event types without a daily cap, this method returns Integer.MAX_VALUE
     * to indicate no practical limit.
     * 
     * @return the daily cap value (5 for PROJECT_UPDATED, Integer.MAX_VALUE for others)
     */
    public int getDailyCap() {
        return this == PROJECT_UPDATED ? 5 : Integer.MAX_VALUE;
    }
}

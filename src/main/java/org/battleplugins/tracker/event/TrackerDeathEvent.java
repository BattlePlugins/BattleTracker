package org.battleplugins.tracker.event;

import org.battleplugins.tracker.Tracker;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Called when a player dies and has their data
 * tracked by BattleTracker.
 */
public class TrackerDeathEvent extends PlayerEvent {
    private final static HandlerList HANDLERS = new HandlerList();

    private final Tracker tracker;
    private final DeathType deathType;
    private final Entity killer;
    private final PlayerDeathEvent deathEvent;

    public TrackerDeathEvent(Tracker tracker, DeathType deathType, @Nullable Entity killer, PlayerDeathEvent deathEvent) {
        super(deathEvent.getEntity());

        this.tracker = tracker;
        this.deathType = deathType;
        this.killer = killer;
        this.deathEvent = deathEvent;
    }

    /**
     * Returns the {@link Tracker} instance this
     * tally was recorded in.
     *
     * @return the Tracker instance
     */
    public Tracker getTracker() {
        return this.tracker;
    }

    /**
     * Returns the type of death the player experienced.
     *
     * @return the type of death
     */
    public DeathType getDeathType() {
        return this.deathType;
    }

    /**
     * Returns the entity that killed the player.
     *
     * @return the entity that killed the player
     */
    public Optional<Entity> killer() {
        return Optional.ofNullable(this.killer);
    }

    /**
     * Returns the entity that killed the player.
     *
     * @return the entity that killed the player
     */
    @Nullable
    public Entity getKiller() {
        return this.killer;
    }

    /**
     * Returns the backing {@link PlayerDeathEvent}.
     *
     * @return the backing PlayerDeathEvent
     */
    public PlayerDeathEvent getDeathEvent() {
        return this.deathEvent;
    }

    /**
     * Returns the type of death the player experienced.
     */
    public enum DeathType {
        /**
         * The player died to another player.
         */
        PLAYER,
        /**
         * The player died to an entity.
         */
        ENTITY,
        /**
         * The player died to the world.
         */
        WORLD
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

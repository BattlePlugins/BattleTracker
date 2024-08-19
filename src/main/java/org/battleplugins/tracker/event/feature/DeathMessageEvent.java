package org.battleplugins.tracker.event.feature;

import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.Tracker;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player dies and has their death message
 * displayed by BattleTracker.
 */
public class DeathMessageEvent extends PlayerEvent {
    private final static HandlerList HANDLERS = new HandlerList();

    private Component deathMessage;
    private final Tracker tracker;

    public DeathMessageEvent(Player player, Component deathMessage, Tracker tracker) {
        super(player);

        this.deathMessage = deathMessage;
        this.tracker = tracker;
    }

    /**
     * Returns the {@link Tracker} instance this
     * death message was displayed for.
     *
     * @return the Tracker instance
     */
    public Tracker getTracker() {
        return this.tracker;
    }

    /**
     * Returns the death message that will be displayed.
     *
     * @return the death message
     */
    public Component getDeathMessage() {
        return deathMessage;
    }

    /**
     * Sets the death message that will be displayed.
     *
     * @param deathMessage the death message
     */
    public void setDeathMessage(Component deathMessage) {
        this.deathMessage = deathMessage;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

package org.battleplugins.tracker.feature.message;

import net.kyori.adventure.text.Component;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.event.TrackerDeathEvent;
import org.battleplugins.tracker.event.feature.DeathMessageEvent;
import org.battleplugins.tracker.util.ItemCollection;
import org.battleplugins.tracker.util.Util;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class DeathMessagesListener implements Listener {
    private final DeathMessages deathMessages;
    private final Tracker tracker;

    public DeathMessagesListener(DeathMessages deathMessages, Tracker tracker) {
        this.deathMessages = deathMessages;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTrackerDeath(TrackerDeathEvent event) {
        if (!event.getTracker().equals(this.tracker)) {
            return;
        }

        if (event.getDeathType() == TrackerDeathEvent.DeathType.PLAYER && this.deathMessages.playerMessages().enabled()) {
            this.onPlayerDeath(event);
        } else if (event.getDeathType() == TrackerDeathEvent.DeathType.ENTITY && this.deathMessages.entityMessages().enabled()) {
            this.onEntityDeath(event);
        } else if (event.getDeathType() == TrackerDeathEvent.DeathType.WORLD && this.deathMessages.worldMessages().enabled()) {
            this.onWorldDeath(event);
        }
    }

    private void onPlayerDeath(TrackerDeathEvent event) {
        PlayerMessages messages = this.deathMessages.playerMessages();
        Player player = event.getDeathEvent().getPlayer();

        // If we've recorded a PVP death, we can assume the killer is a player and make
        // a few assumptions about the killer player.
        Player killer = (Player) event.getKiller();

        ItemStack item = killer.getInventory().getItem(killer.getActiveItemHand());
        Component deathMessage = null;
        for (Map.Entry<ItemCollection, List<Component>> entry : messages.messages().entrySet()) {
            if (!entry.getKey().contains(item.getType())) {
                continue;
            }

            Component message = Util.getRandom(entry.getValue());
            deathMessage = replace(message, killer, player, item);
        }

        if (deathMessage == null) {
            Component defaultMessage = Util.getRandom(messages.defaultMessages());
            deathMessage = replace(defaultMessage, killer, player, item);
        }

        event.getDeathEvent().deathMessage(null);
        broadcastMessage(this.tracker, this.deathMessages.audience(), deathMessage, player, killer);
    }

    private void onEntityDeath(TrackerDeathEvent event) {
        EntityMessages messages = this.deathMessages.entityMessages();
        Player player = event.getDeathEvent().getPlayer();

        // If we've recorded a PVE death, we can assume the killer is a player and make
        // a few assumptions about the killer player.
        Entity killer = event.getKiller();

        Component deathMessage = null;
        for (Map.Entry<EntityType, List<Component>> entry : messages.messages().entrySet()) {
            if (entry.getKey() != killer.getType()) {
                continue;
            }

            Component message = Util.getRandom(entry.getValue());
            deathMessage = replace(message, player, player, null);
        }

        if (deathMessage == null) {
            Component defaultMessage = Util.getRandom(messages.defaultMessages());
            deathMessage = replace(defaultMessage, player, player, null);
        }

        event.getDeathEvent().deathMessage(null);
        broadcastMessage(this.tracker, this.deathMessages.audience(), deathMessage, player, player);
    }

    private void onWorldDeath(TrackerDeathEvent event) {
        WorldMessages messages = this.deathMessages.worldMessages();
        Player player = event.getDeathEvent().getPlayer();

        EntityDamageEvent lastDamageCause = player.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = lastDamageCause == null ? null : lastDamageCause.getCause();

        Component deathMessage = null;
        for (Map.Entry<EntityDamageEvent.DamageCause, List<Component>> entry : messages.messages().entrySet()) {
            if (entry.getKey() != cause) {
                continue;
            }

            Component message = Util.getRandom(entry.getValue());
            deathMessage = replace(message, player, player, null);
        }

        if (deathMessage == null) {
            Component defaultMessage = Util.getRandom(messages.defaultMessages());
            deathMessage = replace(defaultMessage, player, player, null);
        }

        event.getDeathEvent().deathMessage(null);
        broadcastMessage(this.tracker, this.deathMessages.audience(), deathMessage, player, player);
    }
    
    private static void broadcastMessage(Tracker tracker, MessageAudience audience, Component message, Player player, Player target) {
        DeathMessageEvent event = new DeathMessageEvent(player, message, tracker);
        event.callEvent();
        
        if (event.getDeathMessage().equals(Component.empty())) {
            return;
        }
        
        audience.broadcastMessage(event.getDeathMessage(), player, target);
    }

    private static Component replace(Component component, Entity player, Entity target, @Nullable ItemStack item) {
        component = component.replaceText(builder -> builder.matchLiteral("%player%").once().replacement(player.name()));
        component = component.replaceText(builder -> builder.matchLiteral("%target%").once().replacement(target.name()));
        if (item != null) {
            Component itemName;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                itemName = item.getItemMeta().displayName();
            } else {
                itemName = Component.translatable(item.getType());
            }

            itemName = itemName.hoverEvent(item.asHoverEvent());

            Component finalItemName = itemName;
            component = component.replaceText(builder -> builder.matchLiteral("%item%").once().replacement(finalItemName));
        }

        return component;
    }
}

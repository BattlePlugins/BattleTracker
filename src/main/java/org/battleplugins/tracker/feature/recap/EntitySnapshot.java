package org.battleplugins.tracker.feature.recap;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public record EntitySnapshot(EntityType type, Component displayedName) {

    public EntitySnapshot(Entity entity) {
        this(entity.getType(), entity instanceof Player player ? player.name() : Component.translatable(entity.getType()));
    }
}

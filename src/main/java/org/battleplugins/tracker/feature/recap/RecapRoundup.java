package org.battleplugins.tracker.feature.recap;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.util.Util;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecapRoundup {

    public static void recapItem(Audience sender, BattleRecap recap) {
        Messages.send(sender, "header", Messages.getPlain("recap-damage-item"));
        Map<ItemStack, List<RecapEntry>> entriesByStack = new Object2ObjectOpenCustomHashMap<>(new Hash.Strategy<>() {

            @Override
            public int hashCode(ItemStack o) {
                if (o.getType() == Material.AIR) {
                    return o.getType().hashCode();
                }

                int hash = 1;

                hash = hash * 31 + o.getType().hashCode();
                hash = hash * 31 + (o.hasItemMeta() ? (o.getItemMeta().hashCode()) : 0);
                return hash;
            }

            @Override
            public boolean equals(ItemStack a, ItemStack b) {
                if (a == null && b == null) {
                    return true;
                }

                if (a == null && b.getType() == Material.AIR) {
                    return true;
                }

                if (a != null && a.getType() == Material.AIR && b == null) {
                    return true;
                }

                if (a != null && b != null && a.getType() == Material.AIR && b.getType() == Material.AIR) {
                    return true;
                }

                return a != null && a.isSimilar(b);
            }
        });

        for (RecapEntry entry : recap.getEntries()) {
            if (entry.itemUsed() == null || entry.kind() != RecapEntry.Kind.LOSS) {
                continue;
            }

            entriesByStack.computeIfAbsent(entry.itemUsed(), k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<ItemStack, List<RecapEntry>> entry : entriesByStack.entrySet()) {
            double damage = entry.getValue().stream().mapToDouble(RecapEntry::amount).sum();
            if (damage == 0) {
                continue;
            }

            TranslatableComponent itemComponent = Component.translatable(entry.getKey());
            if (entry.getKey().getType() != Material.AIR) {
                itemComponent = itemComponent.hoverEvent(entry.getKey());
            }

            Messages.send(sender, "recap-log-item", Map.of(
                    "item", itemComponent,
                    "hits", Integer.toString(entry.getValue().size()),
                    "damage", Util.HEALTH_FORMAT.format(damage)
            ));
        }

        Messages.send(sender, "recap-log-general", Map.of(
                "time", Util.toTimeStringShort(Duration.between(recap.getCreateTime(), recap.getLastEntry().logTime())),
                "health", Util.formatHealth(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.GAIN).mapToDouble(RecapEntry::amount).sum(), false),
                "damage", Util.HEALTH_FORMAT.format(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.LOSS).mapToDouble(RecapEntry::amount).sum())
        ));
    }
    
    public static void recapPlayer(Audience sender, BattleRecap recap) {
        Messages.send(sender, "header", Messages.getPlain("recap-damage-player"));
        Map<EntitySnapshot, List<RecapEntry>> entriesByStack = new HashMap<>();

        for (RecapEntry entry : recap.getEntries()) {
            if (entry.kind() != RecapEntry.Kind.LOSS) {
                continue;
            }

            if (entry.causingEntity() == null) {
                continue;
            }

            entriesByStack.computeIfAbsent(entry.causingEntity(), k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<EntitySnapshot, List<RecapEntry>> entry : entriesByStack.entrySet()) {
            if (entry.getKey().type() != EntityType.PLAYER) {
                continue;
            }

            double damage = entry.getValue().stream().mapToDouble(RecapEntry::amount).sum();
            if (damage == 0) {
                continue;
            }

            Messages.send(sender, "recap-log-player", Map.of(
                    "player", entry.getKey().displayedName(),
                    "hits", Integer.toString(entry.getValue().size()),
                    "damage", Util.HEALTH_FORMAT.format(damage)
            ));
        }

        Messages.send(sender, "recap-log-general", Map.of(
                "time", Util.toTimeStringShort(Duration.between(recap.getCreateTime(), recap.getLastEntry().logTime())),
                "health", Util.formatHealth(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.GAIN).mapToDouble(RecapEntry::amount).sum(), false),
                "damage", Util.HEALTH_FORMAT.format(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.LOSS).mapToDouble(RecapEntry::amount).sum())
        ));
    }

    public static void recapEntity(Audience sender, BattleRecap recap) {
        Messages.send(sender, "header", Messages.getPlain("recap-damage-entity"));
        Map<EntitySnapshot, List<RecapEntry>> entriesByStack = new HashMap<>();

        for (RecapEntry entry : recap.getEntries()) {
            if (entry.kind() != RecapEntry.Kind.LOSS) {
                continue;
            }

            if (entry.causingEntity() == null) {
                continue;
            }

            entriesByStack.computeIfAbsent(entry.causingEntity(), k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<EntitySnapshot, List<RecapEntry>> entry : entriesByStack.entrySet()) {
            double damage = entry.getValue().stream().mapToDouble(RecapEntry::amount).sum();
            if (damage == 0) {
                continue;
            }

            Component entityComponent = entry.getKey().displayedName();
            if (entry.getKey().type() == EntityType.PLAYER) {
                entityComponent = entityComponent.append(Component.text(" ("))
                        .append(Component.translatable(entry.getKey().type()))
                        .append(Component.text(")"));
            }

            Messages.send(sender, "recap-log-entity", Map.of(
                    "entity", entityComponent,
                    "hits", Integer.toString(entry.getValue().size()),
                    "damage", Util.HEALTH_FORMAT.format(damage)
            ));
        }

        Messages.send(sender, "recap-log-general", Map.of(
                "time", Util.toTimeStringShort(Duration.between(recap.getCreateTime(), recap.getLastEntry().logTime())),
                "health", Util.formatHealth(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.GAIN).mapToDouble(RecapEntry::amount).sum(), false),
                "damage", Util.HEALTH_FORMAT.format(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.LOSS).mapToDouble(RecapEntry::amount).sum())
        ));
    }

    public static void recapSource(Audience sender, BattleRecap recap) {
        Messages.send(sender, "header", Messages.getPlain("recap-damage-cause"));
        Map<EntityDamageEvent.DamageCause, List<RecapEntry>> entriesByStack = new HashMap<>();

        for (RecapEntry entry : recap.getEntries()) {
            if (entry.kind() != RecapEntry.Kind.LOSS) {
                continue;
            }

            if (entry.damageCause() == null) {
                continue;
            }

            entriesByStack.computeIfAbsent(entry.damageCause(), k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<EntityDamageEvent.DamageCause, List<RecapEntry>> entry : entriesByStack.entrySet()) {
            double damage = entry.getValue().stream().mapToDouble(RecapEntry::amount).sum();
            if (damage == 0) {
                continue;
            }

            // TODO: Translation support
            Component causeComponent = Component.text(Util.capitalize(entry.getKey().name().toLowerCase(Locale.ROOT).replace("_", " ")));
            Messages.send(sender, "recap-log-cause", Map.of(
                    "cause", causeComponent,
                    "hits", Integer.toString(entry.getValue().size()),
                    "damage", Util.HEALTH_FORMAT.format(damage)
            ));
        }

        Messages.send(sender, "recap-log-general", Map.of(
                "time", Util.toTimeStringShort(Duration.between(recap.getCreateTime(), recap.getLastEntry().logTime())),
                "health", Util.formatHealth(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.GAIN).mapToDouble(RecapEntry::amount).sum(), false),
                "damage", Util.HEALTH_FORMAT.format(recap.getEntries().stream().filter(entry -> entry.kind() == RecapEntry.Kind.LOSS).mapToDouble(RecapEntry::amount).sum())
        ));
    }

    public static void sendFooter(Audience audience, Tracker tracker, BattleRecap recap) {
        if (tracker.tracksData(TrackedDataType.PVP)) {
            Messages.send(audience, "recap-footer-pvp", Map.of(
                    "tracker", tracker.getName().toLowerCase(Locale.ROOT),
                    "player", recap.getRecapOwner()
            ));
        } else {
            Messages.send(audience, "recap-footer-pve", Map.of(
                    "tracker", tracker.getName().toLowerCase(Locale.ROOT),
                    "player", recap.getRecapOwner()
            ));
        }
    }
}

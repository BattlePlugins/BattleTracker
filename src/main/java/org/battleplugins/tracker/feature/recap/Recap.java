package org.battleplugins.tracker.feature.recap;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.TrackedDataType;
import org.battleplugins.tracker.Tracker;
import org.battleplugins.tracker.event.feature.DeathMessageEvent;
import org.battleplugins.tracker.feature.TrackerFeature;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.util.TrackerInventoryHolder;
import org.battleplugins.tracker.util.Util;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public record Recap(
        boolean enabled,
        DisplayContent displayContent,
        boolean hoverRecap,
        Map<UUID, BattleRecap> previousRecaps,
        Map<UUID, BattleRecap> recaps
) implements TrackerFeature {

    public Recap(boolean enabled, DisplayContent displayContent, boolean hoverRecap) {
        this(enabled, displayContent, hoverRecap, new HashMap<>(), new HashMap<>());
    }

    public static Recap load(ConfigurationSection section) {
        return new Recap(
                section.getBoolean("enabled", true),
                DisplayContent.valueOf(section.getString("display-content", "ALL").toUpperCase(Locale.ROOT)),
                section.getBoolean("hover-recap", true)
        );
    }

    @Override
    public void onEnable(BattleTracker battleTracker, Tracker tracker) {
        battleTracker.registerListener(tracker, new RecapListener(tracker, this));
    }

    public BattleRecap getRecap(Player player) {
        return this.recaps.computeIfAbsent(player.getUniqueId(), uuid -> new BattleRecap(player));
    }

    public Optional<BattleRecap> previousRecap(Player player) {
        return Optional.ofNullable(this.getPreviousRecap(player));
    }

    @Nullable
    public BattleRecap getPreviousRecap(Player player) {
        return this.previousRecaps.get(player.getUniqueId());
    }

    @NotNull
    public static ItemStack getRecapItem(Duration deathDuration, BattleRecap battleRecap) {
        ItemStack recapItem = new ItemStack(Material.BOOK);
        recapItem.editMeta(meta -> {
            meta.displayName(Messages.get("recap-info").decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Messages.get("recap-death-time", Util.toTimeStringShort(deathDuration)));
            lore.add(Messages.get("recap-starting-health", Util.formatHealth(battleRecap.getStartingHealth(), false)));
            lore.add(Component.empty());
            lore.add(Messages.get("recap-damage-log"));

            processRecapEntry(battleRecap.getCombinedEntries(), 10, true, lore::add);

            lore.add(Component.empty());
            lore.add(Messages.get("recap-click-for-more"));

            // Make sure the lore lines are not italic
            ListIterator<Component> itr = lore.listIterator();
            while (itr.hasNext()) {
                itr.set(itr.next().decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(TrackerInventoryHolder.RECAP_KEY, PersistentDataType.BOOLEAN, true);
        });

        return recapItem;
    }

    public static void processRecapEntry(List<RecapEntry> entries, int max, boolean showItem, Consumer<Component> componentConsumer) {
        for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - max); i--) {
            RecapEntry entry = entries.get(i);
            ItemStack item = entry.itemUsed();

            // TODO: Translation support
            Component cause = null;
            if (entry.causingEntity() != null) {
                cause = entry.causingEntity().displayedName();
                if (entry.sourceEntity() != null && !entry.sourceEntity().equals(entry.causingEntity())) {
                    cause = entry.sourceEntity().displayedName()
                            .append(Component.text(" ("))
                            .append(entry.causingEntity().displayedName())
                            .append(Component.text(")"));
                } else if (showItem && item != null && entry.causingEntity().type() == EntityType.PLAYER) {
                    Component itemName;
                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                        itemName = item.getItemMeta().displayName();
                    } else {
                        itemName = Component.translatable(item.getType());
                    }

                    cause = cause.append(Component.text(" (").append(itemName).append(Component.text(")")));
                }
            }

            if (cause == null && entry.damageCause() != null) {
                cause = Component.text(Util.capitalize(entry.damageCause().name().toLowerCase(Locale.ROOT).replace("_", " ")));
            }

            boolean loss = entry.kind() == RecapEntry.Kind.LOSS;
            Component recapLog = Messages.get("recap-log", Map.of(
                    "health", Component.text(Util.formatHealth(entry.amount(), loss), loss ? NamedTextColor.RED : NamedTextColor.GREEN),
                    "time", Util.toTimeStringShort(Duration.between(entry.logTime(), Instant.now().plusSeconds(1))),
                    "damage_cause", cause == null ? Component.empty() : cause
            ));

            componentConsumer.accept(recapLog);
        }
    }

    public void showRecap(Audience audience, Tracker tracker, BattleRecap battleRecap) {
        Duration deathDuration = Duration.between(battleRecap.getLastEntry().logTime(), Instant.now());

        ItemStack recapItem = Recap.getRecapItem(deathDuration, battleRecap);

        Inventory inventory = TrackerInventoryHolder.create(TrackerInventoryHolder.RECAP_KEY, tracker, 54, Messages.get("recap"), handler -> {
            handler.onClick(TrackerInventoryHolder.RECAP_KEY, () -> {
                if (tracker.tracksData(TrackedDataType.PVP)) {
                    RecapRoundup.recapPlayer(audience, battleRecap);
                } else if (tracker.tracksData(TrackedDataType.PVE)) {
                    RecapRoundup.recapEntity(audience, battleRecap);
                } else {
                    RecapRoundup.recapSource(audience, battleRecap);
                }

                RecapRoundup.sendFooter(audience, tracker, battleRecap);
            });
        });
        Recap.DisplayContent displayContent = this.displayContent;
        if (displayContent == Recap.DisplayContent.ALL) {
            if (!(audience instanceof Player senderPlayer)) {
                Messages.send(audience, "command-must-be-player");
                return;
            }

            ItemStack[] snapshot = battleRecap.getInventorySnapshot();
            for (int i = 0; i < snapshot.length; i++) {
                inventory.setItem(i, snapshot[i]);
            }

            inventory.setItem(52, recapItem);

            senderPlayer.openInventory(inventory);
        } else if (displayContent == Recap.DisplayContent.ARMOR) {
            if (!(audience instanceof Player senderPlayer)) {
                Messages.send(audience, "command-must-be-player");
                return;
            }

            ItemStack empty = new ItemStack(Material.BONE);
            empty.editMeta(meta -> meta.displayName(Component.empty()));

            ItemStack[] snapshot = battleRecap.getInventorySnapshot();
            inventory.setItem(13, Optional.ofNullable(snapshot[36]).orElse(empty));
            inventory.setItem(22, Optional.ofNullable(snapshot[37]).orElse(empty));
            inventory.setItem(31, Optional.ofNullable(snapshot[38]).orElse(empty));
            inventory.setItem(40, Optional.ofNullable(snapshot[39]).orElse(empty));

            inventory.setItem(21, Optional.ofNullable(snapshot[40]).orElse(empty));
            inventory.setItem(23, Optional.ofNullable(snapshot[41]).orElse(empty));

            inventory.setItem(25, recapItem);

            senderPlayer.openInventory(inventory);
        } else if (displayContent == Recap.DisplayContent.RECAP) {
            // TODO: Send paginated chat message
        }
    }

    public enum DisplayContent {
        ALL,
        ARMOR,
        RECAP
    }

    private record RecapListener(Tracker tracker, Recap recap) implements Listener {

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            this.recap.recaps.remove(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void onDeath(PlayerDeathEvent event) {
            Player player = event.getEntity();
            BattleRecap recap = this.recap.getRecap(player);
            recap.markDeath();

            this.recap.previousRecaps.put(player.getUniqueId(), recap);
            this.recap.recaps.remove(player.getUniqueId());
        }

        @EventHandler
        public void onHealthRegain(EntityRegainHealthEvent event) {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            BattleRecap recap = this.recap.getRecap(player);
            recap.record(RecapEntry.builder()
                    .amount(event.getAmount())
                    .logTime(Instant.now())
                    .kind(RecapEntry.Kind.GAIN)
                    .build());
        }

        @EventHandler
        public void onDeathMessage(DeathMessageEvent event) {
            if (!this.tracker.equals(event.getTracker())) {
                return;
            }

            if (!this.recap.hoverRecap) {
                return;
            }

            Component deathMessage = event.getDeathMessage();
            if (deathMessage == null) {
                return;
            }

            BattleRecap recap = this.recap.previousRecaps.get(event.getPlayer().getUniqueId());
            if (recap == null) {
                return;
            }

            List<Component> lines = new ArrayList<>();
            processRecapEntry(recap.getCombinedEntries(), 5, false, lines::add);

            deathMessage = deathMessage.hoverEvent(
                    HoverEvent.showText(Messages.get("recap-damage-log")
                            .append(Component.newline())
                            .append(Component.join(JoinConfiguration.newlines(), lines))
                            .append(Component.newline())
                            .append(Component.newline())
                            .append(Messages.get("recap-click-for-more"))
                    ));

            deathMessage = deathMessage.clickEvent(ClickEvent.callback(audience -> this.recap.showRecap(audience, this.tracker, recap), builder -> builder.lifetime(Duration.ofMinutes(5)).uses(ClickCallback.UNLIMITED_USES)));
            event.setDeathMessage(deathMessage);
        }
    }
}

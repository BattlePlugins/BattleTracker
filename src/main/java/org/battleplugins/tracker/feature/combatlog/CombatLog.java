package org.battleplugins.tracker.feature.combatlog;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.TitlePart;
import org.battleplugins.tracker.BattleTracker;
import org.battleplugins.tracker.feature.Feature;
import org.battleplugins.tracker.message.Messages;
import org.battleplugins.tracker.util.MessageType;
import org.battleplugins.tracker.util.Util;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CombatLog implements Feature {
    private final boolean enabled;
    private final List<String> disabledWorlds;
    private final int combatTime;
    private final boolean combatSelf;
    private final boolean combatEntities;
    private final boolean combatPlayers;
    private final @Nullable MessageType displayMethod;
    private final boolean allowPermissionBypass;
    private final Set<EntityType> disabledEntities;
    private final List<String> disabledCommands;

    private CombatLogListener listener;

    public CombatLog(
            boolean enabled,
            List<String> disabledWorlds,
            int combatTime,
            boolean combatSelf,
            boolean combatEntities,
            boolean combatPlayers,
            @Nullable MessageType displayMethod,
            boolean allowPermissionBypass,
            Set<EntityType> disabledEntities,
            List<String> disabledCommands
    ) {
        this.enabled = enabled;
        this.disabledWorlds = disabledWorlds;
        this.combatTime = combatTime;
        this.combatSelf = combatSelf;
        this.combatEntities = combatEntities;
        this.combatPlayers = combatPlayers;
        this.displayMethod = displayMethod;
        this.allowPermissionBypass = allowPermissionBypass;
        this.disabledEntities = disabledEntities;
        this.disabledCommands = disabledCommands;
    }

    @Override
    public void onEnable(BattleTracker battleTracker) {
        battleTracker.getServer().getPluginManager().registerEvents(this.listener = new CombatLogListener(battleTracker, this), battleTracker);
    }

    @Override
    public void onDisable(BattleTracker battleTracker) {
        if (this.listener != null) {
            this.listener.onUnload();
        }
    }

    public boolean isInCombat(Player player) {
        if (this.listener == null) {
            return false;
        }

        return this.listener.combatTasks.containsKey(player);
    }

    public static CombatLog load(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled");
        if (!enabled) {
            return new CombatLog(false, List.of(), 0, false, false, false, MessageType.ACTION_BAR, false, Set.of(), List.of());
        }

        List<String> disabledWorlds = section.getStringList("disabled-worlds");
        int combatTime = section.getInt("combat-time");
        boolean combatSelf = section.getBoolean("combat-self");
        boolean combatEntities = section.getBoolean("combat-entities");
        boolean combatPlayers = section.getBoolean("combat-players");
        MessageType displayMethod = Optional.ofNullable(section.getString("display-method"))
                .map(method -> MessageType.valueOf(method.toUpperCase(Locale.ROOT)))
                .orElse(null);
        boolean allowPermissionBypass = section.getBoolean("allow-permission-bypass");

        Set<EntityType> disabledEntities = section.getStringList("disabled-entities")
                .stream()
                .map(key -> Registry.ENTITY_TYPE.get(NamespacedKey.fromString(key)))
                .collect(Collectors.toSet());

        List<String> disabledCommands = section.getStringList("disabled-commands");

        return new CombatLog(true, disabledWorlds, combatTime, combatSelf, combatEntities, combatPlayers, displayMethod, allowPermissionBypass, disabledEntities, disabledCommands);
    }

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    private static class CombatLogListener implements Listener {
        private static final String BOSS_BAR_META_KEY = "combat-log-bar";

        private final Map<Player, CombatEntry> combatTasks = new HashMap<>();

        private final BattleTracker battleTracker;
        private final CombatLog combatLog;

        private BukkitTask tickTask;

        private CombatLogListener(BattleTracker battleTracker, CombatLog combatLog) {
            this.battleTracker = battleTracker;
            this.combatLog = combatLog;

            if (combatLog.displayMethod == null) {
                return;
            }

            this.tickTask = this.battleTracker.getServer().getScheduler().runTaskTimer(this.battleTracker, this::tick, 0L, 20L);
        }

        public void onUnload() {
            HandlerList.unregisterAll(this);

            if (this.tickTask != null) {
                this.tickTask.cancel();
            }
        }

        private void tick() {
            for (Map.Entry<Player, CombatEntry> entry : this.combatTasks.entrySet()) {
                Duration remainingTime = Duration.ofSeconds(this.combatLog.combatTime)
                        .minus(Duration.ofMillis(System.currentTimeMillis() - entry.getValue().enteredCombat()))
                        .plusSeconds(1);

                if (remainingTime.isZero() || remainingTime.isNegative()) {
                    continue;
                }

                Component message = Messages.get("combat-log-remaining-time", Util.toTimeString(remainingTime));

                Player player = entry.getKey();
                switch (this.combatLog.displayMethod) {
                    case ACTION_BAR -> player.sendActionBar(message);
                    case CHAT -> player.sendMessage(message);
                    case TITLE -> player.sendTitlePart(TitlePart.TITLE, message);
                    case SUBTITLE -> {
                        player.sendTitlePart(TitlePart.TITLE, Component.space());
                        player.sendTitlePart(TitlePart.SUBTITLE, message);
                    }
                    case BOSSBAR -> {
                        player.getMetadata(BOSS_BAR_META_KEY).stream()
                                .map(MetadataValue::value)
                                .filter(value -> value instanceof BossBar)
                                .map(value -> (BossBar) value)
                                .findFirst()
                                .ifPresentOrElse(bar -> {
                                    bar.name(message);

                                    float progress = (float) remainingTime.toSeconds() / (float) this.combatLog.combatTime;
                                    bar.progress(progress);
                                }, () -> {
                                    BossBar bar = BossBar.bossBar(message, 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                                    player.showBossBar(bar);

                                    player.setMetadata(BOSS_BAR_META_KEY, new FixedMetadataValue(this.battleTracker, bar));
                                });
                    }
                }
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
            if (this.combatLog.disabledWorlds.contains(event.getEntity().getWorld().getName())) {
                return;
            }

            if (!(event.getEntity() instanceof Player player)) {
                return;
            }

            Entity damager = this.getTrueDamager(event, true);
            if (damager == null) {
                return;
            }

            if (this.combatLog.disabledEntities.contains(damager.getType())) {
                return;
            }

            if (damager instanceof Player damagerPlayer) {
                if (!this.combatLog.combatPlayers) {
                    return;
                }

                if (damagerPlayer.equals(player) && !this.combatLog.combatSelf) {
                    return;
                }

            } else if (!this.combatLog.combatEntities) {
                return;
            }

            this.enterCombat(player);
            if (damager instanceof Player damagerPlayer) {
                this.enterCombat(damagerPlayer);
            }
        }

        @EventHandler
        public void onDeath(PlayerDeathEvent event) {
            if (this.combatLog.disabledWorlds.contains(event.getEntity().getWorld().getName())) {
                return;
            }

            if (event.getPlayer().getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
                Entity damager = this.getTrueDamager(damageEvent, false);
                if (damager instanceof Player damagerPlayer && this.combatTasks.containsKey(damagerPlayer)) {
                    this.exitCombat(damagerPlayer);
                }
            }

            if (this.combatTasks.containsKey(event.getEntity())) {
                this.exitCombat(event.getEntity());
            }
        }

        @EventHandler
        public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
            if (!this.combatTasks.containsKey(event.getPlayer())) {
                return;
            }

            if (this.combatLog.disabledCommands.stream().anyMatch(cmd -> event.getMessage().startsWith("/" + cmd))) {
                event.setCancelled(true);

                Messages.send(event.getPlayer(), "combat-log-cannot-run-command");
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            CombatEntry entry = this.combatTasks.get(player);
            if (entry != null) {
                entry.task().cancel();
                this.combatTasks.remove(player);

                // If player has a combat log bypass, exclude them from
                // losing their items on logout
                if (this.combatLog.allowPermissionBypass && player.hasPermission("battletracker.combatlog.bypass")) {
                    return;
                }

                // Kill the player if they log out in combat
                player.setHealth(0);
            }
        }

        private void enterCombat(Player player) {
            CombatEntry entry = this.combatTasks.get(player);
            if (entry != null) {
                entry.task().cancel();
            } else {
                Messages.send(player, "combat-log-entered-combat");
            }

            BukkitTask task = this.battleTracker.getServer().getScheduler().runTaskLater(
                    this.battleTracker,
                    () -> this.exitCombat(player),
                    this.combatLog.combatTime * 20L
            );

            this.combatTasks.put(player, new CombatEntry(task, System.currentTimeMillis()));
        }

        private void exitCombat(Player player) {
            CombatEntry entry = this.combatTasks.remove(player);
            if (entry == null) {
                return;
            }

            entry.task().cancel();

            Messages.send(player, "combat-log-exited-combat");

            // Remove bossbar if it exists
            player.getMetadata(BOSS_BAR_META_KEY).stream()
                    .map(MetadataValue::value)
                    .filter(value -> value instanceof BossBar)
                    .map(value -> (BossBar) value)
                    .findFirst()
                    .ifPresent(bar -> {
                        player.hideBossBar(bar);

                        player.removeMetadata(BOSS_BAR_META_KEY, this.battleTracker);
                    });
        }

        private Entity getTrueDamager(EntityDamageByEntityEvent event, boolean checkIgnored) {
            Entity damager = event.getDamager();
            if (event.getDamager() instanceof Projectile projectile) {
                if (checkIgnored && this.combatLog.disabledEntities.contains(projectile.getType())) {
                    return null;
                }

                if (projectile.getShooter() instanceof Entity entity) {
                    damager = entity;
                }
            }

            if (damager instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() instanceof Entity owner) {
                damager = owner;
            }

            return damager;
        }

        public record CombatEntry(BukkitTask task, long enteredCombat) {
        }
    }
}

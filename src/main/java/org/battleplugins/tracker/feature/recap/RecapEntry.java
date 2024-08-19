package org.battleplugins.tracker.feature.recap;

import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public record RecapEntry(
        @Nullable EntityDamageEvent.DamageCause damageCause,
        double amount,
        @Nullable EntitySnapshot causingEntity,
        @Nullable EntitySnapshot sourceEntity,
        @Nullable ItemStack itemUsed,
        Instant logTime,
        Kind kind
) {

    public RecapEntry(EntityDamageEvent.DamageCause damageCause, double amount, Entity causingEntity, Entity sourceEntity, ItemStack itemUsed, Instant logTime, Kind kind) {
        this(damageCause, amount, new EntitySnapshot(causingEntity), new EntitySnapshot(sourceEntity), itemUsed, logTime, kind);
    }

    public Builder toBuilder() {
        return new Builder()
                .damageCause(this.damageCause)
                .amount(this.amount)
                .causingEntity(this.causingEntity)
                .sourceEntity(this.sourceEntity)
                .itemUsed(this.itemUsed)
                .logTime(this.logTime)
                .kind(this.kind);
    }

    public enum Kind {
        GAIN,
        LOSS
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EntityDamageEvent.DamageCause damageCause;
        private double amount;
        private EntitySnapshot causingEntity;
        private EntitySnapshot sourceEntity;
        private ItemStack itemUsed;
        private Instant logTime;
        private Kind kind = Kind.LOSS;

        public Builder damageCause(EntityDamageEvent.DamageCause cause) {
            this.damageCause = cause;
            return this;
        }

        public Builder amount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder causingEntity(Entity causingEntity) {
            this.causingEntity = new EntitySnapshot(causingEntity);
            return this;
        }

        public Builder causingEntity(EntitySnapshot causingEntity) {
            this.causingEntity = causingEntity;
            return this;
        }

        public Builder sourceEntity(Entity sourceEntity) {
            this.sourceEntity = new EntitySnapshot(sourceEntity);
            return this;
        }

        public Builder sourceEntity(EntitySnapshot sourceEntity) {
            this.sourceEntity = sourceEntity;
            return this;
        }

        public Builder itemUsed(ItemStack itemUsed) {
            this.itemUsed = itemUsed;
            return this;
        }

        public Builder logTime(Instant logTime) {
            this.logTime = logTime;
            return this;
        }

        public Builder kind(Kind kind) {
            this.kind = kind;
            return this;
        }

        public RecapEntry build() {
            return new RecapEntry(this.damageCause, this.amount, this.causingEntity, this.sourceEntity, this.itemUsed, this.logTime, this.kind);
        }
    }
}

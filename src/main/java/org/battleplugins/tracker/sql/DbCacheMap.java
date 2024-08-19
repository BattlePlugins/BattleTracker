package org.battleplugins.tracker.sql;

import org.battleplugins.tracker.BattleTracker;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

class DbCacheMap<K, V> implements DbCache.MapCache<K, V> {
    private final Map<K, DbValue<V>> entries = new ConcurrentHashMap<>();

    @Override
    public Set<K> keySet() {
        return this.entries.keySet();
    }

    @Override
    public void put(K key, V value) {
        this.entries.put(key, new DbValue<>(value, true));
    }

    @Override
    public void remove(K key) {
        this.entries.remove(key);
    }

    @Override
    public V getCached(K key) {
        DbValue<V> dbValue = this.entries.get(key);
        if (dbValue == null) {
            return null;
        }

        dbValue.resetLastAccess();
        return dbValue.value;
    }

    @Override
    public CompletableFuture<V> getOrLoad(K key, CompletableFuture<V> loader) {
        if (this.entries.containsKey(key)) {
            return CompletableFuture.completedFuture(this.getCached(key));
        }

        return loader.thenApply(value -> {
            if (value == null) {
                return null;
            }

            this.entries.put(key, new DbValue<>(value, false));
            return value;
        });
    }

    @Override
    public CompletableFuture<? extends Collection<V>> loadBulk(CompletableFuture<? extends Collection<V>> loader, Function<V, K> keyFunction) {
        return loader.thenApply(values -> {
            for (V value : values) {
                K key = keyFunction.apply(value);

                // If we have this data in our cache already, let's use
                // the cache as the source of truth, since the data may
                // have been updated
                if (this.entries.containsKey(key)) {
                    continue;
                }

                this.entries.put(key, new DbValue<>(value, false));
            }

            return values;
        });
    }

    @Override
    public void save(K key, Consumer<V> valueConsumer) {
        DbValue<V> value = this.entries.get(key);
        if (value == null) {
            BattleTracker.getInstance().warn("No value found in cache for key {}", key);
            return;
        }

        if (value.dirty) {
            valueConsumer.accept(value.value);
            value.dirty = false;
        }
    }

    @Override
    public void flush(K key, boolean all) {
        DbValue<V> dbValue = this.entries.get(key);
        if (dbValue == null) {
            this.entries.remove(key);
            return;
        }

        if (!all && !dbValue.shouldFlush()) {
            return;
        }

        // If the db value is locked, do not flush
        if (dbValue.locked) {
            return;
        }

        if (!dbValue.dirty) {
            this.entries.remove(key);
        } else {
            BattleTracker.getInstance().warn("Unsaved DB value found in cache: {} for key {}", dbValue.value, key);
        }
    }

    @Override
    public void lock(K key) {
        DbValue<V> dbValue = this.entries.get(key);
        if (dbValue != null) {
            dbValue.lock();
        }
    }

    @Override
    public void unlock(K key) {
        DbValue<V> dbValue = this.entries.get(key);
        if (dbValue != null) {
            dbValue.unlock();
        }
    }
}

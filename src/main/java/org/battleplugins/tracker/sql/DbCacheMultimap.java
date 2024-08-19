package org.battleplugins.tracker.sql;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import org.battleplugins.tracker.BattleTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

class DbCacheMultimap<K, V> implements DbCache.MultimapCache<K, V> {
    private final ListMultimap<K, DbValue<V>> entries = Multimaps.synchronizedListMultimap(
            MultimapBuilder.hashKeys()
                    .arrayListValues()
                    .build()
    );

    private final Set<K> lockedKeys = new HashSet<>();
    private final Set<K> loadedKeys = new HashSet<>();

    @Override
    public Set<K> keySet() {
        return this.entries.keySet();
    }

    @Override
    public void put(K key, V value) {
        this.entries.put(key, new DbValue<>(value, true));
    }

    @Override
    public void lock(K key) {
        this.lockedKeys.add(key);
    }

    @Override
    public void unlock(K key) {
        this.lockedKeys.remove(key);
    }

    @Override
    public void remove(K key) {
        this.entries.removeAll(key);
    }

    @Override
    public void putAll(K key, Collection<V> values) {
        values.forEach(value -> this.put(key, value));
    }

    @NotNull
    @Override
    public List<V> getCached(K key) {
        if (!this.entries.containsKey(key)) {
            return List.of();
        }

        List<DbValue<V>> entries = this.entries.get(key);
        if (entries.isEmpty()) {
            return List.of();
        }

        List<V> cached = new ArrayList<>(entries.size());
        for (DbValue<V> entry : entries) {
            cached.add(entry.value);
            entry.resetLastAccess();
        }

        return cached;
    }

    @Override
    public CompletableFuture<List<V>> getOrLoad(K key, CompletableFuture<List<V>> loader) {
        if (this.loadedKeys.contains(key)) {
            return CompletableFuture.completedFuture(this.getCached(key));
        }

        List<V> cachedAndLoaded = new ArrayList<>();
        if (this.entries.containsKey(key)) {
            List<DbValue<V>> cachedData = this.entries.get(key);
            cachedAndLoaded.addAll(cachedData.stream()
                    .peek(DbValue::resetLastAccess)
                    .map(dbValue -> dbValue.value)
                    .toList());
        }

        return loader.thenApply(value -> {
            if (value == null) {
                return List.of();
            }

            for (V v : value) {
                this.entries.put(key, new DbValue<>(v, false));
            }

            this.loadedKeys.add(key);

            // If there is cached data, we need to merge the cached data
            // with the loaded data. This will only be called once, so if
            // we take a slight performance hit on load, that's fine, as the
            // data will be cached for future use
            if (!cachedAndLoaded.isEmpty()) {
                cachedAndLoaded.addAll(value);
                return cachedAndLoaded;
            }

            return value;
        });
    }

    @Override
    public CompletableFuture<? extends Collection<List<V>>> loadBulk(CompletableFuture<? extends Collection<List<V>>> loader, Function<List<V>, K> keyFunction) {
        return loader.thenApply(values -> {
            for (List<V> value : values) {
                K key = keyFunction.apply(value);
                for (V v : value) {
                    // If we have this data in our cache already, let's use
                    // the cache as the source of truth, since the data may
                    // have been updated
                    if (this.entries.containsEntry(key, v)) {
                        continue;
                    }

                    this.entries.put(key, new DbValue<>(v, false));
                }

                this.loadedKeys.add(key);
            }

            return values;
        });
    }

    @Override
    public void save(K key, Consumer<V> valueConsumer) {
        this.entries.get(key).forEach(dbValue -> {
            if (dbValue.dirty) {
                valueConsumer.accept(dbValue.value);

                dbValue.dirty = false;
            }
        });

        // If this key has never been loaded from the database
        // before, we need to flush the data from the cache as
        // keeping it here will mean that if a db load is called,
        // the data could exist twice in our cached instance
        if (!this.loadedKeys.contains(key)) {
            this.flush(key, true);
        }
    }

    @Override
    public void flush(K key, boolean all) {
        this.loadedKeys.remove(key);
        if (!this.entries.containsKey(key)) {
            return;
        }

        Iterator<DbValue<V>> iterator = this.entries.get(key).iterator();
        while (iterator.hasNext()) {
            DbValue<V> dbValue = iterator.next();
            if (!all && !dbValue.shouldFlush()) {
                continue;
            }

            // If the db value is locked, do not flush
            if (this.lockedKeys.contains(key)) {
                continue;
            }

            if (!dbValue.dirty) {
                iterator.remove();
            } else {
                BattleTracker.getInstance().warn("Unsaved DB value found in cache: {} for key {}", dbValue.value, key);
            }
        }
    }
}

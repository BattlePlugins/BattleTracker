package org.battleplugins.tracker.sql;

import org.battleplugins.tracker.BattleTracker;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

class DbCacheSet<V> implements DbCache.SetCache<V> {
    private final Set<DbValue<V>> entries = ConcurrentHashMap.newKeySet();

    @Override
    public void add(V value) {
        this.entries.add(new DbValue<>(value, true));
    }

    @Override
    public void modify(V value) {
        for (DbValue<V> entry : this.entries) {
            if (entry.value.equals(value)) {
                entry.dirty = true;
                entry.resetLastAccess();
                return;
            }
        }
    }

    @Override
    public void lock(V value) {
        for (DbValue<V> entry : this.entries) {
            if (entry.value.equals(value)) {
                entry.lock();
                return;
            }
        }
    }

    @Override
    public void unlock(V value) {
        for (DbValue<V> entry : this.entries) {
            if (entry.value.equals(value)) {
                entry.unlock();
                return;
            }
        }
    }

    @Nullable
    @Override
    public V getCached(Predicate<V> predicate) {
        for (DbValue<V> entry : this.entries) {
            if (predicate.test(entry.value)) {
                entry.resetLastAccess();
                return entry.value;
            }
        }

        return null;
    }

    @Override
    public CompletableFuture<V> getOrLoad(Predicate<V> predicate, CompletableFuture<V> loader) {
        V cached = this.getCached(predicate);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return loader.thenApply(value -> {
            if (value == null) {
                return null;
            }

            this.entries.add(new DbValue<>(value, false));
            return value;
        });
    }

    @Override
    public void save(Consumer<V> valueConsumer) {
        this.entries.forEach(dbValue -> {
            if (dbValue.dirty) {
                valueConsumer.accept(dbValue.value);
                dbValue.dirty = false;
            }
        });
    }

    @Override
    public void flush(boolean all) {
        Iterator<DbValue<V>> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            DbValue<V> dbValue = iterator.next();
            if (!all && !dbValue.shouldFlush()) {
                continue;
            }

            if (!dbValue.dirty) {
                iterator.remove();
            } else {
                BattleTracker.getInstance().warn("Unsaved DB value found in cache: {}", dbValue.value);
            }
        }
    }
}

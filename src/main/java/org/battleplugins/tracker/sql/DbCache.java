package org.battleplugins.tracker.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A cache holding data from a database.
 * <p>
 * This is an async, thread-safe cache that can be used to store data
 * from a database. This cache is designed for used of data that is
 * frequently accessed and should be stored in memory for faster access.
 * <p>
 * This cache has flushing capabilities, meaning that the cache can be
 * saved to the database at any time. This is useful for saving data
 * that has been modified in the cache. Additionally, this can be scaled
 * or modified in cases where lots of data is loaded.
 */
public interface DbCache {

    /**
     * Creates a new Set cache.
     *
     * @param <V> the value of the cache
     * @return a new Set cache
     */
    static <V> SetCache<V> createSet() {
        return new DbCacheSet<>();
    }

    /**
     * Creates a new Map cache.
     *
     * @param <K> the key of the cache
     * @param <V> the value of the cache
     * @return a new Map cache
     */
    static <K, V> MapCache<K, V> createMap() {
        return new DbCacheMap<>();
    }

    /**
     * Creates a new Multimap cache.
     *
     * @param <K> the key of the cache
     * @param <V> the value of the cache
     * @return a new Multimap cache
     */
    static <K, V> MultimapCache<K, V> createMultimap() {
        return new DbCacheMultimap<>();
    }

    interface SetCache<V> extends DbCache {

        /**
         * Adds a value to the cache.
         *
         * @param value the value to add
         */
        void add(V value);

        /**
         * Modifies a value in the cache.
         *
         * @param value the value to modify
         */
        void modify(V value);

        /**
         * Locks a value in the cache.
         * <p>
         * This will ensure that an entry is not removed
         * from the cache until it is unlocked.
         *
         * @param value the value to lock
         */
        void lock(V value);

        /**
         * Unlocks a value in the cache.
         * <p>
         * This will allow an entry to be removed from
         * the cache.
         *
         * @param value the value to unlock
         */
        void unlock(V value);

        /**
         * Returns a cached value from the cache immediately.
         * <p>
         * This method should be used when the value is expected to be
         * in the cache. If the value is not in the cache, this method
         * will return null.
         *
         * @param predicate the predicate to get the value from
         * @return the value from the cache, or null if the value is not in the cache
         */
        @Nullable
        V getCached(Predicate<V> predicate);

        /**
         * Returns a value from the cache or loads it if it is not in the cache.
         * <p>
         * This method should be used when the value is not guaranteed to be
         * in the cache. If the value is in the cache, this method will
         * return the value immediately. If the value is not in the cache,
         * this method will load the value from the database and return it.
         *
         * @param predicate the predicate to get the value from. If the predicate returns
         *                  false, the value will be loaded from the database
         * @param loader the loader to load the value from the database
         * @return the value from the cache or the value loaded from the database
         */
        CompletableFuture<V> getOrLoad(Predicate<V> predicate, CompletableFuture<V> loader);

        /**
         * Saves the cache to the database.
         * <p>
         * The consumer will be called for each value in the cache that
         * has been modified. This is useful for saving data that has been
         * modified in the cache.
         *
         * @param value the value to save
         */
        void save(Consumer<V> value);

        /**
         * Flushes the cache.
         * <p>
         * This method will remove the key from the cache and save the
         * values to the database. This is useful for saving data that
         * has been modified in the cache.
         * <p>
         * NOTE: This should be used in conjunction with the
         * {@link #save} method as flushing with
         * unsaved entries will not damageCause the objects to be flushed
         * from memory.
         *
         * @param all whether to flush all entries
         */
        void flush(boolean all);
    }

    interface MapBase<K, V, C> extends DbCache {

        /**
         * Returns a {@link Set} of all the keys in
         * the cache.
         *
         * @return a set of all the keys in the cache
         */
        Set<K> keySet();

        /**
         * Puts a value into the cache.
         *
         * @param key the key to store the value under
         * @param value the value to store
         */
        void put(K key, V value);

        /**
         * Locks a key in the cache.
         * <p>
         * This will ensure that an entry is not removed
         * from the cache until it is unlocked.
         *
         * @param key the key to lock
         */
        void lock(K key);

        /**
         * Unlocks a key in the cache.
         * <p>
         * This will allow an entry to be removed from
         * the cache.
         *
         * @param key the key to unlock
         */
        void unlock(K key);

        /**
         * Removes an entry from the cache.
         *
         * @param key the key of the entry
         */
        void remove(K key);

        /**
         * Returns a cached value from the cache immediately.
         * <p>
         * This method should be used when the value is expected to be
         * in the cache. If the value is not in the cache, this method
         * will return null.
         *
         * @param key the key to get the value from
         * @return the value from the cache, or null if the value is not in the cache
         */
        @Nullable
        C getCached(K key);

        /**
         * Returns a value from the cache or loads it if it is not in the cache.
         * <p>
         * This method should be used when the value is not guaranteed to be
         * in the cache. If the value is in the cache, this method will
         * return the value immediately. If the value is not in the cache,
         * this method will load the value from the database and return it.
         *
         * @param key the key to get the value from
         * @param loader the loader to load the value from the database
         * @return the value from the cache or the value loaded from the database
         */
        CompletableFuture<C> getOrLoad(K key, CompletableFuture<C> loader);

        /**
         * Bulk loads data into this cache.
         *
         * @param loader the loader to load the data from the database
         * @param keyFunction the function to get the key from the value
         * @return the loaded data
         */
        CompletableFuture<? extends Collection<C>> loadBulk(CompletableFuture<? extends Collection<C>> loader, Function<C, K> keyFunction);

        /**
         * Saves the cache to the database.
         * <p>
         * The consumer will be called for each value in the cache that
         * has been modified. This is useful for saving data that has been
         * modified in the cache.
         *
         * @param key the key to save the value under
         * @param value the value to save
         */
        void save(K key, Consumer<V> value);

        /**
         * Flushes the cache.
         * <p>
         * This method will remove the key from the cache and save the
         * values to the database. This is useful for saving data that
         * has been modified in the cache.
         * <p>
         * NOTE: This should be used in conjunction with the
         * {@link #save(Object, Consumer)} method as flushing with
         * unsaved entries will not damageCause the objects to be flushed
         * from memory.
         *
         * @param key the key to flush
         * @param all whether to flush all entries
         */
        void flush(K key, boolean all);
    }

    interface MapCache<K, V> extends MapBase<K, V, V> {
    }

    interface MultimapCache<K, V> extends MapBase<K, V, List<V>> {

        @NotNull
        @Override
        List<V> getCached(K key);

        /**
         * Puts a collection of values into the cache.
         *
         * @param key the key to store the values under
         * @param values the values to store
         */
        void putAll(K key, Collection<V> values);
    }
}

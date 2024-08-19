package org.battleplugins.tracker.sql;

import org.battleplugins.tracker.BattleTracker;

class DbValue<V> {
    final V value;
    boolean dirty;
    boolean locked;
    long lastAccess = System.currentTimeMillis();

    public DbValue(V value, boolean dirty) {
        this.value = value;
        this.dirty = dirty;
    }

    public void lock() {
        this.locked = true;
    }

    public void unlock() {
        this.locked = false;
    }

    public void resetLastAccess() {
        this.lastAccess = System.currentTimeMillis();
    }

    public boolean shouldFlush() {
        long staleEntryTime = BattleTracker.getInstance().getMainConfig().getAdvanced().staleEntryTime() * 1000L;
        return !this.dirty && System.currentTimeMillis() - this.lastAccess > staleEntryTime;
    }
}
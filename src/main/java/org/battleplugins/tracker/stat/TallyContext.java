package org.battleplugins.tracker.stat;

public interface TallyContext {

    void recordStat(StatType statType, float value);
}

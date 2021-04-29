package com.aiyi.game.dnfserver.utils.cache;

public class CacheItem {

    private Runnable collback;

    private Object value;

    private long expTime;

    public Runnable getCollback() {
        return collback;
    }

    public void setCollback(Runnable collback) {
        this.collback = collback;
    }

    public<T> T getValue(Class<T> clazz) {
        return (T)value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public long getExpTime() {
        return expTime;
    }

    public void setExpTime(long expTime) {
        this.expTime = expTime;
    }
}

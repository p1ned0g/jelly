package dev.jelly.storage;

public record PageId(int value) {

    public long toOffset(int pageSize) {
        return (long) value * pageSize;
    }

    public boolean isInvalid() {
        return value == -1;
    }
}
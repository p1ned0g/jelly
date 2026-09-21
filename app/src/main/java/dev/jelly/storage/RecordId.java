package dev.jelly.storage;

public record RecordId(
    PageId pageId,
    int slotId
) {
}
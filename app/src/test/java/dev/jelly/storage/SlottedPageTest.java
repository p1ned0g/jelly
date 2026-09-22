package dev.jelly.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import dev.jelly.common.PageConstants;

class SlottedPageTest {

    private SlottedPage newPage() {
        Buffer buffer = new Buffer(PageConstants.PAGE_SIZE);
        return new SlottedPage(buffer);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    @Test
    void insertAndGetRecord() {
        SlottedPage page = newPage();

        int slotId = page.insertRecord(bytes("Alice"));

        assertEquals(0, slotId);
        assertEquals("Alice", text(page.getRecord(slotId)));
        assertEquals(1, page.getSlotCount());
    }

    @Test
    void deleteRecord() {
        SlottedPage page = newPage();

        int slotId = page.insertRecord(bytes("Alice"));

        assertTrue(page.deleteRecord(slotId));
        assertNull(page.getRecord(slotId));

        // 同じレコードを再度削除するのは失敗
        assertFalse(page.deleteRecord(slotId));
    }

    @Test
    void deletedSlotIsReused() {
        SlottedPage page = newPage();

        int aliceSlot = page.insertRecord(bytes("Alice"));
        int bobSlot = page.insertRecord(bytes("Bob"));

        assertTrue(page.deleteRecord(aliceSlot));

        int charlieSlot = page.insertRecord(bytes("Charlie"));

        // 削除したslot 0を再利用
        assertEquals(aliceSlot, charlieSlot);

        assertEquals("Charlie", text(page.getRecord(charlieSlot)));
        assertEquals("Bob", text(page.getRecord(bobSlot)));

        // 新しいスロットは増えていない
        assertEquals(2, page.getSlotCount());
    }

    @Test
    void compactPreservesSlotIdsAndRecords() {
        SlottedPage page = newPage();

        int aliceSlot = page.insertRecord(bytes("Alice"));
        int bobSlot = page.insertRecord(bytes("Bob"));
        int charlieSlot = page.insertRecord(bytes("Charlie"));

        page.deleteRecord(bobSlot);

        page.compact();

        assertEquals("Alice", text(page.getRecord(aliceSlot)));
        assertNull(page.getRecord(bobSlot));
        assertEquals("Charlie", text(page.getRecord(charlieSlot)));
    }

    @Test
    void emptyRecordIsRejected() {
        SlottedPage page = newPage();

        assertThrows(
            IllegalArgumentException.class,
            () -> page.insertRecord(new byte[0])
        );
    }

    @Test
    void invalidSlotIdReturnsNull() {
        SlottedPage page = newPage();

        assertNull(page.getRecord(-1));
        assertNull(page.getRecord(0));
    }
}
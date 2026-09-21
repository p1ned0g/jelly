package dev.jelly.common;

import java.nio.charset.StandardCharsets;

import dev.jelly.storage.SlottedPage;

public class Utils {
    private Utils() {
    }

    public static byte[] encode(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String decode(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    public static void printRecord(SlottedPage page, int slotId, String label) {
        byte[] record = page.getRecord(slotId);

        System.out.printf(
            "%s (slot=%d): %s%n",
            label,
            slotId,
            record == null ? "<deleted>" : decode(record)
        );
    }
}

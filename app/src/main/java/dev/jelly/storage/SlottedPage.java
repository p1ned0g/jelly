package dev.jelly.storage;

import java.util.Objects;

public final class SlottedPage {

    private static final int HEADER_SIZE = 8;
    private static final int SLOT_SIZE = 8;
    private static final int DATA_START_OFFSET = 4;

    private final Buffer buffer;
    private final int pageSize;

    public SlottedPage(Buffer buffer) {
        this.buffer = Objects.requireNonNull(buffer);
        this.pageSize = buffer.getPageSize();

        if (pageSize < HEADER_SIZE) {
            throw new IllegalArgumentException("Page is too small");
        }

        // 新規ページ（全バイト0）の初期化
        if (readInt(0) == 0 && readInt(4) == 0) {
            writeInt(0, 0); // slotCount
            writeInt(4, pageSize); // dataStart
        }

        validateHeader();
        validateSlots();
    }

    /**
     * レコードを挿入し、slot IDを返す。
     * 空き領域が足りない場合は -1。
     */
    public int insertRecord(byte[] record) {
        Objects.requireNonNull(record, "record");

        if (record.length == 0) {
            throw new IllegalArgumentException(
                "Empty records are not supported"
            );
        }

        compact();

        int slotCount = getSlotCount();
        int reusableSlot = findDeletedSlot();

        int slotId = reusableSlot >= 0
            ? reusableSlot
            : slotCount;

        int requiredDirectoryEnd = HEADER_SIZE + (slotId + 1) * SLOT_SIZE;

        int dataStart = getDataStart();

        if (dataStart - record.length < requiredDirectoryEnd) {
            return -1;
        }

        int newOffset = dataStart - record.length;

        for (int i = 0; i < record.length; i++) {
            buffer.writeByte(newOffset + i, record[i]);
        }

        writeSlot(slotId, newOffset, record.length);

        if (reusableSlot < 0) {
            writeInt(0, slotCount + 1);
        }

        writeInt(4, newOffset);

        return slotId;
    }

    /**
     * slot IDからレコードを取得する。
     * 削除済み・不正なslot IDなら null。
     */
    public byte[] getRecord(int slotId) {
        if (!isValidSlotId(slotId)) {
            return null;
        }

        int offset = readSlotOffset(slotId);
        int length = readSlotLength(slotId);
        // System.out.println("----- Slot -------");
        // System.out.println("slotId : " + slotId);
        // System.out.println("Offset : " + offset);
        // System.out.println("length : " + length);
        // System.out.println("----- Slot -------");

        if (length == 0) {
            return null;
        }

        byte[] result = new byte[length];

        for (int i = 0; i < length; i++) {
            result[i] = buffer.readByte(offset + i);
        }

        return result;
    }

    /**
     * レコードを削除する。
     * 削除に成功したら true。
     */
    public boolean deleteRecord(int slotId) {
        if (!isValidSlotId(slotId)) {
            return false;
        }

        if (readSlotLength(slotId) == 0) {
            return false;
        }

        // slot IDは維持し、レコードを無効化する
        writeSlot(slotId, 0, 0);

        return true;
    }

    /**
     * ディレクトリ領域を含めた空き容量。
     */
    public int getFreeSpace() {
        return getDataStart() - (HEADER_SIZE + getSlotCount() * SLOT_SIZE);
    }

    public int getSlotCount() {
        return readInt(0);
    }
        

    private int getDataStart() {
        return readInt(DATA_START_OFFSET);
    }

    /**
     * 削除済みレコードを除き、
     * レコード本体をページ末尾側に詰め直す。
     * slot IDは変えない。
     */
    public void compact() {
        int slotCount = getSlotCount();

        // 先に全レコードを退避する
        byte[][] records = new byte[slotCount][];

        for (int slotId = 0; slotId < slotCount; slotId++) {
            records[slotId] = getRecord(slotId);
        }

        int cursor = pageSize;

        // 退避したデータを末尾側から配置し直す
        for (int slotId = 0; slotId < slotCount; slotId++) {
            byte[] record = records[slotId];

            if (record == null) {
                continue;
            }

            cursor -= record.length;

            for (int i = 0; i < record.length; i++) {
                buffer.writeByte(cursor + i, record[i]);
            }

            writeSlot(slotId, cursor, record.length);
        }

        writeInt(4, cursor);
    }

    private int findDeletedSlot() {
        for (int i = 0; i < getSlotCount(); i++) {
            if (readSlotLength(i) == 0) {
                return i;
            }
        }

        return -1;
    }

    private boolean isValidSlotId(int slotId) {
        return slotId >= 0 && slotId < getSlotCount();
    }

    private int slotPosition(int slotId) {
        return HEADER_SIZE + slotId * SLOT_SIZE;
    }

    private int readSlotOffset(int slotId) {
        return readInt(slotPosition(slotId));
    }

    private int readSlotLength(int slotId) {
        return readInt(slotPosition(slotId) + 4);
    }

    private void writeSlot(int slotId, int offset, int length) {
        int position = slotPosition(slotId);
        writeInt(position, offset);
        writeInt(position + 4, length);
    }

    private int readInt(int offset) {
        return ((buffer.readByte(offset) & 0xFF) << 24)
            | ((buffer.readByte(offset + 1) & 0xFF) << 16)
            | ((buffer.readByte(offset + 2) & 0xFF) << 8)
            | (buffer.readByte(offset + 3) & 0xFF);
    }

    private void writeInt(int offset, int value) {
        buffer.writeByte(offset, (byte) (value >>> 24));
        buffer.writeByte(offset + 1, (byte) (value >>> 16));
        buffer.writeByte(offset + 2, (byte) (value >>> 8));
        buffer.writeByte(offset + 3, (byte) value);
    }

    private void validateHeader() {
        int slotCount = getSlotCount();
        int dataStart = getDataStart();

        long directoryEnd = HEADER_SIZE + (long) slotCount * SLOT_SIZE;

        if (slotCount < 0
                || directoryEnd > pageSize
                || dataStart < directoryEnd
                || dataStart > pageSize) {
            throw new IllegalStateException(
                "Invalid SlottedPage header"
            );
        }
    }

    private void validateSlots() {
        int slotCount = getSlotCount();
        int dataStart = getDataStart();

        int directoryEnd = HEADER_SIZE + slotCount * SLOT_SIZE;

        for (int slotId = 0; slotId < slotCount; slotId++) {
            int offset = readSlotOffset(slotId);
            int length = readSlotLength(slotId);

            // 削除済みスロット
            if (length == 0) {
                if (offset != 0) {
                    throw new IllegalStateException(
                        "Invalid deleted slot: " + slotId
                    );
                }
                continue;
            }

            // 負の長さは不正
            if (length < 0) {
                throw new IllegalStateException(
                    "Negative record length: " + slotId
                );
            }

            // レコードがディレクトリ領域に食い込んでいないか
            if (offset < directoryEnd) {
                throw new IllegalStateException(
                    "Record overlaps slot directory: " + slotId
                );
            }

            // レコードがデータ領域の外にないか
            if (offset < dataStart || (long) offset + length > pageSize) {
                throw new IllegalStateException(
                    "Record is outside data area: " + slotId
                );
            }
        }
    }
}
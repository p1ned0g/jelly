package dev.jelly.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeapFileTest {

    private Path testFile;
    private DiskManager diskManager;
    private BufferPoolManager bufferPoolManager;
    private HeapFile heapFile;

    @BeforeEach
    void setUp() throws IOException {
        // テストごとに別のDBファイルを作成する
        testFile = Files.createTempFile("heapfile-test-", ".db");

        diskManager = new DiskManager(testFile.toString());
        bufferPoolManager = new BufferPoolManager(diskManager);
        heapFile = new HeapFile(bufferPoolManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        // バッファの内容をディスクへ書き込む
        if (heapFile != null) {
            heapFile.flush();
        }

        // DiskManagerにclose()がある場合は、
        // ファイル削除前にcloseする
        if (diskManager != null) {
            diskManager.close();
        }

        Files.deleteIfExists(testFile);
    }

    @Test
    void insertAndGetRecord() throws IOException {
        byte[] data = "Hello, Jelly!".getBytes();

        RecordId recordId = heapFile.insertRecord(data);

        byte[] actual = heapFile.getRecord(recordId);

        assertArrayEquals(data, actual);
    }

    @Test
    void insertMultipleRecords() throws IOException {
        byte[] first = "first".getBytes();
        byte[] second = "second".getBytes();

        RecordId firstId = heapFile.insertRecord(first);
        RecordId secondId = heapFile.insertRecord(second);

        assertArrayEquals(first, heapFile.getRecord(firstId));
        assertArrayEquals(second, heapFile.getRecord(secondId));
    }

    @Test
    void deleteRecord() throws IOException {
        byte[] data = "delete me".getBytes();

        RecordId recordId = heapFile.insertRecord(data);

        heapFile.deleteRecord(recordId);

        assertNull(heapFile.getRecord(recordId));
    }

    @Test 
    void scanPage() throws IOException {
        byte[] data1 = "Scan me first".getBytes();
        byte[] data2 = "Scan me second".getBytes();
        byte[] data3 = "Scan me third".getBytes();

        heapFile.insertRecord(data1);
        heapFile.insertRecord(data2);
        heapFile.insertRecord(data3);

        List<byte[]> actualList = heapFile.scan();

        assertArrayEquals(data1, actualList.get(0));
        assertArrayEquals(data2, actualList.get(1));
        assertArrayEquals(data3, actualList.get(2));

    }
    @Test
    void insertAndGetTuple() throws IOException {
        Tuple original = new Tuple(1, "Alice", 20);

        byte[] data = original.toBytes();

        RecordId recordId = heapFile.insertRecord(data);

        byte[] readData = heapFile.getRecord(recordId);

        Tuple restored = Tuple.fromBytes(readData);

        assertEquals(3, restored.getColumnCount());
        assertEquals(1, restored.getValue(0));
        assertEquals("Alice", restored.getValue(1));
        assertEquals(20, restored.getValue(2));
    }

    @Test
    void scanTuples() throws IOException {
        Tuple first = new Tuple(1, "Alice", 20);
        Tuple second = new Tuple(2, "Bob", 25);
        Tuple third = new Tuple(3, "Charlie", 30);

        heapFile.insertRecord(first.toBytes());
        heapFile.insertRecord(second.toBytes());
        heapFile.insertRecord(third.toBytes());

        List<byte[]> records = heapFile.scan();

        assertEquals(3, records.size());

        Tuple actualFirst = Tuple.fromBytes(records.get(0));
        Tuple actualSecond = Tuple.fromBytes(records.get(1));
        Tuple actualThird = Tuple.fromBytes(records.get(2));

        assertEquals(1, actualFirst.getValue(0));
        assertEquals("Alice", actualFirst.getValue(1));
        assertEquals(20, actualFirst.getValue(2));

        assertEquals(2, actualSecond.getValue(0));
        assertEquals("Bob", actualSecond.getValue(1));
        assertEquals(25, actualSecond.getValue(2));

        assertEquals(3, actualThird.getValue(0));
        assertEquals("Charlie", actualThird.getValue(1));
        assertEquals(30, actualThird.getValue(2));
    }
}
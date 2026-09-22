package dev.jelly.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jelly.storage.Buffer;
import dev.jelly.storage.BufferPoolManager;
import dev.jelly.common.PageConstants;
import dev.jelly.storage.DiskManager;
import dev.jelly.storage.PageId;
import dev.jelly.storage.HeapFile;
import dev.jelly.storage.RecordId;
import dev.jelly.storage.Tuple;

public class StorageIntegrationTest {

    @TempDir
    Path tempDir;

    private DiskManager disk;
    private BufferPoolManager bpm;
    private HeapFile heapFile;
    private Path dbPath;

    @BeforeEach
    void setup() throws IOException {
        dbPath = tempDir.resolve("storage-integration-test.db");

        disk = new DiskManager(dbPath.toString());

        // Poolサイズを1にして、退避を確実に発生させる
        bpm = new BufferPoolManager(
                disk,
                1,
                PageConstants.PAGE_SIZE
        );

        heapFile = new HeapFile(bpm);
    }

    /**
     * 1. newPage() で新しいページを作る
     * 2. そのページに書き込む
     * 3. unpin() する
     * 4. fetchPage() でキャッシュヒットする
     * 5. flushAll() で Disk に書き戻す
     * 6. 2ページ目を作り、1ページ目を退避させる
     * 7. 1ページ目を再fetchして、データを確認する
     * 8. DiskManagerを開き直して、永続化を確認する
     */
    @Test
    void testStorageIntegration() throws IOException {

        // 1. newPage()で新しいページを作成
        Buffer buf = bpm.newPage();
        PageId pid = buf.getPageId();

        // 2. ページに書き込み（dirty = true）
        buf.writeByte(0, (byte) 123);

        // 3. unpin()して、退避可能な状態にする
        buf.unpin();

        // 4. fetchPage() → キャッシュヒット
        Buffer buf2 = bpm.fetchPage(pid);

        // 同じBufferインスタンスが返ることを確認
        assertSame(buf, buf2);
        assertEquals((byte) 123, buf2.readByte(0));

        buf2.unpin();

        // 5. flushAll() → dirtyページをDiskに書き戻す
        bpm.flushAll();

        // 6. 2ページ目を作成して、Poolサイズ1で1ページ目を退避させる
        Buffer buf3 = bpm.newPage();
        PageId pid2 = buf3.getPageId();

        assertNotEquals(pid, pid2);

        buf3.writeByte(0, (byte) 45);
        buf3.unpin();

        // 7. 1ページ目を再fetch → Diskから読み込まれる
        Buffer buf4 = bpm.fetchPage(pid);

        assertEquals((byte) 123, buf4.readByte(0));

        buf4.unpin();

        bpm.flushAll();

        // 8. DiskManagerを閉じて開き直し、Disk上のデータを直接確認
        disk.close();

        try (DiskManager reopenedDisk = new DiskManager(dbPath.toString())) {
            ByteBuffer readBuffer = ByteBuffer.allocate(PageConstants.PAGE_SIZE);

            reopenedDisk.read(pid, readBuffer);
            readBuffer.flip();

            assertEquals((byte) 123, readBuffer.get());
        }
    }

    /**
     * 1. Tupleを作成する
     * 2. Tupleをbyte[]に変換する
     * 3. HeapFile経由でレコードを保存する
     * 4. RecordIdを使ってレコードを取得する
     * 5. byte[]からTupleを復元する
     * 6. 復元したTupleの各値を確認する
     */
    @Test
    void testHeapFileTupleIntegration() throws IOException {

        // 1. Tupleを作成
        Tuple original = new Tuple(1, "Alice", 20);

        // 2. Tupleをbyte[]に変換
        byte[] data = original.toBytes();

        // 3. HeapFile経由で保存
        RecordId rid = heapFile.insertRecord(data);

        // 4. RecordIdを使って取得
        byte[] readData = heapFile.getRecord(rid);

        // 5. byte[]からTupleを復元
        Tuple restored = Tuple.fromBytes(readData);

        // 6. 復元したTupleの値を確認
        assertEquals(3, restored.getColumnCount());
        assertEquals(1, restored.getValue(0));
        assertEquals("Alice", restored.getValue(1));
        assertEquals(20, restored.getValue(2));
    }

    /**
     * 1. 複数のTupleを作成する
     * 2. HeapFileにそれぞれ保存する
     * 3. 保存時に取得したRecordIdで読み戻す
     * 4. 各Tupleの値が正しいことを確認する
     */
    @Test
    void testMultipleTupleIntegration() throws IOException {

        // 1. 複数のTupleを作成
        Tuple tuple1 = new Tuple(1, "Alice", 20);
        Tuple tuple2 = new Tuple(2, "Bob", 25);
        Tuple tuple3 = new Tuple(3, "Charlie", 30);

        // 2. HeapFileに保存
        RecordId rid1 = heapFile.insertRecord(tuple1.toBytes());
        RecordId rid2 = heapFile.insertRecord(tuple2.toBytes());
        RecordId rid3 = heapFile.insertRecord(tuple3.toBytes());

        // 3. RecordIdで読み戻す
        Tuple actual1 = Tuple.fromBytes(heapFile.getRecord(rid1));
        Tuple actual2 = Tuple.fromBytes(heapFile.getRecord(rid2));
        Tuple actual3 = Tuple.fromBytes(heapFile.getRecord(rid3));

        // 4. 各Tupleの値を確認
        assertEquals(1, actual1.getValue(0));
        assertEquals("Alice", actual1.getValue(1));
        assertEquals(20, actual1.getValue(2));

        assertEquals(2, actual2.getValue(0));
        assertEquals("Bob", actual2.getValue(1));
        assertEquals(25, actual2.getValue(2));

        assertEquals(3, actual3.getValue(0));
        assertEquals("Charlie", actual3.getValue(1));
        assertEquals(30, actual3.getValue(2));
    }

    /**
     * 1. Tupleを2件保存する
     * 2. 1件目を削除する
     * 3. 削除したレコードが取得できないことを確認する
     * 4. 削除していないレコードが取得できることを確認する
     */
    @Test
    void testDeleteTupleIntegration() throws IOException {

        // 1. Tupleを2件保存
        Tuple tuple1 = new Tuple(1, "Alice", 20);
        Tuple tuple2 = new Tuple(2, "Bob", 25);

        RecordId rid1 = heapFile.insertRecord(tuple1.toBytes());
        RecordId rid2 = heapFile.insertRecord(tuple2.toBytes());

        // 2. 1件目を削除
        heapFile.deleteRecord(rid1);

        // 3. 削除したレコードが取得できないことを確認
        assertNull(heapFile.getRecord(rid1));

        // 4. 削除していないレコードは取得できる
        Tuple actual2 = Tuple.fromBytes(heapFile.getRecord(rid2));

        assertEquals(2, actual2.getValue(0));
        assertEquals("Bob", actual2.getValue(1));
        assertEquals(25, actual2.getValue(2));
    }

    /**
     * 1. 複数のTupleをHeapFileに保存する
     * 2. scan()ですべてのレコードを取得する
     * 3. 取得件数を確認する
     * 4. 各レコードをTupleに復元して内容を確認する
     */
    @Test
    void testScanTupleIntegration() throws IOException {

        // 1. 複数のTupleを保存
        Tuple tuple1 = new Tuple(1, "Alice", 20);
        Tuple tuple2 = new Tuple(2, "Bob", 25);
        Tuple tuple3 = new Tuple(3, "Charlie", 30);

        heapFile.insertRecord(tuple1.toBytes());
        heapFile.insertRecord(tuple2.toBytes());
        heapFile.insertRecord(tuple3.toBytes());

        // 2. scan()ですべてのレコードを取得
        var records = heapFile.scan();

        // 3. 取得件数を確認
        assertEquals(3, records.size());

        // 4. Tupleに復元して内容を確認
        Tuple actual1 = Tuple.fromBytes(records.get(0));
        Tuple actual2 = Tuple.fromBytes(records.get(1));
        Tuple actual3 = Tuple.fromBytes(records.get(2));

        assertEquals("Alice", actual1.getValue(1));
        assertEquals("Bob", actual2.getValue(1));
        assertEquals("Charlie", actual3.getValue(1));
    }

    /**
     * 1. TupleをHeapFileに保存する
     * 2. BufferPoolManagerをflushする
     * 3. DiskManagerを閉じる
     * 4. DiskManagerとBufferPoolManagerを開き直す
     * 5. 保存したTupleを再取得して内容を確認する
     *
     * ※ HeapFileの再オープン時に既存ページを認識できる実装が必要
     */
    @Test
    void testTuplePersistenceIntegration() throws IOException {

        // 1. TupleをHeapFileに保存
        Tuple original = new Tuple(1, "Alice", 20);
        RecordId rid = heapFile.insertRecord(original.toBytes());

        // 2. Diskへ書き戻す
        bpm.flushAll();

        // 3. DiskManagerを閉じる
        disk.close();

        // 4. DiskManagerとBufferPoolManagerを開き直す
        try (DiskManager reopenedDisk = new DiskManager(dbPath.toString())) {

            BufferPoolManager reopenedBpm = new BufferPoolManager(reopenedDisk, 1, PageConstants.PAGE_SIZE);

            HeapFile reopenedHeapFile = new HeapFile(reopenedBpm);

            // 5. 保存したTupleを再取得して内容を確認
            Tuple restored = Tuple.fromBytes(reopenedHeapFile.getRecord(rid));

            assertEquals(1, restored.getValue(0));
            assertEquals("Alice", restored.getValue(1));
            assertEquals(20, restored.getValue(2));

            reopenedBpm.flushAll();
        }
    }
    
    /**
     * 1. 複数のTupleを作成する
     * 2. HeapFileに保存し、ページをまたぐ状態にする
     * 3. 保存したRecordIdを使って全レコードを取得する
     * 4. 取得したTupleの内容を確認する
     * 5. scan()で全レコードを取得できることを確認する
     */
    @Test
    void testMultiplePagesIntegration() throws IOException {

        // 1. 複数のTupleを作成・保存する
        List<RecordId> recordIds = new ArrayList<>();
        int tupleCount = 100;
        String largeValue = "A".repeat(1000);

        for (int i = 0; i < tupleCount; i++) {
            Tuple tuple = new Tuple(i, largeValue + i, 20 + i);
            RecordId rid = heapFile.insertRecord(tuple.toBytes());
            recordIds.add(rid);
        }

        // 2. 複数ページにまたがっていることを確認
        long distinctPageCount = recordIds.stream().map(rid -> rid.pageId()).distinct().count();
        System.out.println("distinctPageCount = " + distinctPageCount);
        System.out.println("recordIds = " + recordIds);
        assertTrue(distinctPageCount > 1);

        // 3・4. RecordIdから取得してTupleの内容を確認
        for (int i = 0; i < tupleCount; i++) {
            byte[] data = heapFile.getRecord(recordIds.get(i));
            Tuple actual = Tuple.fromBytes(data);

            assertEquals(i, actual.getValue(0));
            assertEquals(largeValue + i, actual.getValue(1));
            assertEquals(20 + i, actual.getValue(2));
        }

        // 5. scan()ですべて取得できることを確認
        List<byte[]> records = heapFile.scan();

        assertEquals(tupleCount, records.size());
    }
}
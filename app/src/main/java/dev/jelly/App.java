package dev.jelly;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import dev.jelly.common.Utils;
import dev.jelly.storage.Buffer;
import dev.jelly.storage.BufferPoolManager;
import dev.jelly.storage.DiskManager;
import dev.jelly.storage.PageId;
import dev.jelly.storage.SlottedPage;
import dev.jelly.storage.Tuple;

public class App {

    public static void main(String[] args) {
        try (DiskManager disk = new DiskManager("jelly.db")) {

            BufferPoolManager bpm = new BufferPoolManager(disk);

            System.out.println("=== 1. Create new page ===");

            Buffer buffer = bpm.newPage();
            PageId pid = buffer.getPageId();

            System.out.println("Allocated PageId: " + pid);

            SlottedPage page = new SlottedPage(buffer);

            int bobSlot = page.insertRecord(Utils.encode("Bob"));
            int aliceSlot = page.insertRecord(Utils.encode("Alice"));
            int charlieSlot = page.insertRecord(Utils.encode("Charlie"));
            int alexanderSlot = page.insertRecord(Utils.encode("Alexander"));

            System.out.println("Inserted records:");
            Utils.printRecord(page, bobSlot, "Bob");
            Utils.printRecord(page, aliceSlot, "Alice");
            Utils.printRecord(page, charlieSlot, "Charlie");
            Utils.printRecord(page, alexanderSlot, "Alexander");

            System.out.println("Slot count: " + page.getSlotCount());
            System.out.println("Free space: " + page.getFreeSpace());

            buffer.unpin();

            System.out.println("\n=== 2. Fetch page ===");

            Buffer fetched = bpm.fetchPage(pid);
            SlottedPage fetchedPage = new SlottedPage(fetched);

            Utils.printRecord(fetchedPage, bobSlot, "Read");
            Utils.printRecord(fetchedPage, aliceSlot, "Read");
            Utils.printRecord(fetchedPage, charlieSlot, "Read");
            Utils.printRecord(fetchedPage, alexanderSlot, "Read");

            fetched.unpin();

            System.out.println("\n=== 3. Delete Alice ===");

            Buffer deleteBuffer = bpm.fetchPage(pid);
            SlottedPage deletePage = new SlottedPage(deleteBuffer);

            System.out.println(
                "Deleted: " + deletePage.deleteRecord(aliceSlot)
            );

            Utils.printRecord(deletePage, aliceSlot, "Alice");

            System.out.println("Free space: " + deletePage.getFreeSpace());

            deleteBuffer.unpin();

            System.out.println("\n=== 4. Reuse deleted slot ===");

            Buffer reuseBuffer = bpm.fetchPage(pid);
            SlottedPage reusePage = new SlottedPage(reuseBuffer);

            // Aliceと同じ5バイトの名前
            int davidSlot = reusePage.insertRecord(Utils.encode("David"));

            System.out.println("David slot: " + davidSlot);
            System.out.println("Alice slot: " + aliceSlot);
            System.out.println(
                "Reused Alice slot: " + (davidSlot == aliceSlot)
            );

            Utils.printRecord(reusePage, davidSlot, "David");

            reuseBuffer.unpin();

            System.out.println("\n=== 5. Insert longer record ===");

            Buffer longBuffer = bpm.fetchPage(pid);
            SlottedPage longPage = new SlottedPage(longBuffer);

            // さらに長い可変長レコード
            String longName = "Christopher";
            int longSlot = longPage.insertRecord(Utils.encode(longName));

            if (longSlot >= 0) {
                Utils.printRecord(longPage, longSlot, "Long name");
            } else {
                System.out.println("Not enough free space");
            }

            System.out.println("Slot count: " + longPage.getSlotCount());
            System.out.println("Free space: " + longPage.getFreeSpace());

            longBuffer.unpin();

            System.out.println("\n=== 6. Compact ===");

            Buffer compactBuffer = bpm.fetchPage(pid);
            SlottedPage compactPage = new SlottedPage(compactBuffer);

            compactPage.compact();

            System.out.println("Compaction completed");

            Utils.printRecord(compactPage, bobSlot, "Bob");
            Utils.printRecord(compactPage, davidSlot, "David");
            Utils.printRecord(compactPage, charlieSlot, "Charlie");
            Utils.printRecord(compactPage, alexanderSlot, "Alexander");

            if (longSlot >= 0) {
                Utils.printRecord(compactPage, longSlot, "Long name");
            }

            System.out.println("Free space: " + compactPage.getFreeSpace());

            compactBuffer.unpin();

            System.out.println("\n=== 7. Test insufficient space ===");

            Buffer testBuffer = bpm.fetchPage(pid);
            SlottedPage testPage = new SlottedPage(testBuffer);

            // 挿入前の状態を保存
            int freeSpaceBefore = testPage.getFreeSpace();
            int slotCountBefore = testPage.getSlotCount();

            byte[] bobBefore = testPage.getRecord(bobSlot);
            byte[] davidBefore = testPage.getRecord(davidSlot);

            // 空き容量より1バイト大きいレコードを作る
            byte[] hugeRecord = new byte[freeSpaceBefore + 1];

            System.out.println("Free space before: " + freeSpaceBefore);
            System.out.println("Trying to insert: " + hugeRecord.length + " bytes");

            // 挿入を試す
            int result = testPage.insertRecord(hugeRecord);

            System.out.println("Insert result: " + result);
            System.out.println("Expected result: -1");

            // 既存レコードが壊れていないか確認
            System.out.println(
                "Bob unchanged: "
                    + java.util.Arrays.equals(
                        bobBefore,
                        testPage.getRecord(bobSlot)
                    )
            );

            System.out.println(
                "David unchanged: "
                    + java.util.Arrays.equals(
                        davidBefore,
                        testPage.getRecord(davidSlot)
                    )
            );

            // スロット数と空き容量も確認
            System.out.println(
                "Slot count unchanged: "
                    + (slotCountBefore == testPage.getSlotCount())
            );

            System.out.println(
                "Free space unchanged: "
                    + (freeSpaceBefore == testPage.getFreeSpace())
            );

            testBuffer.unpin();

            System.out.println("\n=== 8. Tuple serialization ===");

            // Tupleを作成
            Tuple tuple = new Tuple(1, "Alice", 20);

            System.out.println("Original Tuple:");
            System.out.println(tuple.getValue(0));
            System.out.println(tuple.getValue(1));
            System.out.println(tuple.getValue(2));

            // byte[]に変換
            byte[] tupleBytes = tuple.toBytes();

            System.out.println("Serialized size: " + tupleBytes.length);

            // byte[]から復元
            Tuple restored = Tuple.fromBytes(tupleBytes);

            System.out.println("Restored Tuple:");
            System.out.println(restored.getValue(0));
            System.out.println(restored.getValue(1));
            System.out.println(restored.getValue(2));

            
            System.out.println("\n=== 9. Flush ===");

            bpm.flushAll();

            System.out.println("flushAll completed");
            System.out.println("All checks completed.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

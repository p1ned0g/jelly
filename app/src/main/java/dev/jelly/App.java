package dev.jelly;

import java.io.IOException;
import java.nio.ByteBuffer;

import dev.jelly.buffer.Buffer;
import dev.jelly.buffer.BufferPoolManager;
import dev.jelly.disk.DiskManager;
import dev.jelly.disk.PageId;

public class App {
    public static void main(String[] args) {
        try (DiskManager disk = new DiskManager("mydb.db");){

            BufferPoolManager bpm = new BufferPoolManager(disk);
            // 新しいページを作る
            Buffer buf1 = bpm.newPage();
            PageId pid1 = buf1.getPageId();
            System.out.println("Allocated PageId: " + pid1.getPageId());

            // ページに書き込む
            buf1.writeByte(0, (byte) 'H');
            buf1.writeByte(1, (byte) 'i');
            buf1.unpin();

            // fetchPageで読み込む
            Buffer buf2 = bpm.fetchPage(pid1);
            byte b0 = buf2.readByte(0);
            byte b1 = buf2.readByte(1);
            System.out.println("Read from cache: " + (char)b0 + (char)b1);
            buf2.unpin();

            // flushAllでディスクに書き戻す
            bpm.flushAll();

            // ディスクから直接読み込んで確認
            Buffer buf3 = bpm.fetchPage(pid1);
            byte c0 = buf3.readByte(0);
            byte c1 = buf3.readByte(1);
            System.out.println("Read after flush: " + (char)c0 + (char)c1);
            buf3.unpin();
            
            // PageId pageId = disk.allocate();
            // ByteBuffer writeBuf = ByteBuffer.allocate(4096);
            // writeBuf.put("I'm p1ned0g".getBytes());
            // writeBuf.flip();

            // disk.write(pageId, writeBuf);

            // ByteBuffer readBuf = ByteBuffer.allocate(4096);
            // disk.read(pageId, readBuf);
            // readBuf.flip();

            // byte[] data = new byte[readBuf.remaining()];
            // readBuf.get(data);
            // System.out.println("Read from page: " + pageId.getPageId() + " : " + new String(data));
            
            // PageId pageId2 = disk.allocate();
            // ByteBuffer writeBuf2 = ByteBuffer.allocate(4096);
            // writeBuf2.put("I'm good".getBytes());
            // writeBuf2.flip();
            
            // disk.write(pageId2, writeBuf2);
            
            // ByteBuffer readBuf2 = ByteBuffer.allocate(4096);
            // disk.read(pageId2, readBuf2);
            // readBuf2.flip();
            
            // byte[] data2 = new byte[readBuf2.remaining()];
            // readBuf2.get(data2);
            // System.out.println("Read from page: " + pageId2.getPageId() + " : " + new String(data2));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

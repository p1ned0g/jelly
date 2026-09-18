package dev.jelly;

import java.io.IOException;
import java.nio.ByteBuffer;

import dev.jelly.disk.DiskManager;
import dev.jelly.disk.PageId;

public class App {
    public static void main(String[] args) {
        try (DiskManager disk = new DiskManager("mydb.db");){
            
            PageId pageId = disk.allocate();
            ByteBuffer writeBuf = ByteBuffer.allocate(4096);
            writeBuf.put("I'm p1ned0g".getBytes());
            writeBuf.flip();

            disk.write(pageId, writeBuf);

            ByteBuffer readBuf = ByteBuffer.allocate(4096);
            disk.read(pageId, readBuf);
            readBuf.flip();

            byte[] data = new byte[readBuf.remaining()];
            readBuf.get(data);
            System.out.println("Read from page: " + pageId.getPageId() + " : " + new String(data));
            
            PageId pageId2 = disk.allocate();
            ByteBuffer writeBuf2 = ByteBuffer.allocate(4096);
            writeBuf2.put("I'm good".getBytes());
            writeBuf2.flip();
            
            disk.write(pageId2, writeBuf2);
            
            ByteBuffer readBuf2 = ByteBuffer.allocate(4096);
            disk.read(pageId2, readBuf2);
            readBuf2.flip();
            
            byte[] data2 = new byte[readBuf2.remaining()];
            readBuf2.get(data2);
            System.out.println("Read from page: " + pageId2.getPageId() + " : " + new String(data2));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

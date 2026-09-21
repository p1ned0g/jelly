
package dev.jelly.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Tuple {

    private final Object[] values;

    public Tuple(Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(
                "Tuple must have at least one value"
            );
        }

        this.values = values.clone();
    }

    public Object getValue(int index) {
        checkIndex(index);
        return values[index];
    }

    public void setValue(int index, Object value) {
        checkIndex(index);
        values[index] = value;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= getColumnCount()) {
            throw new IndexOutOfBoundsException("index: " + index);
        }
    }

    public int getColumnCount() {
        return values.length;
    }


    // カラム数を保存する
    // 各値の型情報と値を保存する
    // String は UTF-8 などでバイト列に変換する
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);

        // カラム数を書き込む
        out.writeInt(getColumnCount());

        // 各カラムを順番に書き込む
        for (Object value : values) {

            if (value instanceof Integer) {
                // 型コード: Integer
                out.writeByte(1);

                // 値
                out.writeInt((Integer) value);

            } else if (value instanceof String) {
                // 型コード: String
                out.writeByte(2);

                // UTF-8に変換
                byte[] strBytes = ((String) value).getBytes(StandardCharsets.UTF_8);

                // 文字列のバイト長
                out.writeInt(strBytes.length);

                // 文字列本体
                out.write(strBytes);

            } else {
                throw new IllegalArgumentException(value == null ? "Null values are not supported" : "Unsupported value type: " + value.getClass());
            }
        }

        out.flush();

        return byteOut.toByteArray();
    }


    // toBytes() と同じ形式で読み取る
    // 不正なデータは例外にする
    public static Tuple fromBytes(byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }

        ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(byteIn);

        // カラム数を読み込む
        int columnCount = in.readInt();

        if (columnCount <= 0) {
            throw new IOException("Invalid column count: " + columnCount);
        }

        Object[] values = new Object[columnCount];

        // 各カラムを順番に読み込む
        for (int i = 0; i < columnCount; i++) {

            // 型コード
            int type = in.readUnsignedByte();

            switch (type) {
                case 1:
                    // Integer
                    values[i] = in.readInt();
                    break;

                case 2:
                    // Stringのバイト長
                    int length = in.readInt();

                    if (length < 0 || length > in.available()) {
                        throw new IOException(
                            "Invalid string length: " + length
                        );
                    }

                    byte[] strBytes = new byte[length];
                    in.readFully(strBytes);

                    values[i] = new String(strBytes, StandardCharsets.UTF_8);
                    break;

                default:
                    throw new IOException("Unknown type code: " + type);
            }
        }

        // Tupleを生成して返す
        if (in.available() != 0) {
            throw new IOException("Unexpected trailing bytes");
        }

        return new Tuple(values);
    }

}

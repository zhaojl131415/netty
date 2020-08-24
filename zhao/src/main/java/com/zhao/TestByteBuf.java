package com.zhao;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.util.Arrays;

public class TestByteBuf {
    public static void main(String[] args) {
//        ByteBuf byteBuf = Unpooled.copiedBuffer("taibai", CharsetUtil.UTF_8);
//        System.out.println(byteBuf.readerIndex());
//        System.out.println(byteBuf.writerIndex());
//        System.out.println(byteBuf.capacity());
//        System.out.println(byteBuf.maxCapacity());
//        System.out.println((char) byteBuf.getByte(5));
//        String string = byteBuf.toString(CharsetUtil.UTF_8);
//        System.out.println(string);
//        System.out.println(System.getProperty("line.separator")+"---");

        // 1.创建一个非池化的ByteBuf，大小为10个字节
        ByteBuf buf = Unpooled.buffer(10);
        System.out.println("原始ByteBuf为====================>" + buf.toString());
        System.out.println("1.ByteBuf中的内容为===============>" + Arrays.toString(buf.array()) + "\n");

        // 2.往ByteBuf写入一段内容
        byte[] bytes = {1, 2, 3, 4, 5};
        buf.writeBytes(bytes);
        System.out.println("写入的bytes为====================>" + Arrays.toString(bytes));
        System.out.println("写入一段内容后ByteBuf为===========>" + buf.toString());
        System.out.println("2.ByteBuf中的内容为===============>" + Arrays.toString(buf.array()) + "\n");

        // 3.读取一段内容
        byte b1 = buf.readByte();
        byte b2 = buf.readByte();
        byte b3 = buf.readByte();
        System.out.println("读取的bytes为====================>" + Arrays.toString(new byte[]{b1, b2, b3}));
        System.out.println("读取一段内容后ByteBuf为===========>" + buf.toString());
        // 读完之后，只是移动了readerIndex的位置，并不会丢弃已经被读过的数据
        System.out.println("3.ByteBuf中的内容为===============>" + Arrays.toString(buf.array()) + "\n");

        /**
         * 4.将读取的内容丢弃
         * 举例：
         * 例1、已读区间数据 [1, 2]，已写未读区间数据[3, 4, 5]，未写区间数据[0, 0, 0, 0, 0]
         * [1, 2, 3, 4, 5, 0, 0, 0, 0, 0]
         * [3, 4, 5, 4, 5, 0, 0, 0, 0, 0]
         * 已写未读区间[3, 4, 5]长度为3，全部复制并左移，覆盖从 0 开始长度为 3 区间的数据
         *
         * 例2、已读区间数据 [1, 2, 3]，已写未读区间数据[4, 5]，未写区间数据[0, 0, 0, 0, 0]
         * [1, 2, 3, 4, 5, 0, 0, 0, 0, 0]
         * [4, 5, 3, 4, 5, 0, 0, 0, 0, 0]
         * 已写未读区间[4, 5]长度为2，全部复制并左移，覆盖从 0 开始长度为 2 区间的数据，其余不做处理
         */
        buf.discardReadBytes();
        System.out.println("将读取的内容丢弃后ByteBuf为========>" + buf.toString());
        System.out.println("4.ByteBuf中的内容为===============>" + Arrays.toString(buf.array()) + "\n");

        // 5.清空读写指针，只清空位置指针，不清空数据
        buf.clear();
        System.out.println("将读写指针清空后ByteBuf为==========>" + buf.toString());
        System.out.println("5.ByteBuf中的内容为===============>" + Arrays.toString(buf.array()) + "\n");

        // 6.再次写入一段内容，比第一段内容少， 清空之后写入数据还是从0开始，
        byte[] bytes2 = {1, 2, 3};
        buf.writeBytes(bytes2);
        System.out.println("写入的bytes为====================>" + Arrays.toString(bytes2));
        System.out.println("写入一段内容后ByteBuf为===========>" + buf.toString());
        System.out.println("6.ByteBuf中的内容为===============>" + Arrays.toString(buf.array()) + "\n");

        // 7.将ByteBuf清零， 只清空数据，不清空位置指针
        buf.setZero(0, buf.capacity());
        System.out.println("将内容清零后ByteBuf为==============>" + buf.toString());
        System.out.println("7.ByteBuf中的内容为================>" + Arrays.toString(buf.array()) + "\n");

        // 8.再次写入一段超过容量的内容，从写指针位置开始写，如果超过设置的容量，自动扩容到64
        // 扩容机制后续再介绍。
        byte[] bytes3 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        buf.writeBytes(bytes3);
        System.out.println("写入的bytes为====================>" + Arrays.toString(bytes3));
        System.out.println("写入一段内容后ByteBuf为===========>" + buf.toString());
        System.out.println("8.ByteBuf中的内容为===============>" + Arrays.toString(buf.array()) + "\n");
    }
}

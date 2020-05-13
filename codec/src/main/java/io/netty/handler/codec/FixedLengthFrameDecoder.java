/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * A decoder that splits the received {@link ByteBuf}s by the fixed number
 * of bytes. For example, if you received the following four fragmented packets:
 * 一种解码器，它将接收到的{@link ByteBuf}按固定的字节数分开。例如，如果你收到以下四个片段数据包:
 * <pre>
 * +---+----+------+----+
 * | A | BC | DEFG | HI |
 * +---+----+------+----+
 * </pre>
 * A {@link FixedLengthFrameDecoder}{@code (3)} will decode them into the
 * following three packets with the fixed length:
 * 将它们解码成以下三个长度固定的数据包:
 * <pre>
 * +-----+-----+-----+
 * | ABC | DEF | GHI |
 * +-----+-----+-----+
 * </pre>
 *
 * 固定长度解码器
 * 继承自字节转消息解码器
 */
public class FixedLengthFrameDecoder extends ByteToMessageDecoder {

    private final int frameLength;

    /**
     * Creates a new instance.
     *
     * @param frameLength the length of the frame 长度
     */
    public FixedLengthFrameDecoder(int frameLength) {
        checkPositive(frameLength, "frameLength");
        this.frameLength = frameLength;
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        /**
         * 返回长度为 {@code frameLength} 的ByteBuf,
         * 如果获取可读的字节数长度 < 设置长度, 返回null, 则不会加入到List中
         */
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param   ctx             the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param   in              the {@link ByteBuf} from which to read data
     * @return  frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                          be created.
     */
    protected Object decode(
            @SuppressWarnings("UnusedParameters") ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        /**
         * 如果获取可读的字节数长度 < 设置长度, 返回null
         * 举例: 当前解码器初始化时指定frameLength为10, 如果此时ByteBuf中可读的数据字节长度只剩下5了, 则直接返回null
         */
        if (in.readableBytes() < frameLength) {
            return null;
        } else {
            /**
             * 从当前的{@code readerIndex}开始，返回这个缓冲区的子区域的一个新保留的片，
             * 并根据新片的大小(= {@code length})增加{@code readerIndex}。
             * 会移动长度为frameLength的读指针
             */
            return in.readRetainedSlice(frameLength);
        }
    }
}

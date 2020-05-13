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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ByteProcessor;

import java.util.List;

/**
 * A decoder that splits the received {@link ByteBuf}s on line endings.
 * <p>
 * Both {@code "\n"} and {@code "\r\n"} are handled.
 * <p>
 * The byte stream is expected to be in UTF-8 character encoding or ASCII. The current implementation
 * uses direct {@code byte} to {@code char} cast and then compares that {@code char} to a few low range
 * ASCII characters like {@code '\n'} or {@code '\r'}. UTF-8 is not using low range [0..0x7F]
 * byte values for multibyte codepoint representations therefore fully supported by this implementation.
 * <p>
 * For a more general delimiter-based decoder, see {@link DelimiterBasedFrameDecoder}.
 *
 * 行解码器("\n" 和 "\r\n")
 */
public class LineBasedFrameDecoder extends ByteToMessageDecoder {

    /**
     * Maximum length of a frame we're willing to decode.
     * 解码的最大长度
     */
    private final int maxLength;
    /**
     * Whether or not to throw an exception as soon as we exceed maxLength.
     * 是否在超过maxLength时立即抛出异常
     */
    private final boolean failFast;
    /**
     * 返回数据是否剔除换行符("\n" 和 "\r\n")
     */
    private final boolean stripDelimiter;

    /**
     * True if we're discarding input because we're already over maxLength.
     * 如果长度超过maxLength，是否丢弃数据
     */
    private boolean discarding;
    /**
     * 已经丢弃的字节数
     */
    private int discardedBytes;

    /**
     * Last scan position.
     * 最后一次扫描的位置
     */
    private int offset;

    /**
     * Creates a new decoder.
     * @param maxLength  the maximum length of the decoded frame.
     *                   A {@link TooLongFrameException} is thrown if
     *                   the length of the frame exceeds this value.
     */
    public LineBasedFrameDecoder(final int maxLength) {
        this(maxLength, true, false);
    }

    /**
     * Creates a new decoder.
     * @param maxLength  the maximum length of the decoded frame.
     *                   A {@link TooLongFrameException} is thrown if
     *                   the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param failFast  If <tt>true</tt>, a {@link TooLongFrameException} is
     *                  thrown as soon as the decoder notices the length of the
     *                  frame will exceed <tt>maxFrameLength</tt> regardless of
     *                  whether the entire frame has been read.
     *                  If <tt>false</tt>, a {@link TooLongFrameException} is
     *                  thrown after the entire frame that exceeds
     *                  <tt>maxFrameLength</tt> has been read.
     */
    public LineBasedFrameDecoder(final int maxLength, final boolean stripDelimiter, final boolean failFast) {
        this.maxLength = maxLength;
        this.failFast = failFast;
        this.stripDelimiter = stripDelimiter;
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param   ctx             the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param   buffer          the {@link ByteBuf} from which to read data
     * @return  frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                          be created.
     */
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        // 查找("\n" 和 "\r\n")的下标位置
        final int eol = findEndOfLine(buffer);
        // 如果长度超过maxLength，是否丢弃数据, 初始为false, 这里取反则为true
        if (!discarding) {
            // 判断换行符是否存在
            if (eol >= 0) {
                final ByteBuf frame;
                // 计算本次需要读取的数据长度, 当前下标 - 已读下标
                final int length = eol - buffer.readerIndex();
                // 判断下标位置的数据是否为'\r', 是返回2 否返回1
                final int delimLength = buffer.getByte(eol) == '\r'? 2 : 1;

                // 如果当前需要读取的长度 大于 指定最大长度
                if (length > maxLength) {
                    // 设置buffer的读指针, 跳过这段数据
                    buffer.readerIndex(eol + delimLength);
                    fail(ctx, length);
                    return null;
                }
                // 判断解析的数据是否剔除换行符
                if (stripDelimiter) {
                    /**
                     * 从当前的{@code readerIndex}开始，返回这个缓冲区的子区域的一个新保留的片，
                     * 并根据新片的大小(= {@code length})增加{@code readerIndex}。
                     * 会移动长度为length的读指针
                     *
                     * 剔除换行符
                     */
                    frame = buffer.readRetainedSlice(length);
                    // 读指针跳过换行符长度
                    buffer.skipBytes(delimLength);
                } else {
                    // 不剔除换行符
                    frame = buffer.readRetainedSlice(length + delimLength);
                }

                return frame;
            } else {
                // 如果换行符不存在, 因为粘包拆包的问题, 可能会找不到换行符
                // 获取buffer的可读字节数长度
                final int length = buffer.readableBytes();
                // 如果可读长度 大于 当前解码器初始化时设置的maxLength, 丢弃本次数据
                if (length > maxLength) {
                    // 设置丢弃的字节数长度为可读字节数长度
                    discardedBytes = length;
                    // 跳过本次数据
                    buffer.readerIndex(buffer.writerIndex());
                    // 设置为丢弃模式
                    discarding = true;
                    offset = 0;
                    // 判断是否在超过maxLength时立即抛出异常
                    if (failFast) {
                        fail(ctx, "over " + discardedBytes);
                    }
                }
                return null;
            }
        } else {
            // 如果为丢弃模式
            // 判断换行符是否存在
            if (eol >= 0) {
                // 计算本次需要读取的数据长度,  已经丢弃的数据长度 + 当前下标 - 已读下标
                final int length = discardedBytes + eol - buffer.readerIndex();
                // 判断下标位置的数据是否为'\r', 是返回2 否返回1
                final int delimLength = buffer.getByte(eol) == '\r'? 2 : 1;
                // 跳过(丢弃)本次数据
                buffer.readerIndex(eol + delimLength);
                // 丢弃数据长度清零
                discardedBytes = 0;
                // 设置为非丢弃模式
                discarding = false;
                if (!failFast) {
                    fail(ctx, length);
                }
            } else {
                // 换行符不存在
                // 累加丢弃数据长度
                discardedBytes += buffer.readableBytes();
                // 跳过本次数据
                buffer.readerIndex(buffer.writerIndex());
                // We skip everything in the buffer, we need to set the offset to 0 again.
                // 我们跳过缓冲区中的所有内容，需要再次将偏移量设置为0。
                offset = 0;
            }
            return null;
        }
    }

    private void fail(final ChannelHandlerContext ctx, int length) {
        fail(ctx, String.valueOf(length));
    }

    private void fail(final ChannelHandlerContext ctx, String length) {
        ctx.fireExceptionCaught(
                new TooLongFrameException(
                        "frame length (" + length + ") exceeds the allowed maximum (" + maxLength + ')'));
    }

    /**
     * Returns the index in the buffer of the end of line found. 返回在找到的行末尾的缓冲区中的索引。
     * Returns -1 if no end of line was found in the buffer. 如果在缓冲区中没有找到行尾，则返回-1。
     */
    private int findEndOfLine(final ByteBuf buffer) {
        // 获取可读字节数
        int totalLength = buffer.readableBytes();
        // 从buffer中遍历查找('\n'), 区间从已读到可读, 不会影响读下标
        int i = buffer.forEachByte(buffer.readerIndex() + offset, totalLength - offset, ByteProcessor.FIND_LF);
        // >= 0 表示存在
        if (i >= 0) {
            offset = 0;
            // 如果('\n')存在, i为('\n')的下标 > 0, 从buffer中获取 ('\n')的下标的前一位是否为 '\r'
            // 假设buffer的数据为: abcde\r\n, 这里获取到'\n'的下标为6, 判断下标为5的是不是\r, 如果是则下标应减1, 不能将最后的\r作为数据
            if (i > 0 && buffer.getByte(i - 1) == '\r') {
                // 如果'\n'的前一位是'\r', 下标减一
                i--;
            }
        } else {
            offset = totalLength;
        }
        return i;
    }
}

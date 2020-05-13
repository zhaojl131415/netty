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
import io.netty.util.internal.ObjectUtil;

import java.util.List;

/**
 * A decoder that splits the received {@link ByteBuf}s by one or more
 * delimiters.  It is particularly useful for decoding the frames which ends
 * with a delimiter such as {@link Delimiters#nulDelimiter() NUL} or
 * {@linkplain Delimiters#lineDelimiter() newline characters}.
 *
 * <h3>Predefined delimiters</h3>
 * <p>
 * {@link Delimiters} defines frequently used delimiters for convenience' sake.
 *
 * <h3>Specifying more than one delimiter</h3>
 * <p>
 * {@link DelimiterBasedFrameDecoder} allows you to specify more than one
 * delimiter.  If more than one delimiter is found in the buffer, it chooses
 * the delimiter which produces the shortest frame.  For example, if you have
 * the following data in the buffer:
 * <pre>
 * +--------------+
 * | ABC\nDEF\r\n |
 * +--------------+
 * </pre>
 * a {@link DelimiterBasedFrameDecoder}({@link Delimiters#lineDelimiter() Delimiters.lineDelimiter()})
 * will choose {@code '\n'} as the first delimiter and produce two frames:
 * <pre>
 * +-----+-----+
 * | ABC | DEF |
 * +-----+-----+
 * </pre>
 * rather than incorrectly choosing {@code '\r\n'} as the first delimiter:
 * <pre>
 * +----------+
 * | ABC\nDEF |
 * +----------+
 * </pre>
 *
 * 自定义分隔符解码器
 * 1 找到分隔符, 超过最大长度, 直接跳过本次要读取的数据(跳过到分隔符之前的数据), 抛出异常
 * 2 没有找到分隔符, 超过最大长度, 直接跳过整个buffer的数据, 开启丢弃模式, 根据failFast判断是否马上抛出异常
 * 3 找到了分隔符, 处于丢弃模式, 关闭丢弃模式, 跳过本次要读取的数据(跳过到分隔符之前的数据), 已丢弃字节长度清零, 根据failFast判断是否马上抛出异常
 * 4 没找到了分隔符, 处于非丢弃模式, 累加丢弃的字节长度, 丢弃整个buffer的数据
 */
public class DelimiterBasedFrameDecoder extends ByteToMessageDecoder {

    // 用于缓存所有的分隔符, 可以多个
    private final ByteBuf[] delimiters;
    private final int maxFrameLength;

    /**
     * 返回数据是否剔除分隔符 没有指定默认为true
     */
    private final boolean stripDelimiter;
    private final boolean failFast;
    // 如果长度超过maxLength，是否丢弃数据, 默认为false
    private boolean discardingTooLongFrame;
    private int tooLongFrameLength;
    /** Set only when decoding with "\n" and "\r\n" as the delimiter. 仅在解码时设置“\n”和“\r\n”作为分隔符。 */
    private final LineBasedFrameDecoder lineBasedDecoder;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param delimiter  the delimiter
     */
    public DelimiterBasedFrameDecoder(int maxFrameLength, ByteBuf delimiter) {
        this(maxFrameLength, true, delimiter);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param delimiter  the delimiter
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, ByteBuf delimiter) {
        this(maxFrameLength, stripDelimiter, true, delimiter);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param failFast  If <tt>true</tt>, a {@link TooLongFrameException} is
     *                  thrown as soon as the decoder notices the length of the
     *                  frame will exceed <tt>maxFrameLength</tt> regardless of
     *                  whether the entire frame has been read.
     *                  If <tt>false</tt>, a {@link TooLongFrameException} is
     *                  thrown after the entire frame that exceeds
     *                  <tt>maxFrameLength</tt> has been read.
     * @param delimiter  the delimiter
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, boolean failFast,
            ByteBuf delimiter) {
        this(maxFrameLength, stripDelimiter, failFast, new ByteBuf[] {
                delimiter.slice(delimiter.readerIndex(), delimiter.readableBytes())});
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(int maxFrameLength, ByteBuf... delimiters) {
        this(maxFrameLength, true, delimiters);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, ByteBuf... delimiters) {
        this(maxFrameLength, stripDelimiter, true, delimiters);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param failFast  If <tt>true</tt>, a {@link TooLongFrameException} is
     *                  thrown as soon as the decoder notices the length of the
     *                  frame will exceed <tt>maxFrameLength</tt> regardless of
     *                  whether the entire frame has been read.
     *                  If <tt>false</tt>, a {@link TooLongFrameException} is
     *                  thrown after the entire frame that exceeds
     *                  <tt>maxFrameLength</tt> has been read.
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, boolean failFast, ByteBuf... delimiters) {
        validateMaxFrameLength(maxFrameLength);
        ObjectUtil.checkNonEmpty(delimiters, "delimiters");
        // 判断自定义分隔符是否为“\n”和“\r\n” 且 是否为DelimiterBasedFrameDecoder的子类
        if (isLineBased(delimiters) && !isSubclass()) {
            // 如果分隔符满足行解析器, 就使用行解析器
            lineBasedDecoder = new LineBasedFrameDecoder(maxFrameLength, stripDelimiter, failFast);
            this.delimiters = null;
        } else {
            this.delimiters = new ByteBuf[delimiters.length];
            for (int i = 0; i < delimiters.length; i ++) {
                ByteBuf d = delimiters[i];
                validateDelimiter(d);
                // 将分隔符赋值给delimiters
                this.delimiters[i] = d.slice(d.readerIndex(), d.readableBytes());
            }
            lineBasedDecoder = null;
        }
        this.maxFrameLength = maxFrameLength;
        this.stripDelimiter = stripDelimiter;
        this.failFast = failFast;
    }

    /**
     * Returns true if the delimiters are "\n" and "\r\n".
     * 如果分隔符是“\n”和“\r\n”，则返回true。
     *
     * 分隔符规则必须匹配: {@link Delimiters#lineDelimiter()}
     */
    private static boolean isLineBased(final ByteBuf[] delimiters) {
        // 判断长度是否为2
        if (delimiters.length != 2) {
            return false;
        }
        // 用于缓存'\r', '\n'的ByteBuf
        ByteBuf a = delimiters[0];
        // 用于缓存'\n'的ByteBuf
        ByteBuf b = delimiters[1];
        // 比较ByteBuf的容量
        if (a.capacity() < b.capacity()) {
            // 如果a的容量 小于 b的容量, 则交换a/b
            a = delimiters[1];
            b = delimiters[0];
        }
        // 返回 a的容量是否为2 且 b的容量是否为1 且 a是否为'\r', '\n' 且 b是否为'\n'
        return a.capacity() == 2 && b.capacity() == 1
                && a.getByte(0) == '\r' && a.getByte(1) == '\n'
                && b.getByte(0) == '\n';
    }

    /**
     * Return {@code true} if the current instance is a subclass of DelimiterBasedFrameDecoder
     * 如果当前实例是DelimiterBasedFrameDecoder的子类，则返回{@code true}
     */
    private boolean isSubclass() {
        return getClass() != DelimiterBasedFrameDecoder.class;
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
        // 如果自定义的分隔符为“\n”和“\r\n”, 行解码器不为空, 直接执行换行符解码器的解码方法
        if (lineBasedDecoder != null) {
            return lineBasedDecoder.decode(ctx, buffer);
        }
        // Try all delimiters and choose the delimiter which yields the shortest frame.
        // 尝试所有分隔符并选择产生最短帧的分隔符。
        // 用于存储数据buffer中距离最近的分隔符的长度: 即有效数据长度
        int minFrameLength = Integer.MAX_VALUE;
        // 用于存储数据buffer中最近的分隔符
        ByteBuf minDelim = null;
        // 遍历分隔符, 从数据buffer中找出最近的分隔符
        // 举例: 如果分隔符为 '$_$' 和 '@_@', 数据为 'abcd@_@efg$_$hi', 找到最近的分隔符('@_@')
        for (ByteBuf delim: delimiters) {
            // 获取分隔符位置
            int frameLength = indexOf(buffer, delim);
            if (frameLength >= 0 && frameLength < minFrameLength) {
                minFrameLength = frameLength;
                minDelim = delim;
            }
        }

        // 如果找到下标最小的分隔符
        if (minDelim != null) {
            // 下标最小的分隔符的容量长度
            int minDelimLength = minDelim.capacity();
            ByteBuf frame;

            // 判断是否为过长丢弃模式
            if (discardingTooLongFrame) {
                // We've just finished discarding a very large frame. 我们刚刚丢弃了一个非常大的框架。
                // Go back to the initial state. 回到初始状态。
                // 设置为非丢弃模式
                discardingTooLongFrame = false;
                // 跳过本次要截取的数据
                buffer.skipBytes(minFrameLength + minDelimLength);

                int tooLongFrameLength = this.tooLongFrameLength;
                // 过长丢弃长度清零
                this.tooLongFrameLength = 0;
                if (!failFast) {
                    fail(tooLongFrameLength);
                }
                return null;
            }

            // 有效数据长度 大于 最大长度
            if (minFrameLength > maxFrameLength) {
                // Discard read frame.
                // 丢弃数据。
                buffer.skipBytes(minFrameLength + minDelimLength);
                fail(minFrameLength);
                return null;
            }

            // 返回数据是否剔除分隔符, 默认为true 剔除
            if (stripDelimiter) {
                frame = buffer.readRetainedSlice(minFrameLength);
                buffer.skipBytes(minDelimLength);
            } else {
                frame = buffer.readRetainedSlice(minFrameLength + minDelimLength);
            }

            return frame;
        } else {
            // 没找到分隔符位置
            // 判断是否为过长丢弃模式
            if (!discardingTooLongFrame) {
                // 如果数据buffer可读字节数长度 > 最大长度
                if (buffer.readableBytes() > maxFrameLength) {
                    // Discard the content of the buffer until a delimiter is found.
                    // 丢弃缓冲区的内容，直到找到分隔符为止。
                    // 丢弃长度为当前数据buffer可读字节数长度
                    tooLongFrameLength = buffer.readableBytes();
                    // 丢弃
                    buffer.skipBytes(buffer.readableBytes());
                    // 丢弃模式
                    discardingTooLongFrame = true;
                    if (failFast) {
                        fail(tooLongFrameLength);
                    }
                }
            } else {
                // Still discarding the buffer since a delimiter is not found.
                // 仍然丢弃缓冲区，因为没有找到分隔符。
                tooLongFrameLength += buffer.readableBytes();
                buffer.skipBytes(buffer.readableBytes());
            }
            return null;
        }
    }

    private void fail(long frameLength) {
        if (frameLength > 0) {
            throw new TooLongFrameException(
                            "frame length exceeds " + maxFrameLength +
                            ": " + frameLength + " - discarded");
        } else {
            throw new TooLongFrameException(
                            "frame length exceeds " + maxFrameLength +
                            " - discarding");
        }
    }

    /**
     *
     * Returns the number of bytes between the readerIndex of the haystack and
     * the first needle found in the haystack.  -1 is returned if no needle is
     * found in the haystack.
     * 返回从数据buffer的readerIndex 到 第一个分隔符buffer之间的字节数。
     * 如果在数据buffer中没有找到分隔符buffer，则返回-1。
     * @param haystack  数据buffer: abcd@_@efg$_$hi
     * @param needle    分隔符buffer: $_$
     *
     */
    private static int indexOf(ByteBuf haystack, ByteBuf needle) {
        // 从数据buffer的已读下标开始, 到数据buffer的已写下标, 遍历
        for (int i = haystack.readerIndex(); i < haystack.writerIndex(); i ++) {
            int haystackIndex = i;
            int needleIndex;

            // 遍历分隔符buffer($_$), 第1次遍历"$", 第2次遍历"_", 第3次遍历"$"
            for (needleIndex = 0; needleIndex < needle.capacity(); needleIndex ++) {
                // 如果数据buffer对应下标的数据 不等于 分隔符buffer对应下标的数据, 跳出当前循环, 继续外层循环
                if (haystack.getByte(haystackIndex) != needle.getByte(needleIndex)) {
                    break;
                } else {
                    // 这里累加数据buffer中的下标, 为了在内循环中完全匹配分隔符($_$)中的每一个元素
                    haystackIndex ++;
                    // 判断读取 数据buffer 是否读到了末尾
                    if (haystackIndex == haystack.writerIndex() &&
                        // 判断数据buffer 是否全部满足分隔符($_$)
                        needleIndex != needle.capacity() - 1) {
                        return -1;
                    }
                }
            }

            // 如果相等, 表示分隔符buffer每个元素("$", "_", "$")都已经完全遍历完了
            // 如果不匹配之前就已经返回了, 如果能执行到这里, 表示找到了匹配
            if (needleIndex == needle.capacity()) {
                // Found the needle from the haystack!
                // 从数据buffer中找到分隔符buffer
                return i - haystack.readerIndex();
            }
        }
        return -1;
    }

    private static void validateDelimiter(ByteBuf delimiter) {
        ObjectUtil.checkNotNull(delimiter, "delimiter");
        if (!delimiter.isReadable()) {
            throw new IllegalArgumentException("empty delimiter");
        }
    }

    private static void validateMaxFrameLength(int maxFrameLength) {
        checkPositive(maxFrameLength, "maxFrameLength");
    }
}

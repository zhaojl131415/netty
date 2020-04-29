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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link Channel} implementations which use a Selector based approach.
 */
public abstract class AbstractNioChannel extends AbstractChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private final SelectableChannel ch;
    /**
     * 服务端
     * NioServerSocketChannel 构造方法中赋值 SelectionKey.OP_ACCEPT
     * @see NioServerSocketChannel#NioServerSocketChannel(java.nio.channels.ServerSocketChannel)
     *
     * 客户端:
     * NioSocketChannel构造方法调用了父类AbstractNioByteChannel的构造方法, 而在AbstractNioByteChannel的构造方法中赋值了 SelectionKey.OP_READ
     * @see NioSocketChannel#NioSocketChannel(io.netty.channel.Channel, java.nio.channels.SocketChannel)
     * @see AbstractNioByteChannel#AbstractNioByteChannel(io.netty.channel.Channel, java.nio.channels.SelectableChannel)
     */
    protected final int readInterestOp;
    volatile SelectionKey selectionKey;
    boolean readPending;
    private final Runnable clearReadPendingRunnable = new Runnable() {
        @Override
        public void run() {
            clearReadPending0();
        }
    };

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     * @param readInterestOp    the ops to set to receive data from the {@link SelectableChannel}
     *
     * 服务端
     * @param parent null
     * @param ch NioServerSocketChannel 服务端channel
     * @param readInterestOp 事件:SelectionKey.OP_ACCEPT
     *
     * 客户端
     * @param parent    NioServerSocketChannel  服务端channel
     * @param ch    SocketChannel   客户端channel
     * @param readInterestOp 事件:SelectionKey.OP_READ
     */
    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        // 创建id、unsafe、pipeline
        super(parent);
        this.ch = ch;
        // 服务端:SelectionKey.OP_ACCEPT  客户端:SelectionKey.OP_READ
        this.readInterestOp = readInterestOp;
        try {
            // 设置通道非阻塞 相当于nio原生的：ssc.configureBlocking(false);
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                logger.warn(
                            "Failed to close a partially initialized socket.", e2);
            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    /**
     * @return 服务端: NioServerSocketChannel, 客户端 NioSocketChannel
     */
    protected SelectableChannel javaChannel() {
        return ch;
    }

    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    /**
     * Return the current {@link SelectionKey}
     */
    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    /**
     * @deprecated No longer supported.
     * No longer supported.
     */
    @Deprecated
    protected boolean isReadPending() {
        return readPending;
    }

    /**
     * @deprecated Use {@link #clearReadPending()} if appropriate instead.
     * No longer supported.
     */
    @Deprecated
    protected void setReadPending(final boolean readPending) {
        if (isRegistered()) {
            EventLoop eventLoop = eventLoop();
            if (eventLoop.inEventLoop()) {
                setReadPending0(readPending);
            } else {
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        setReadPending0(readPending);
                    }
                });
            }
        } else {
            // Best effort if we are not registered yet clear readPending.
            // NB: We only set the boolean field instead of calling clearReadPending0(), because the SelectionKey is
            // not set yet so it would produce an assertion failure.
            this.readPending = readPending;
        }
    }

    /**
     * Set read pending to {@code false}.
     */
    protected final void clearReadPending() {
        if (isRegistered()) {
            EventLoop eventLoop = eventLoop();
            if (eventLoop.inEventLoop()) {
                clearReadPending0();
            } else {
                eventLoop.execute(clearReadPendingRunnable);
            }
        } else {
            // Best effort if we are not registered yet clear readPending. This happens during channel initialization.
            // NB: We only set the boolean field instead of calling clearReadPending0(), because the SelectionKey is
            // not set yet so it would produce an assertion failure.
            readPending = false;
        }
    }

    private void setReadPending0(boolean readPending) {
        this.readPending = readPending;
        if (!readPending) {
            ((AbstractNioUnsafe) unsafe()).removeReadOp();
        }
    }

    private void clearReadPending0() {
        readPending = false;
        ((AbstractNioUnsafe) unsafe()).removeReadOp();
    }

    /**
     * Special {@link Unsafe} sub-type which allows to access the underlying {@link SelectableChannel}
     */
    public interface NioUnsafe extends Unsafe {
        /**
         * Return underlying {@link SelectableChannel}
         */
        SelectableChannel ch();

        /**
         * Finish connect
         */
        void finishConnect();

        /**
         * Read from underlying {@link SelectableChannel}
         */
        void read();

        void forceFlush();
    }

    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        protected final void removeReadOp() {
            SelectionKey key = selectionKey();
            // Check first if the key is still valid as it may be canceled as part of the deregistration
            // from the EventLoop
            // See https://github.com/netty/netty/issues/2104
            if (!key.isValid()) {
                return;
            }
            int interestOps = key.interestOps();
            if ((interestOps & readInterestOp) != 0) {
                // only remove readInterestOp if needed
                key.interestOps(interestOps & ~readInterestOp);
            }
        }

        @Override
        public final SelectableChannel ch() {
            return javaChannel();
        }

        @Override
        public final void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                if (connectPromise != null) {
                    // Already a connect in process.
                    throw new ConnectionPendingException();
                }

                boolean wasActive = isActive();
                if (doConnect(remoteAddress, localAddress)) {
                    fulfillConnectPromise(promise, wasActive);
                } else {
                    connectPromise = promise;
                    requestedRemoteAddress = remoteAddress;

                    // Schedule connect timeout.
                    int connectTimeoutMillis = config().getConnectTimeoutMillis();
                    if (connectTimeoutMillis > 0) {
                        connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                ChannelPromise connectPromise = AbstractNioChannel.this.connectPromise;
                                ConnectTimeoutException cause =
                                        new ConnectTimeoutException("connection timed out: " + remoteAddress);
                                if (connectPromise != null && connectPromise.tryFailure(cause)) {
                                    close(voidPromise());
                                }
                            }
                        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                    }

                    promise.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isCancelled()) {
                                if (connectTimeoutFuture != null) {
                                    connectTimeoutFuture.cancel(false);
                                }
                                connectPromise = null;
                                close(voidPromise());
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                promise.tryFailure(annotateConnectException(t, remoteAddress));
                closeIfClosed();
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
            // We still need to ensure we call fireChannelActive() in this case.
            boolean active = isActive();

            // trySuccess() will return false if a user cancelled the connection attempt.
            boolean promiseSet = promise.trySuccess();

            // Regardless if the connection attempt was cancelled, channelActive() event should be triggered,
            // because what happened is what happened.
            if (!wasActive && active) {
                pipeline().fireChannelActive();
            }

            // If a user cancelled the connection attempt, close the channel, which is followed by channelInactive().
            if (!promiseSet) {
                close(voidPromise());
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, Throwable cause) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(cause);
            closeIfClosed();
        }

        @Override
        public final void finishConnect() {
            // Note this method is invoked by the event loop only if the connection attempt was
            // neither cancelled nor timed out.

            assert eventLoop().inEventLoop();

            try {
                boolean wasActive = isActive();
                doFinishConnect();
                fulfillConnectPromise(connectPromise, wasActive);
            } catch (Throwable t) {
                fulfillConnectPromise(connectPromise, annotateConnectException(t, requestedRemoteAddress));
            } finally {
                // Check for null as the connectTimeoutFuture is only created if a connectTimeoutMillis > 0 is used
                // See https://github.com/netty/netty/issues/1770
                if (connectTimeoutFuture != null) {
                    connectTimeoutFuture.cancel(false);
                }
                connectPromise = null;
            }
        }

        @Override
        protected final void flush0() {
            // Flush immediately only when there's no pending flush.
            // If there's a pending flush operation, event loop will call forceFlush() later,
            // and thus there's no need to call it now.
            if (!isFlushPending()) {
                super.flush0();
            }
        }

        @Override
        public final void forceFlush() {
            // directly call super.flush0() to force a flush now
            super.flush0();
        }

        private boolean isFlushPending() {
            SelectionKey selectionKey = selectionKey();
            return selectionKey.isValid() && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioEventLoop;
    }

    /**
     * 注册核心方法
     * @throws Exception
     */
    @Override
    protected void doRegister() throws Exception {
        boolean selected = false;
        for (;;) {
            try {
                /**
                 * 类似于nio代码: serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, att);
                 *
                 * javaChannel(): 服务端: NioServerSocketChannel, 客户端 NioSocketChannel
                 * 通过反射创建出来nio底层channel
                 *
                 * 调用nio底层将ServerSocketChannel注册到selector上
                 *
                 * 0: 表示对任何事件都不感兴趣, 后续可以通过: selectionKey.interestOps(SelectionKey.OP_ACCEPT); 改变
                 * 附加对象: this: AbstractNioChannel, 为了让客户端来连接时, 能直接拿到ServerSocketChannel对象
                 * this: 服务端: NioServerSocketChannel, 客户端 NioSocketChannel,
                 */
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                return;
            } catch (CancelledKeyException e) {
                if (!selected) {
                    // Force the Selector to select now as the "canceled" SelectionKey may still be
                    // cached and not removed because no Select.select(..) operation was called yet.
                    eventLoop().selectNow();
                    selected = true;
                } else {
                    // We forced a select operation on the selector before but the SelectionKey is still cached
                    // for whatever reason. JDK bug ?
                    throw e;
                }
            }
        }
    }

    @Override
    protected void doDeregister() throws Exception {
        eventLoop().cancel(selectionKey());
    }

    /**
     * selectionKey绑定事件
     * 服务端绑定:SelectionKey.OP_ACCEPT
     * 客户端绑定:SelectionKey.OP_READ
     *
     * @throws Exception
     */
    @Override
    protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        /**
         * selectionKey赋值：
         * @see AbstractNioChannel#doRegister()
         */
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        /**
         * interestOps：注册时赋值为0，表示对任何事件都不敢兴趣
         * readInterestOp：
         * 服务端: NioServerSocketChannel 构造方法中赋值 SelectionKey.OP_ACCEPT
         * 客户端: NioSocketChannel 构造方法中赋值 SelectionKey.OP_READ
         *
         * interestOps & readInterestOp即与运算为：0 & 16 = 0
         */
        if ((interestOps & readInterestOp) == 0) {
            // interestOps | readInterestOp即或运算为：0 | 16 = 16
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    /**
     * Connect to the remote peer
     */
    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    /**
     * Finish the connect
     */
    protected abstract void doFinishConnect() throws Exception;

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the original one.
     * Note that this method does not create an off-heap copy if the allocation / deallocation cost is too high,
     * but just returns the original {@link ByteBuf}..
     */
    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(buf);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            ByteBuf directBuf = alloc.directBuffer(readableBytes);
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(buf);
            return directBuf;
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf != null) {
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(buf);
            return directBuf;
        }

        // Allocating and deallocating an unpooled direct buffer is very expensive; give up.
        return buf;
    }

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the specified holder.
     * The caller must ensure that the holder releases the original {@link ByteBuf} when the holder is released by
     * this method.  Note that this method does not create an off-heap copy if the allocation / deallocation cost is
     * too high, but just returns the original {@link ByteBuf}..
     */
    protected final ByteBuf newDirectBuffer(ReferenceCounted holder, ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(holder);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            ByteBuf directBuf = alloc.directBuffer(readableBytes);
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(holder);
            return directBuf;
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf != null) {
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(holder);
            return directBuf;
        }

        // Allocating and deallocating an unpooled direct buffer is very expensive; give up.
        if (holder != buf) {
            // Ensure to call holder.release() to give the holder a chance to release other resources than its content.
            buf.retain();
            ReferenceCountUtil.safeRelease(holder);
        }

        return buf;
    }

    @Override
    protected void doClose() throws Exception {
        ChannelPromise promise = connectPromise;
        if (promise != null) {
            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(new ClosedChannelException());
            connectPromise = null;
        }

        ScheduledFuture<?> future = connectTimeoutFuture;
        if (future != null) {
            future.cancel(false);
            connectTimeoutFuture = null;
        }
    }
}

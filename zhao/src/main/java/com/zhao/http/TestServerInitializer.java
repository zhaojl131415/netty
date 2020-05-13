package com.zhao.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

public class TestServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        pipeline.addLast("httpServerCodec",new HttpServerCodec());
        pipeline.addLast("testServerHandler",new TestServerHandler());
        // 如果碰到比较复杂耗时的处理器, 可以使用如下方式: 会通过开启线程池, 执行处理器逻辑.
        // 虽说nio是非阻塞io, 客户端会平均分配到各个工作selector上, selector内的客户端会阻塞同一个selector的客户端, 不会阻塞其他selector中的客户端
        // 举例: 如果是100个客户端, 4个工作selector, 每个selector上都有25个客户端, 这25个之间会阻塞, 但是不会阻塞另外的75个.
        // 而通过这种方式, 各个selector都会创建一个线程池, 就可以减少阻塞.
        pipeline.addLast(new NioEventLoopGroup(), new TestServerHandler());
    }
}

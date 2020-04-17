package com.zhao;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

//客户端业务处理类

/**
 * 添加了 @ChannelHandler.Sharable注解, 表示这个处理器为共享的
 * 可以理解为spring中的单例, 如果没加, 就是原型的, 每次都会新建一个
 *
 * 可以的话尽量使用单例, 节约空间, 但是如果处理器中包含全局的成员变量, 最好不要使用单例
 */
@ChannelHandler.Sharable
public class NettyTestHandler extends ChannelInboundHandlerAdapter{

    //通道准备就绪事件
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelActive-----"+ctx);
    }

    //读取数据事件
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof NioSocketChannel){
            System.out.println(msg.getClass());
            ctx.fireChannelRead(msg);
            return;
        }
        ByteBuf byteBuf= (ByteBuf) msg;
        System.out.println("channelRead:"+byteBuf.toString(CharsetUtil.UTF_8));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        System.out.println("handlerAdded");
    }


    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelRegistered");
    }

}

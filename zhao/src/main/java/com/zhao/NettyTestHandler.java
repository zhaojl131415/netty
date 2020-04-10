package com.zhao;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

//客户端业务处理类
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

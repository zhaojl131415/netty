package com.zhao;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.AbstractChannelHandlerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

//服务器端的业务逻辑处理类
public class NettyServerHandler extends ChannelInboundHandlerAdapter{


    private int count=0;

    //读取数据事件
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        System.out.println("服务端上下文对象"+ctx);
        String byteBuf= (String) msg;
        System.out.println("客户端发来消息:"+byteBuf);
//        ctx.fireChannelRead(msg);

        /**
         * 假设pipeline中有多个handler, 顺序为 head - Inbound1 - Inbound2 - Outbound1 - Outbound2 - Inbound3 - Outbound3 - tail
         * 从源码中得知尾节点为入栈处理器, 而头节点既是入栈处理器也是出栈处理器
         * 所以pipeline中的处理器, 读取数据顺序为 head -> Inbound1 -> Inbound2 -> Inbound3 -> tail,
         * 假设当前处理器对应为Inbound3, 且并没有调用 {@code ctx.fireChannelRead(msg);} 继续向下传播, 而是直接执行给客户端写回数据
         * 这里两种方式都可以给客户端写回数据, 但是实现方式, 执行流程却有所不同
         */

        /**
         * @see AbstractChannel#writeAndFlush(java.lang.Object)
         * 1 通过通道获取到pipeline, 然后获取到tail, 从尾节点开始向上递归寻找出栈处理器执行, 顺序为 tail -> Outbound3 -> Outbound2 -> Outbound1 -> head
         */
        ctx.channel().writeAndFlush(Unpooled.copiedBuffer("同好"+System.getProperty("line.separator"),CharsetUtil.UTF_8));
        /**
         * @see AbstractChannelHandlerContext#writeAndFlush(java.lang.Object)
         * 2 直接从当前位置向上递归寻找出栈处理器执行, 顺序为 Outbound2 -> Outbound1 -> head, 其中tail和Outbound3没有执行
         */
        ctx.writeAndFlush(Unpooled.copiedBuffer("同好"+System.getProperty("line.separator"),CharsetUtil.UTF_8));
//        System.out.println(++count);
    }


    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Netty Server channelRegistered");
        ctx.fireChannelRegistered();
    }

    //读取数据完毕事件
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    //异常发生回调
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}

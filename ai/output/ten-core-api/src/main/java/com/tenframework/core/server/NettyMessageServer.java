package com.tenframework.core.server;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.MessageDecoder;
import com.tenframework.core.message.MessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyMessageServer {

    private final int port;
    private final Engine engine;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyMessageServer(int port, Engine engine) {
        this.port = port;
        this.engine = engine;
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(); // (1) 用于接受传入连接的线程组
        workerGroup = new NioEventLoopGroup(); // (2) 用于处理已接受连接的I/O操作的线程组

        try {
            ServerBootstrap b = new ServerBootstrap(); // (3) 一个辅助类，用于设置服务器
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // (4) 使用NioServerSocketChannel作为服务器的通道类型
                    .childHandler(new ChannelInitializer<SocketChannel>() { // (5)
                                                                            // ChannelInitializer用于为新接受的通道设置ChannelPipeline
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    // 解决TCP粘包/半包问题
                                    // maxFrameLength: 消息的最大长度
                                    // lengthFieldOffset: 长度字段的偏移量
                                    // lengthFieldLength: 长度字段本身的字节数
                                    // lengthAdjustment: 长度字段的调整值
                                    // initialBytesToStrip: 从解码帧中剥离的字节数
                                    new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4),
                                    new MessageEncoder(), // 消息编码器
                                    new MessageDecoder(), // 消息解码器
                                    new EngineChannelInboundHandler(engine) // 自定义业务处理器，将消息提交给Engine
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128) // (6) 设置服务器套接字的选项，如积压队列大小
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // (7) 设置已接受通道的选项，如启用TCP KeepAlive

            // 绑定端口并启动服务器
            ChannelFuture f = b.bind(port).sync(); // (8) 绑定端口并等待绑定操作完成

            log.info("NettyMessageServer started and listening on port {}", port);

            // 等待服务器套接字关闭
            // 在这个例子中，这不会发生，除非调用shutdown()
            f.channel().closeFuture().sync();
        } finally {
            shutdown(); // (9) 确保在所有操作完成后关闭线程组
        }
    }

    public void shutdown() {
        log.info("Shutting down NettyMessageServer on port {}", port);
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("NettyMessageServer on port {} shut down.", port);
    }
}
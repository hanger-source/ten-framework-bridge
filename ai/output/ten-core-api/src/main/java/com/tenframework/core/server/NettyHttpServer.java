package com.tenframework.core.server;

import com.tenframework.core.engine.Engine;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyHttpServer {

    private final int port;
    private final Engine engine; // Engine 实例
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyHttpServer(int port, Engine engine) {
        this.port = port;
        this.engine = engine;
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpRequestDecoder());
                            ch.pipeline().addLast(new HttpCommandResultOutboundHandler()); // 添加HTTP命令结果编码器
                            ch.pipeline().addLast(new HttpResponseEncoder());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new HttpCommandInboundHandler(engine)); // 添加自定义的HTTP命令处理器
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();
            log.info("NettyHttpServer started and listening on port {}", port);

            f.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        log.info("Shutting down NettyHttpServer on port {}", port);
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("NettyHttpServer on port {} shut down.", port);
    }
}
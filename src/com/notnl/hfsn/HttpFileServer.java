/*
 * Copyright (C) 2017 noti0na1 <i@notnl.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.notnl.hfsn;

import com.notnl.hfsn.Config;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.internal.StringUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author noti0na1 <i@notnl.com>
 */
public class HttpFileServer {

    public void run() throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (Config.SSL) {
            if (StringUtil.isNullOrEmpty(Config.CERTIFICATE)
                    || StringUtil.isNullOrEmpty(Config.PRIVATE_KEY)) {
                SelfSignedCertificate ssc = new SelfSignedCertificate("notnl.ml");
                sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                        .sslProvider(SslProvider.JDK).build();
            } else {
                sslCtx = SslContextBuilder.forServer(
                        new File(Config.CERTIFICATE), new File(Config.PRIVATE_KEY)
                ).build();
            }
        } else {
            sslCtx = null;
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            List<ChannelFuture> futures = new ArrayList<>();

            ServerBootstrap main = new ServerBootstrap();
            main.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new HttpFileServerInitializer(sslCtx));
            futures.add(main.bind(Config.PORT));

            if (Config.SSL && Config.REDIRECT_TO_HTTPS) {
                ServerBootstrap redirect = new ServerBootstrap();
                redirect.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new HttpRedirectInitializer());
                futures.add(redirect.bind(80));
            }

            for (ChannelFuture f : futures) {
                f.sync().channel().closeFuture().sync();
            }
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

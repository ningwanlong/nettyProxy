/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.example.socksproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private final Bootstrap b = new Bootstrap();

    private static final String oSocketIp = "127.0.01";
    private static final int oSocketPort = 1080;

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws Exception {
        if (message instanceof Socks4CommandRequest) {
            final Socks4CommandRequest request = (Socks4CommandRequest) message;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    new FutureListener<Channel>() {
                        @Override
                        public void operationComplete(final Future<Channel> future) throws Exception {
                            final Channel outboundChannel = future.getNow();
                            if (future.isSuccess()) {
                                ChannelFuture responseFuture = ctx.channel().writeAndFlush(
                                        new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS));

                                responseFuture.addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture channelFuture) {
                                        ctx.pipeline().remove(SocksServerConnectHandler.this);
                                        outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                        ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                    }
                                });
                            } else {
                                ctx.channel().writeAndFlush(
                                        new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED));
                                SocksServerUtils.closeOnFlush(ctx.channel());
                            }
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>(){
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new Socks5ProxyHandler(new InetSocketAddress(oSocketIp, oSocketPort)))
                                    .addLast(new DirectClientHandler(promise));
                        }
                    })
//                    .handler(new DirectClientHandler(promise));
            ;

            String inetHost = request.dstAddr();
            int inetPort = request.dstPort();
            final Result result = getMappingResult(inetHost, inetPort);

            b.connect(result.inetHost, result.inetPort).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                                new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)
                        );
                        SocksServerUtils.closeOnFlush(ctx.channel());
                    }
                }
            });
        } else if (message instanceof Socks5CommandRequest) {
            final Socks5CommandRequest request = (Socks5CommandRequest) message;

            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    new FutureListener<Channel>() {
                        @Override
                        public void operationComplete(final Future<Channel> future) throws Exception {
                            final Channel outboundChannel = future.getNow();
                            if (future.isSuccess()) {
                                ChannelFuture responseFuture =
                                        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                                Socks5CommandStatus.SUCCESS,
                                                request.dstAddrType(),
                                                request.dstAddr(),
                                                request.dstPort()));

                                responseFuture.addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture channelFuture) {
                                        ctx.pipeline().remove(SocksServerConnectHandler.this);
                                        outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                        ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                    }
                                });
                            } else {
                                ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                        Socks5CommandStatus.FAILURE, request.dstAddrType()));
                                SocksServerUtils.closeOnFlush(ctx.channel());
                            }
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>(){
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new Socks5ProxyHandler(new InetSocketAddress(oSocketIp, oSocketPort)))
                                    .addLast(new DirectClientHandler(promise));
                        }
                    })
//                    .handler(new DirectClientHandler(promise))
            ;

            String inetHost = request.dstAddr();
            int inetPort = request.dstPort();
            final Result result = getMappingResult(inetHost, inetPort);
            b.connect(result.inetHost, result.inetPort).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                                new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));
                        SocksServerUtils.closeOnFlush(ctx.channel());
                    }
                }
            });
        } else {
            ctx.close();
        }
    }

    private static Result getMappingResult(String inetHost, int inetPort) {
        String mapIpPort = HandleMapping.mappingMap.get(inetHost + ":" + inetPort);
        if (mapIpPort == null){
            mapIpPort = HandleMapping.mappingMap.get(inetHost);
        }
        if (mapIpPort != null){
            final String[] split = mapIpPort.split(":");
            final String mapIp = split[0];
            inetHost = mapIp;
            if (split.length==2){
                final String mapPort = split[1];
                inetPort = Integer.parseInt(mapPort);
            }
        }
        Result result = new Result(inetHost, inetPort);
        return result;
    }

    private static class Result {
        public final String inetHost;
        public final int inetPort;

        public Result(String inetHost, int inetPort) {
            this.inetHost = inetHost;
            this.inetPort = inetPort;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}

package com.example.tagihan.configuration;

import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.HttpServer;

@Configuration
public class NettyConfig {

    @Bean
    public NettyServerCustomizer nettyServerCustomizer() {
        return httpServer -> httpServer.httpRequestDecoder(spec ->
                spec.validateHeaders(false)
                        .allowDuplicateContentLengths(true)
        );
    }
}
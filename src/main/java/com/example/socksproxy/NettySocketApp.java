package com.example.socksproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * @author ysn
 * @date 2023/11/21
 */
@SpringBootApplication
public class NettySocketApp {
    public static void main(String[] args) {
        new SpringApplicationBuilder(NettySocketApp.class).web(WebApplicationType.NONE).run(args);
    }
}

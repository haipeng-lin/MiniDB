package com.peng.minidb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MiniDBApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniDBApplication.class, args);
    }
}

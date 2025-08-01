package com.agora.tenframework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TEN Framework Server Application
 *
 * @author Agora IO
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TenFrameworkServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenFrameworkServerApplication.class, args);
    }
}
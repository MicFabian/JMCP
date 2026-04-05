package com.example.javamcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CoreConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}

package com.example.javamcp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(@Value("${spring.cache.caffeine.spec:maximumSize=1000,expireAfterWrite=5m}") String caffeineSpec) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("searchResults");
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(Caffeine.from(caffeineSpec).recordStats());
        return cacheManager;
    }
}

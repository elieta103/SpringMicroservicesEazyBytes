package com.eazybytes.accounts.config;

import io.github.resilience4j.spring6.retry.configure.RetryConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetryConfig {
    @Bean
    public RetryConfiguration retryConfiguration() {
        return new RetryConfiguration();
    }
}
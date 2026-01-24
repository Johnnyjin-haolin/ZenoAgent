package com.aiagent.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置
 *
 * @author aiagent
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        String host = redisProperties.getHost();
        int port = redisProperties.getPort();
        boolean ssl = redisProperties.isSsl();
        String address = String.format("%s://%s:%d", ssl ? "rediss" : "redis", host, port);

        Config config = new Config();
        SingleServerConfig singleServer = config.useSingleServer()
            .setAddress(address)
            .setDatabase(redisProperties.getDatabase());

        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty()) {
            singleServer.setPassword(redisProperties.getPassword());
        }

        return Redisson.create(config);
    }
}


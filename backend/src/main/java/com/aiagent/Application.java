package com.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Agent 独立应用入口
 * 
 * 配置文件加载说明：
 * 1. application.yml - 基础配置
 * 2. config/profile/{profile}/application.yml - profile特定配置（根据激活的profile自动加载）
 * 
 * 例如：profile=local 会自动加载 config/profile/local/application.yml
 * 
 * 配置加载通过 application.yml 中的 spring.config.import 实现
 * 
 * @author aiagent
 */
@SpringBootApplication
public class Application {
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}



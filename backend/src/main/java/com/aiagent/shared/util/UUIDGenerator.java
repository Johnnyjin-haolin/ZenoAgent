package com.aiagent.shared.util;

import java.util.UUID;

/**
 * UUID生成器
 * 
 * @author aiagent
 */
public class UUIDGenerator {
    
    /**
     * 生成UUID（去掉横线）
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成标准UUID（带横线）
     */
    public static String generateStandard() {
        return UUID.randomUUID().toString();
    }
}



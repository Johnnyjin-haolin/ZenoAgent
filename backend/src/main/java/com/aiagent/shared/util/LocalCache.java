package com.aiagent.shared.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地缓存工具类
 * 用于存储SSE连接等临时数据
 * 
 * @author aiagent
 */
public class LocalCache {
    
    private static final Map<String, Map<String, Object>> cacheMap = new ConcurrentHashMap<>();
    
    /**
     * 获取缓存值
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String prefix, String key) {
        Map<String, Object> prefixCache = cacheMap.get(prefix);
        if (prefixCache == null) {
            return null;
        }
        return (T) prefixCache.get(key);
    }
    
    /**
     * 设置缓存值
     */
    public static void put(String prefix, String key, Object value) {
        cacheMap.computeIfAbsent(prefix, k -> new ConcurrentHashMap<>()).put(key, value);
    }
    
    /**
     * 移除缓存值
     */
    public static void remove(String prefix, String key) {
        Map<String, Object> prefixCache = cacheMap.get(prefix);
        if (prefixCache != null) {
            prefixCache.remove(key);
            if (prefixCache.isEmpty()) {
                cacheMap.remove(prefix);
            }
        }
    }
    
    /**
     * 清空指定前缀的缓存
     */
    public static void clear(String prefix) {
        cacheMap.remove(prefix);
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        cacheMap.clear();
    }
}


package com.aiagent.common.util;

/**
 * 字符串工具类
 * 替代原项目中的 oConvertUtils
 * 
 * @author aiagent
 */
public class StringUtils {
    
    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
    
    /**
     * 判断对象是否为空
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            return ((String) obj).trim().isEmpty();
        }
        return false;
    }
    
    /**
     * 判断对象是否不为空
     */
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }
    
    /**
     * 获取字符串，如果为空返回默认值
     */
    public static String getString(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }
    
    /**
     * 获取整数值，如果为空或无效返回默认值
     */
    public static Integer getInteger(Object obj, Integer defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 获取双精度浮点数值，如果为空或无效返回默认值
     */
    public static Double getDouble(Object obj, Double defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Double) {
            return (Double) obj;
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}



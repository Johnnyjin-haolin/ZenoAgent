package com.aiagent.common.enums;

/**
 * MCP 服务器作用域
 * 0 = GLOBAL  : 服务端执行，所有用户可用，密钥存数据库
 * 1 = PERSONAL: 客户端执行，按用户隔离，密钥存浏览器 localStorage
 */
public enum McpScope {

    GLOBAL(0),
    PERSONAL(1);

    private final int value;

    McpScope(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static McpScope fromValue(int value) {
        for (McpScope scope : values()) {
            if (scope.value == value) {
                return scope;
            }
        }
        return GLOBAL;
    }
}

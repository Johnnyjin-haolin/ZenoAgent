package com.aiagent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果包装类
 * 
 * @author aiagent
 */
@Data
public class Page<T> {
    
    /**
     * 数据列表
     */
    private List<T> records;
    
    /**
     * 总记录数
     */
    private long total;
    
    /**
     * 当前页码
     */
    private long current;
    
    /**
     * 每页大小
     */
    private long size;
    
    /**
     * 总页数
     */
    public long getPages() {
        if (size == 0) {
            return 0;
        }
        long pages = total / size;
        if (total % size != 0) {
            pages++;
        }
        return pages;
    }
}


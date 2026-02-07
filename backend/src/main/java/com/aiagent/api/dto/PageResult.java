package com.aiagent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 通用分页响应结果
 * 
 * @param <T> 数据项类型
 * @author aiagent
 */
@Data
public class PageResult<T> {
    
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
    private long pageNo;
    
    /**
     * 每页大小
     */
    private long pageSize;
    
    /**
     * 总页数
     */
    private long pages;
    
    /**
     * 从 Page 对象构建 PageResult
     * 
     * @param page 分页对象
     * @param <T> 数据项类型
     * @return PageResult 对象
     */
    public static <T> PageResult<T> from(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNo(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}

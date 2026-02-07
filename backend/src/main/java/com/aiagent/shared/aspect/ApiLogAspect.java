package com.aiagent.shared.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * API接口日志切面
 * 统一记录所有Controller接口的请求和响应日志
 * 
 * @author aiagent
 */
@Slf4j
@Aspect
@Component
public class ApiLogAspect {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 需要排除的接口路径（不记录日志）
     */
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
        "/aiagent/health",
        "/swagger-ui.html",
        "/swagger-ui",
        "/api-docs",
        "/favicon.ico"
    ));
    
    /**
     * 敏感字段名称（需要脱敏）
     */
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(Arrays.asList(
        "password", "pwd", "token", "apiKey", "secret", "authorization"
    ));
    
    /**
     * 响应体最大长度（超过此长度将截断）
     */
    private static final int MAX_RESPONSE_LENGTH = 2000;
    
    /**
     * 请求参数最大长度（超过此长度将截断）
     */
    private static final int MAX_REQUEST_LENGTH = 2000;

    /**
     * 定义切点：拦截所有RestController的方法
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void restController() {}

    /**
     * 环绕通知：记录接口请求和响应
     */
    @Around("restController()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String requestPath = request.getRequestURI();
        
        // 排除不需要记录的接口
        if (EXCLUDED_PATHS.contains(requestPath)) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        
        // 记录请求日志
        logRequest(requestId, method, requestPath, joinPoint);
        
        Object result = null;
        Throwable exception = null;
        
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            // 异步记录响应日志（避免影响接口性能）
            logResponseAsync(requestId, method, requestPath, result, exception, duration);
        }
    }

    /**
     * 记录请求日志
     */
    private void logRequest(String requestId, String method, String path, ProceedingJoinPoint joinPoint) {
        try {
            Map<String, Object> requestInfo = new HashMap<>();
            requestInfo.put("requestId", requestId);
            requestInfo.put("method", method);
            requestInfo.put("path", path);
            
            // 获取请求参数
            Object[] args = joinPoint.getArgs();
            Parameter[] parameters = getParameters(joinPoint);
            Map<String, Object> params = new HashMap<>();
            
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                
                // 跳过HttpServletRequest、HttpServletResponse等框架对象
                if (arg instanceof HttpServletRequest || arg instanceof javax.servlet.http.HttpServletResponse) {
                    continue;
                }
                
                // 处理MultipartFile（文件上传）
                if (arg instanceof MultipartFile) {
                    MultipartFile file = (MultipartFile) arg;
                    params.put("file", String.format("MultipartFile[name=%s, size=%d]", 
                        file.getOriginalFilename(), file.getSize()));
                    continue;
                }
                
                // 获取参数名
                String paramName = getParameterName(parameters, i);
                if (paramName != null) {
                    Object paramValue = maskSensitiveData(paramName, arg);
                    params.put(paramName, formatParamValue(paramValue));
                } else {
                    params.put("arg" + i, formatParamValue(arg));
                }
            }
            
            requestInfo.put("params", params);
            
            log.info("API请求 [{}] {} {} | 参数: {}", 
                requestId, method, path, formatJson(requestInfo.get("params")));
        } catch (Exception e) {
            log.warn("记录请求日志失败", e);
        }
    }

    /**
     * 异步记录响应日志
     */
    private void logResponseAsync(String requestId, String method, String path, 
                                  Object result, Throwable exception, long duration) {
        // 使用新线程异步记录，避免影响接口性能
        new Thread(() -> {
            try {
                if (exception != null) {
                    log.error("API响应 [{}] {} {} | 耗时: {}ms | 异常: {}", 
                        requestId, method, path, duration, exception.getMessage());
                } else {
                    String responseStr = formatResponse(result);
                    log.info("API响应 [{}] {} {} | 耗时: {}ms | 响应: {}", 
                        requestId, method, path, duration, responseStr);
                }
            } catch (Exception e) {
                log.warn("记录响应日志失败", e);
            }
        }).start();
    }

    /**
     * 获取方法参数
     */
    private Parameter[] getParameters(ProceedingJoinPoint joinPoint) {
        try {
            Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
            return method.getParameters();
        } catch (Exception e) {
            return new Parameter[0];
        }
    }

    /**
     * 获取参数名
     */
    private String getParameterName(Parameter[] parameters, int index) {
        if (index >= parameters.length) {
            return null;
        }
        Parameter parameter = parameters[index];
        
        // 优先使用@RequestParam或@RequestBody注解中的名称
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        
        RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
        if (requestBody != null) {
            return "body";
        }
        
        // 使用参数名
        return parameter.getName();
    }

    /**
     * 格式化参数值
     */
    private Object formatParamValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 如果是集合或数组，只显示大小
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            return String.format("Collection[size=%d]", collection.size());
        }
        
        if (value.getClass().isArray()) {
            return String.format("Array[length=%d]", java.lang.reflect.Array.getLength(value));
        }
        
        // 如果是复杂对象，转换为JSON字符串
        String jsonStr = formatJson(value);
        if (jsonStr.length() > MAX_REQUEST_LENGTH) {
            return jsonStr.substring(0, MAX_REQUEST_LENGTH) + "...(truncated)";
        }
        
        return jsonStr;
    }

    /**
     * 格式化响应
     */
    private String formatResponse(Object response) {
        if (response == null) {
            return "null";
        }
        
        try {
            String jsonStr = formatJson(response);
            if (jsonStr.length() > MAX_RESPONSE_LENGTH) {
                return jsonStr.substring(0, MAX_RESPONSE_LENGTH) + "...(truncated)";
            }
            return jsonStr;
        } catch (Exception e) {
            return response.toString();
        }
    }

    /**
     * 敏感信息脱敏
     */
    private Object maskSensitiveData(String fieldName, Object value) {
        if (value == null || fieldName == null) {
            return value;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        for (String sensitiveField : SENSITIVE_FIELDS) {
            if (lowerFieldName.contains(sensitiveField)) {
                if (value instanceof String) {
                    String str = (String) value;
                    if (str.length() > 4) {
                        return str.substring(0, 2) + "****" + str.substring(str.length() - 2);
                    }
                    return "****";
                }
                return "****";
            }
        }
        
        return value;
    }

    /**
     * 格式化JSON
     */
    private String formatJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj != null ? obj.toString() : "null";
        }
    }
}

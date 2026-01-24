package com.aiagent.infrastructure.config;

import com.aiagent.shared.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP配置文件加载器
 * 支持从JSON文件加载配置，并支持热加载
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class McpConfigLoader {
    
    /**
     * 配置文件路径（可通过 application.yml 中的 aiagent.mcp.config-path 配置）
     * 默认：如果未配置，会自动查找项目根目录下的 config/mcp.json 或 resources/config/mcp.json
     */
    @Value("${aiagent.mcp.config-path:}")
    private String configPath;
    
    /**
     * 是否启用热加载
     */
    @Value("${aiagent.mcp.hot-reload:true}")
    private boolean hotReloadEnabled;
    
    /**
     * ResourceLoader（用于加载 resources 下的文件）
     */
    private final ResourceLoader resourceLoader;
    
    /**
     * Spring Environment（用于获取当前激活的profile）
     */
    private final Environment environment;
    
    /**
     * 配置变更监听器列表
     */
    private final List<Runnable> configChangeListeners = new CopyOnWriteArrayList<>();
    
    /**
     * 文件监听线程
     */
    private Thread fileWatcherThread;
    
    /**
     * 当前配置
     */
    private volatile McpJsonConfig currentConfig;
    
    /**
     * 配置文件路径（可能是文件系统路径或 classpath 资源）
     */
    private Path configFilePath;
    
    /**
     * 是否为 classpath 资源（classpath 资源不支持热加载）
     */
    private boolean isClasspathResource = false;
    
    /**
     * 上次修改时间
     */
    private long lastModifiedTime;
    
    /**
     * 构造函数（注入 ResourceLoader 和 Environment）
     */
    public McpConfigLoader(ResourceLoader resourceLoader, Environment environment) {
        this.resourceLoader = resourceLoader;
        this.environment = environment;
    }
    
    @PostConstruct
    public void init() {
        // 确定配置文件路径
        configFilePath = determineConfigPath();
        
        // 加载配置
        loadConfig();
        
        // 如果启用热加载，启动文件监听
        if (hotReloadEnabled) {
            startFileWatcher();
        }
    }
    
    /**
     * 确定配置文件路径
     * 优先级：
     * 1. 配置文件中指定的路径（aiagent.mcp.config-path，外部文件，支持热加载）
     * 2. 根据当前profile查找：profile/{profile}/mcp.json（外部文件，支持热加载）
     * 3. 项目根目录下的 mcp.json（外部文件，支持热加载）
     * 4. resources/profile/{profile}/mcp.json（classpath 资源，不支持热加载）
     * 5. resources/config/mcp.json（classpath 资源，不支持热加载，向后兼容）
     */
    private Path determineConfigPath() {
        // 1. 优先使用配置文件中指定的路径（外部文件，支持热加载）
        if (StringUtils.isNotEmpty(configPath)) {
            Path path = Paths.get(configPath);
            if (Files.exists(path)) {
                log.info("使用指定的配置文件路径: {}", path.toAbsolutePath());
                isClasspathResource = false;
                return path;
            } else {
                log.warn("指定的配置文件不存在: {}，尝试其他路径", path.toAbsolutePath());
            }
        }
        
        // 2. 根据当前激活的profile查找配置文件（优先级最高）
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            String profile = activeProfiles[0]; // 使用第一个激活的profile
            log.debug("当前激活的profile: {}", profile);
            
            // 尝试外部文件路径：profile/{profile}/mcp.json
            String[] profilePaths = {
                "profile/" + profile + "/mcp.json",
                "../profile/" + profile + "/mcp.json",
                System.getProperty("user.dir") + "/profile/" + profile + "/mcp.json"
            };
            
            for (String pathStr : profilePaths) {
                Path path = Paths.get(pathStr).toAbsolutePath();
                if (Files.exists(path)) {
                    log.info("找到profile配置文件: {}（profile: {}，支持热加载）", path.toAbsolutePath(), profile);
                    isClasspathResource = false;
                    return path;
                }
            }
            
            // 尝试classpath路径：classpath:profile/{profile}/mcp.json
            try {
                String classpathPath = "classpath:profile/" + profile + "/mcp.json";
                Resource resource = resourceLoader.getResource(classpathPath);
                if (resource.exists() && resource.isReadable()) {
                    log.info("使用classpath profile配置文件: {}（profile: {}，不支持热加载）", classpathPath, profile);
                    isClasspathResource = true;
                    return Paths.get(classpathPath);
                }
            } catch (Exception e) {
                log.debug("无法加载classpath profile配置文件: profile/{}/mcp.json", profile, e);
            }
        }
        
        // 3. 尝试查找外部默认配置文件（支持热加载）
        String[] externalPaths = {
            "mcp.json",  // 项目根目录
            "../mcp.json",  // 相对于backend目录
            System.getProperty("user.dir") + "/mcp.json"  // 工作目录
        };
        
        for (String pathStr : externalPaths) {
            Path path = Paths.get(pathStr).toAbsolutePath();
            if (Files.exists(path)) {
                log.info("找到外部配置文件: {}（支持热加载）", path.toAbsolutePath());
                isClasspathResource = false;
                return path;
            }
        }
        
        // 4. 尝试classpath默认配置文件（向后兼容）
        try {
            Resource resource = resourceLoader.getResource("classpath:config/mcp.json");
            if (resource.exists() && resource.isReadable()) {
                log.info("使用 classpath 资源: classpath:config/mcp.json（不支持热加载）");
                isClasspathResource = true;
                return Paths.get("classpath:config/mcp.json");
            }
        } catch (Exception e) {
            log.debug("无法加载 classpath 资源: classpath:config/mcp.json", e);
        }
        
        // 5. 如果都不存在，根据profile使用默认路径（外部文件，首次运行需要创建）
        String[] activeProfilesForDefault = environment.getActiveProfiles();
        Path defaultPath;
        if (activeProfilesForDefault.length > 0) {
            String profile = activeProfilesForDefault[0];
            defaultPath = Paths.get("profile/" + profile + "/mcp.json").toAbsolutePath();
            log.warn("配置文件不存在，将使用默认路径: {}（profile: {}，首次运行需要创建，支持热加载）", defaultPath, profile);
        } else {
            defaultPath = Paths.get("mcp.json").toAbsolutePath();
            log.warn("配置文件不存在，将使用默认路径: {}（首次运行需要创建，支持热加载）", defaultPath);
        }
        isClasspathResource = false;
        return defaultPath;
    }
    
    /**
     * 加载配置文件
     */
    public McpJsonConfig loadConfig() {
        try {
            String content;
            
            if (isClasspathResource) {
                // 从 classpath 加载
                String resourcePath = configFilePath.toString();
                if (resourcePath.startsWith("classpath:")) {
                    Resource resource = resourceLoader.getResource(resourcePath);
                    if (!resource.exists() || !resource.isReadable()) {
                        log.warn("classpath 配置文件不存在: {}", resourcePath);
                        currentConfig = new McpJsonConfig();
                        return currentConfig;
                    }
                    
                    try (InputStream inputStream = resource.getInputStream()) {
                        content = new String(inputStream.readAllBytes());
                    }
                    
                    // classpath 资源无法获取修改时间，使用当前时间
                    lastModifiedTime = System.currentTimeMillis();
                } else {
                    throw new IllegalStateException("无效的classpath路径: " + resourcePath);
                }
                
            } else {
                // 从文件系统加载
                if (configFilePath == null || !Files.exists(configFilePath)) {
                    log.warn("配置文件不存在: {}", configFilePath);
                    currentConfig = new McpJsonConfig();
                    return currentConfig;
                }
                
                // 读取文件内容
                content = new String(Files.readAllBytes(configFilePath));
                
                // 更新修改时间
                lastModifiedTime = Files.getLastModifiedTime(configFilePath).toMillis();
            }
            
            // 解析JSON
            McpJsonConfig config = JSON.parseObject(content, McpJsonConfig.class);
            
            if (config == null) {
                log.warn("配置文件解析失败，使用空配置");
                config = new McpJsonConfig();
            }
            
            currentConfig = config;
            
            String configSource = isClasspathResource ? "classpath:config/mcp.json" : configFilePath.toString();
            log.info("成功加载MCP配置文件: {}，服务器数量: {}", 
                configSource, 
                config.getMcpServers() != null ? config.getMcpServers().size() : 0);
            
            return config;
            
        } catch (Exception e) {
            log.error("加载MCP配置文件失败: {}", configFilePath, e);
            currentConfig = new McpJsonConfig();
            return currentConfig;
        }
    }
    
    /**
     * 获取当前配置
     */
    public McpJsonConfig getCurrentConfig() {
        if (currentConfig == null) {
            synchronized (this) {
                if (currentConfig == null) {
                    loadConfig();
                }
            }
        }
        return currentConfig;
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        log.info("重新加载MCP配置文件");
        McpJsonConfig oldConfig = currentConfig;
        loadConfig();
        
        // 通知监听器
        if (oldConfig != currentConfig) {
            notifyConfigChangeListeners();
        }
    }
    
    /**
     * 启动文件监听器
     * 注意：只有外部文件系统路径才支持热加载，classpath 资源不支持
     */
    private void startFileWatcher() {
        if (isClasspathResource) {
            log.info("classpath 资源不支持热加载，跳过文件监听器");
            return;
        }
        
        if (configFilePath == null || !Files.exists(configFilePath)) {
            log.warn("配置文件不存在，无法启动文件监听: {}", configFilePath);
            return;
        }
        
        fileWatcherThread = new Thread(() -> {
            log.info("启动MCP配置文件监听器: {}（每2秒检查一次）", configFilePath);
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000); // 每2秒检查一次
                    
                    if (configFilePath != null && Files.exists(configFilePath)) {
                        long currentModifiedTime = Files.getLastModifiedTime(configFilePath).toMillis();
                        
                        if (currentModifiedTime > lastModifiedTime) {
                            log.info("检测到配置文件变更，重新加载...");
                            reloadConfig();
                        }
                    }
                } catch (InterruptedException e) {
                    log.info("文件监听器被中断");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("文件监听器异常", e);
                }
            }
        }, "MCP-Config-Watcher");
        
        fileWatcherThread.setDaemon(true);
        fileWatcherThread.start();
    }
    
    /**
     * 停止文件监听器
     */
    public void stopFileWatcher() {
        if (fileWatcherThread != null && fileWatcherThread.isAlive()) {
            fileWatcherThread.interrupt();
            log.info("停止MCP配置文件监听器");
        }
    }
    
    /**
     * 添加配置变更监听器
     */
    public void addConfigChangeListener(Runnable listener) {
        configChangeListeners.add(listener);
    }
    
    /**
     * 移除配置变更监听器
     */
    public void removeConfigChangeListener(Runnable listener) {
        configChangeListeners.remove(listener);
    }
    
    /**
     * 通知配置变更监听器
     */
    private void notifyConfigChangeListeners() {
        for (Runnable listener : configChangeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("配置变更监听器执行失败", e);
            }
        }
    }
    
    /**
     * 获取配置文件路径
     */
    public Path getConfigFilePath() {
        return configFilePath;
    }
}


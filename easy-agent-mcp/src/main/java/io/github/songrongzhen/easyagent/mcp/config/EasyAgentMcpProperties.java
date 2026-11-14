package io.github.songrongzhen.easyagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 服务、跨域及工具暴露策略配置。
 */
@ConfigurationProperties(prefix = "easy-agent.mcp")
public class EasyAgentMcpProperties {

    /**
     * 是否启用当前功能。
     */
    private boolean enabled = true;

    /**
     * MCP 服务的可读名称。
     */
    private String serverName = "easy-agent-mcp-server";

    /**
     * MCP 服务实现版本。
     */
    private String serverVersion = "0.2.0";

    /**
     * MCP 跨域传输配置。
     */
    private Cors cors = new Cors();

    /**
     * MCP 工具暴露策略配置。
     */
    private ToolExposure toolExposure = new ToolExposure();

    /**
     * MCP HTTP 跨域请求策略。
     */
    public static class Cors {

        /**
         * 是否启用当前功能。
         */
        private boolean enabled = true;

        /**
         * 允许的跨域来源模式。
         */
        private List<String> allowedOriginPatterns = new ArrayList<>(
            List.of("http://localhost:*", "http://127.0.0.1:*"));

        /**
         * 允许的跨域请求头。
         */
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /**
         * 允许的跨域 HTTP 方法。
         */
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "OPTIONS"));

        /**
         * 允许浏览器读取的响应头。
         */
        private List<String> exposedHeaders = new ArrayList<>(List.of("Content-Type"));

        /**
         * 是否允许跨域请求携带凭证。
         */
        private Boolean allowCredentials;

        /**
         * 查询是否启用当前功能。
         *
         * @return 当前是否启用当前功能
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用当前功能。
         *
         * @param enabled 是否启用当前功能
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 读取允许的跨域来源模式。
         *
         * @return 当前允许的跨域来源模式
         */
        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        /**
         * 设置允许的跨域来源模式。
         *
         * @param allowedOriginPatterns 允许的跨域来源模式
         */
        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        /**
         * 读取允许的跨域请求头。
         *
         * @return 当前允许的跨域请求头
         */
        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        /**
         * 设置允许的跨域请求头。
         *
         * @param allowedHeaders 允许的跨域请求头
         */
        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        /**
         * 读取允许的跨域 HTTP 方法。
         *
         * @return 当前允许的跨域 HTTP 方法
         */
        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        /**
         * 设置允许的跨域 HTTP 方法。
         *
         * @param allowedMethods 允许的跨域 HTTP 方法
         */
        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        /**
         * 读取允许浏览器读取的响应头。
         *
         * @return 当前允许浏览器读取的响应头
         */
        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        /**
         * 设置允许浏览器读取的响应头。
         *
         * @param exposedHeaders 允许浏览器读取的响应头
         */
        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        /**
         * 读取是否允许跨域请求携带凭证。
         *
         * @return 当前是否允许跨域请求携带凭证
         */
        public Boolean getAllowCredentials() {
            return allowCredentials;
        }

        /**
         * 设置是否允许跨域请求携带凭证。
         *
         * @param allowCredentials 是否允许跨域请求携带凭证
         */
        public void setAllowCredentials(Boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    /**
     * MCP 入口工具名称、来源及分类的允许和拒绝规则。
     */
    public static class ToolExposure {

        /**
         * 是否启用当前功能。
         */
        private boolean enabled = false;

        /**
         * 允许暴露的工具名称。
         */
        private List<String> allowedTools = new ArrayList<>();

        /**
         * 禁止暴露的工具名称。
         */
        private List<String> blockedTools = new ArrayList<>();

        /**
         * 允许暴露的工具来源。
         */
        private List<String> allowedSources = new ArrayList<>();

        /**
         * 禁止暴露的工具来源。
         */
        private List<String> blockedSources = new ArrayList<>();

        /**
         * 允许暴露的工具分类。
         */
        private List<String> allowedCategories = new ArrayList<>();

        /**
         * 禁止暴露的工具分类。
         */
        private List<String> blockedCategories = new ArrayList<>();

        /**
         * 查询是否启用当前功能。
         *
         * @return 当前是否启用当前功能
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用当前功能。
         *
         * @param enabled 是否启用当前功能
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 读取允许暴露的工具名称。
         *
         * @return 当前允许暴露的工具名称
         */
        public List<String> getAllowedTools() {
            return allowedTools;
        }

        /**
         * 设置允许暴露的工具名称。
         *
         * @param allowedTools 允许暴露的工具名称
         */
        public void setAllowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools;
        }

        /**
         * 读取禁止暴露的工具名称。
         *
         * @return 当前禁止暴露的工具名称
         */
        public List<String> getBlockedTools() {
            return blockedTools;
        }

        /**
         * 设置禁止暴露的工具名称。
         *
         * @param blockedTools 禁止暴露的工具名称
         */
        public void setBlockedTools(List<String> blockedTools) {
            this.blockedTools = blockedTools;
        }

        /**
         * 读取允许暴露的工具来源。
         *
         * @return 当前允许暴露的工具来源
         */
        public List<String> getAllowedSources() {
            return allowedSources;
        }

        /**
         * 设置允许暴露的工具来源。
         *
         * @param allowedSources 允许暴露的工具来源
         */
        public void setAllowedSources(List<String> allowedSources) {
            this.allowedSources = allowedSources;
        }

        /**
         * 读取禁止暴露的工具来源。
         *
         * @return 当前禁止暴露的工具来源
         */
        public List<String> getBlockedSources() {
            return blockedSources;
        }

        /**
         * 设置禁止暴露的工具来源。
         *
         * @param blockedSources 禁止暴露的工具来源
         */
        public void setBlockedSources(List<String> blockedSources) {
            this.blockedSources = blockedSources;
        }

        /**
         * 读取允许暴露的工具分类。
         *
         * @return 当前允许暴露的工具分类
         */
        public List<String> getAllowedCategories() {
            return allowedCategories;
        }

        /**
         * 设置允许暴露的工具分类。
         *
         * @param allowedCategories 允许暴露的工具分类
         */
        public void setAllowedCategories(List<String> allowedCategories) {
            this.allowedCategories = allowedCategories;
        }

        /**
         * 读取禁止暴露的工具分类。
         *
         * @return 当前禁止暴露的工具分类
         */
        public List<String> getBlockedCategories() {
            return blockedCategories;
        }

        /**
         * 设置禁止暴露的工具分类。
         *
         * @param blockedCategories 禁止暴露的工具分类
         */
        public void setBlockedCategories(List<String> blockedCategories) {
            this.blockedCategories = blockedCategories;
        }
    }

    /**
     * 查询是否启用当前功能。
     *
     * @return 当前是否启用当前功能
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用当前功能。
     *
     * @param enabled 是否启用当前功能
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 读取MCP 服务的可读名称。
     *
     * @return 当前MCP 服务的可读名称
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * 设置MCP 服务的可读名称。
     *
     * @param serverName MCP 服务的可读名称
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * 读取MCP 服务实现版本。
     *
     * @return 当前MCP 服务实现版本
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * 设置MCP 服务实现版本。
     *
     * @param serverVersion MCP 服务实现版本
     */
    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    /**
     * 读取MCP 跨域传输配置。
     *
     * @return 当前MCP 跨域传输配置
     */
    public Cors getCors() {
        return cors;
    }

    /**
     * 设置MCP 跨域传输配置。
     *
     * @param cors MCP 跨域传输配置
     */
    public void setCors(Cors cors) {
        this.cors = cors;
    }

    /**
     * 读取MCP 工具暴露策略配置。
     *
     * @return 当前MCP 工具暴露策略配置
     */
    public ToolExposure getToolExposure() {
        return toolExposure;
    }

    /**
     * 设置MCP 工具暴露策略配置。
     *
     * @param toolExposure MCP 工具暴露策略配置
     */
    public void setToolExposure(ToolExposure toolExposure) {
        this.toolExposure = toolExposure;
    }
}

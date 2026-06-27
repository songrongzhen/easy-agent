package io.github.songrongzhen.easyagent.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class McpCorsConfig {

    @Bean
    @ConditionalOnMissingBean(name = "mcpCorsFilter")
    @ConditionalOnProperty(prefix = "easy-agent.mcp.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorsFilter mcpCorsFilter(EasyAgentMcpProperties properties) {
        EasyAgentMcpProperties.Cors cors = properties.getCors();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(cors.getAllowedOriginPatterns());
        config.setAllowedHeaders(cors.getAllowedHeaders());
        config.setAllowedMethods(cors.getAllowedMethods());
        config.setExposedHeaders(cors.getExposedHeaders());
        config.setAllowCredentials(cors.getAllowCredentials());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/mcp/**", config);
        return new CorsFilter(source);
    }
}

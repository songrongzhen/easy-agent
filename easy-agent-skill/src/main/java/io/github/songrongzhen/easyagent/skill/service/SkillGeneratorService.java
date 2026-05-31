package io.github.songrongzhen.easyagent.skill.service;

import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.skill.config.EasyAgentSkillProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SkillGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SkillGeneratorService.class);

    private final ToolRegistry toolRegistry;
    private final EasyAgentSkillProperties properties;

    public SkillGeneratorService(ToolRegistry toolRegistry, EasyAgentSkillProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public record ToolInfo(
            String name,
            String description,
            List<ParameterInfo> parameters
    ) {
        public record ParameterInfo(
                String name,
                String type,
                String description,
                boolean required
        ) {}
    }

    public List<ToolInfo> getAllAvailableTools() {
        Collection<ToolDefinition> tools = toolRegistry.getEnabledTools();
        return tools.stream()
                .map(tool -> new ToolInfo(
                        tool.name(),
                        tool.description(),
                        tool.parameters().stream()
                                .map(param -> new ToolInfo.ParameterInfo(
                                        param.name(),
                                        param.type(),
                                        param.description(),
                                        param.required()
                                ))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
    }

    public String getToolListForUser() {
        List<ToolInfo> tools = getAllAvailableTools();
        if (tools.isEmpty()) {
            return "当前项目中没有找到任何 @EasyTool 工具。\n\n请先在代码中使用 @EasyTool 注解定义工具方法。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前项目中发现的 @EasyTool 工具列表：\n\n");
        
        for (int i = 0; i < tools.size(); i++) {
            ToolInfo tool = tools.get(i);
            sb.append(i + 1).append(". **").append(tool.name()).append("**\n");
            sb.append("   - 描述：").append(tool.description()).append("\n");
            if (!tool.parameters().isEmpty()) {
                sb.append("   - 参数：\n");
                for (ToolInfo.ParameterInfo param : tool.parameters()) {
                    String required = param.required() ? "必需" : "可选";
                    sb.append("     - ").append(param.name())
                      .append(" (").append(param.type()).append(", ").append(required).append(")")
                      .append("：").append(param.description()).append("\n");
                }
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    public void generateSkill(SkillInput input) throws IOException {
        String outputPath = properties.getSkillOutputPath();
        Path skillDir = Paths.get(outputPath, "skill");
        
        if (!Files.exists(skillDir)) {
            Files.createDirectories(skillDir);
        }
        
        Path path = skillDir.resolve(input.name() + ".md");
        String content = buildSkillMarkdown(input);
        Files.writeString(path, content);
        
        log.info("Generated skill file: {}", path.toAbsolutePath());
    }

    public void generateSkill(String name, String description, String boundary, 
                              List<String> selectedTools, String example, String author) throws IOException {
        generateSkill(new SkillInput(name, description, boundary, selectedTools, example, author));
    }

    private String buildSkillMarkdown(SkillInput input) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("<!--\n");
        sb.append("name: ").append(input.name()).append("\n");
        sb.append("version: 1.0.0\n");
        sb.append("description: ").append(input.description()).append("\n");
        sb.append("author: ").append(input.author() != null ? input.author() : "auto-generated").append("\n");
        sb.append("required_tools: ").append(String.join(", ", input.selectedTools())).append("\n");
        sb.append("-->\n\n");
        
        sb.append("# ").append(input.name()).append("\n\n");
        
        sb.append("## 简介\n");
        sb.append(input.description()).append("\n\n");
        
        sb.append("## 使用边界\n\n");
        sb.append(input.boundary()).append("\n\n");
        
        sb.append("## 可用工具\n\n");
        sb.append("| 工具名称 | 描述 |\n");
        sb.append("|----------|------|\n");
        
        for (String toolName : input.selectedTools()) {
            ToolDefinition tool = toolRegistry.getTool(toolName);
            if (tool != null) {
                sb.append("| ").append(tool.name())
                  .append(" | ").append(tool.description()).append(" |\n");
            } else {
                sb.append("| ").append(toolName).append(" | (工具未找到) |\n");
            }
        }
        sb.append("\n");
        
        sb.append("## 使用示例\n\n");
        sb.append(input.example()).append("\n");
        
        return sb.toString();
    }

    public record SkillInput(
            String name,
            String description,
            String boundary,
            List<String> selectedTools,
            String example,
            String author
    ) {}
}

package io.github.songrongzhen.easyagent.skill.service;

import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.skill.config.EasyAgentSkillProperties;
import io.github.songrongzhen.easyagent.skill.provider.SkillToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 展示可用工具契约并按既有策略生成 Skill 文档。
 */
@Service
public class SkillGeneratorService {

    /**
     * 当前组件的日志记录器。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillGeneratorService.class);

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 组件配置。
     */
    private final EasyAgentSkillProperties properties;

    /**
     * 创建 SkillGeneratorService，保存调用所需的显式依赖。
     *
     * @param toolRegistry 工具注册表
     * @param properties   组件配置
     */
    public SkillGeneratorService(ToolRegistry toolRegistry, EasyAgentSkillProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    /**
     * 面向使用者展示的工具描述，不包含运行时代理元数据。
     *
     * @param name        工具名称
     * @param description 工具的可读用途说明
     * @param parameters  工具参数描述或绑定列表
     */
    public record ToolInfo(String name, String description, List<ParameterInfo> parameters) {

        /**
         * 面向使用者展示的解析后参数类型及描述。
         *
         * @param name        工具名称
         * @param type        参数或协议内容的类型标识
         * @param description 工具的可读用途说明
         * @param required    是否要求存在宿主授权策略
         */
        public record ParameterInfo(String name, String type, String description, boolean required) {
        }
    }

    /**
     * 生成 Skill 时处理同名文件的策略。
     */
    public enum FileExistsStrategy {

        /**
         * 已有文件时要求调用方选择处理策略。
         */
        ASK,
        /**
         * 保留原文件并生成副本。
         */
        COPY,
        /**
         * 覆盖已有同名文件。
         */
        OVERWRITE,
        /**
         * 已有同名文件时拒绝生成。
         */
        ERROR
    }

    /**
     * 获取当前可用工具列表。
     */
    public List<ToolInfo> getAllAvailableTools() {
        Collection<ToolDefinition> tools = toolRegistry.getEnabledTools();
        return tools.stream().filter(tool -> !SkillToolProvider.CATEGORY.equals(tool.category()))
            .filter(tool -> !SkillToolProvider.SOURCE.equals(tool.source()))
            .map(tool -> new ToolInfo(tool.name(), tool.description(),
                tool.parameters().stream()
                    .map(param -> new ToolInfo.ParameterInfo(param.name(), resolveParameterType(tool.name(), param),
                        param.description(), param.required()))
                    .collect(Collectors.toList())))
            .collect(Collectors.toList());
    }

    /**
     * 优先展示注册时解析出的完整类型，兼容保留旧描述中的展示类型。
     */
    private String resolveParameterType(String toolName, ParameterDefinition parameter) {
        return toolRegistry.getRegisteredTool(toolName).parameters().stream()
            .filter(binding -> binding.name().equals(parameter.name())).findFirst()
            .map(binding -> binding.javaType().toCanonical()).orElse(parameter.type());
    }

    /**
     * 获取面向使用者展示的工具列表文本。
     */
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
                    sb.append("     - ").append(param.name()).append(" (").append(param.type()).append(", ")
                        .append(required).append(")").append("：").append(param.description()).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成业务 Skill Markdown 文件。
     */
    public void generateSkill(SkillInput input) throws IOException {
        generateSkill(input, null);
    }

    /**
     * 按同名文件处理策略生成业务 Skill Markdown 文件。
     */
    public void generateSkill(SkillInput input, FileExistsStrategy fileExistsStrategy) throws IOException {
        validateSkillInput(input);
        String outputPath = properties.getSkillOutputPath();
        Path skillDir = Paths.get(outputPath, "skill").toAbsolutePath().normalize();
        if (!Files.exists(skillDir)) {
            Files.createDirectories(skillDir);
        }
        String filename = sanitizeFilename(input.name()) + ".md";
        Path path = skillDir.resolve(filename).normalize();
        if (!path.startsWith(skillDir)) {
            throw new IllegalArgumentException("Skill file path is outside of skill output directory");
        }
        path = resolveTargetPath(path, fileExistsStrategy != null ? fileExistsStrategy : FileExistsStrategy.ASK);
        String content = buildSkillMarkdown(input);
        Files.writeString(path, content);
        LOGGER.info("Generated skill file: {}", path.toAbsolutePath());
    }

    /**
     * 生成业务 Skill Markdown 文件。
     */
    public void generateSkill(String name, String description, String boundary, List<String> selectedTools,
        String example, String author) throws IOException {
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
                sb.append("| ").append(tool.name()).append(" | ").append(tool.description()).append(" |\n");
            } else {
                sb.append("| ").append(toolName).append(" | (工具未找到) |\n");
            }
        }
        sb.append("\n");
        sb.append("## 使用示例\n\n");
        sb.append(input.example()).append("\n");
        return sb.toString();
    }

    private void validateSkillInput(SkillInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Skill input is required");
        }
        if (isBlank(input.name())) {
            throw new IllegalArgumentException("Skill name is required");
        }
        if (isBlank(input.description())) {
            throw new IllegalArgumentException("Skill description is required");
        }
        if (isBlank(input.boundary())) {
            throw new IllegalArgumentException("Skill boundary is required");
        }
        if (isBlank(input.example())) {
            throw new IllegalArgumentException("Skill example is required");
        }
        if (input.selectedTools() == null || input.selectedTools().isEmpty()) {
            throw new IllegalArgumentException("selectedTools must not be empty");
        }
        Set<String> availableToolNames = toolRegistry.getEnabledTools().stream().map(ToolDefinition::name)
            .collect(Collectors.toSet());
        List<String> missingTools = input.selectedTools().stream().filter(this::isBlank).collect(Collectors.toList());
        if (!missingTools.isEmpty()) {
            throw new IllegalArgumentException("selectedTools must not contain blank tool names");
        }
        missingTools = input.selectedTools().stream().filter(toolName -> !availableToolNames.contains(toolName))
            .collect(Collectors.toList());
        if (!missingTools.isEmpty()) {
            throw new IllegalArgumentException("Unknown selectedTools: " + String.join(", ", missingTools));
        }
    }

    private String sanitizeFilename(String name) {
        String sanitized = name.trim().replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", "-")
            .replaceAll("\\.+", ".").replaceAll("^-+", "").replaceAll("-+$", "");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            throw new IllegalArgumentException("Skill name cannot be used as a file name");
        }
        return sanitized;
    }

    private Path resolveTargetPath(Path path, FileExistsStrategy strategy) throws IOException {
        if (!Files.exists(path)) {
            return path;
        }
        return switch (strategy) {
        case OVERWRITE ->
            path;
        case COPY ->
            resolveCopyPath(path);
        case ERROR ->
            throw new IllegalStateException("Skill file already exists: " + path.getFileName());
        case ASK ->
            throw new SkillFileAlreadyExistsException(path.getFileName().toString());
        };
    }

    private Path resolveCopyPath(Path path) {
        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        String basename = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String extension = dotIndex > 0 ? filename.substring(dotIndex) : "";
        Path parent = path.getParent();
        int copyIndex = 1;
        Path copyPath;
        do {
            copyPath = parent.resolve(basename + "-copy-" + copyIndex + extension).normalize();
            copyIndex++;
        } while (Files.exists(copyPath));
        return copyPath;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Skill 文件已存在且尚未选择处理策略时的异常。
     */
    public static class SkillFileAlreadyExistsException extends IOException {

        /**
         * 记录发生冲突的 Skill 文件名称。
         *
         * @param filename 发生冲突的文件名称
         */
        public SkillFileAlreadyExistsException(String filename) {
            super("Skill file already exists: " + filename);
        }
    }

    /**
     * 生成 Skill 文档所需的业务输入。
     *
     * @param name          工具名称
     * @param description   工具的可读用途说明
     * @param boundary      Skill 使用边界和限制
     * @param selectedTools Skill 选用的工具名称列表
     * @param example       Skill 的调用示例
     * @param author        生成 Skill 文档时使用的作者标识
     */
    public record SkillInput(String name, String description, String boundary, List<String> selectedTools,
        String example, String author) {
    }
}

package io.github.songrongzhen.easyagent.mcp.adapter;

import io.github.songrongzhen.easyagent.mcp.protocol.McpProtocol;

import io.github.songrongzhen.easyagent.skill.service.SkillGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class SkillMcpAdapter {

    private static final Logger log = LoggerFactory.getLogger(SkillMcpAdapter.class);

    private final SkillGeneratorService skillGeneratorService;

    public SkillMcpAdapter(SkillGeneratorService skillGeneratorService) {
        this.skillGeneratorService = skillGeneratorService;
    }

    public List<McpProtocol.Tool> getSkillTools() {
        List<McpProtocol.Tool> tools = new ArrayList<>();
        
        tools.add(createListToolsTool());
        tools.add(createGenerateSkillTool());
        
        return tools;
    }

    private McpProtocol.Tool createListToolsTool() {
        Map<String, McpProtocol.PropertyDef> properties = new LinkedHashMap<>();
        McpProtocol.JsonSchema schema = new McpProtocol.JsonSchema("object", properties, List.of());
        return new McpProtocol.Tool(
                "skill.list_tools",
                "skill元技能获取项目中所有可用的 @EasyTool 工具列表，用于创建 Skill 时选择需要调用的工具",
                schema
        );
    }

    private McpProtocol.Tool createGenerateSkillTool() {
        Map<String, McpProtocol.PropertyDef> properties = new LinkedHashMap<>();
        properties.put("name", new McpProtocol.PropertyDef("string", "Skill 的名称"));
        properties.put("description", new McpProtocol.PropertyDef("string", "Skill 的功能描述"));
        properties.put("boundary", new McpProtocol.PropertyDef("string", "Skill 的使用边界和限制"));
        properties.put("selectedTools", new McpProtocol.PropertyDef("array", "选择的工具名称列表"));
        properties.put("example", new McpProtocol.PropertyDef("string", "使用示例"));
        properties.put("fileExistsStrategy", new McpProtocol.PropertyDef("string", "同名文件已存在时的处理策略：copy 或 overwrite"));
        
        List<String> required = List.of("name", "description", "boundary", "selectedTools", "example");
        McpProtocol.JsonSchema schema = new McpProtocol.JsonSchema("object", properties, required);
        return new McpProtocol.Tool(
                "skill.generate",
                "生成业务 Skill Markdown 文件到 skill/{name}.md",
                schema
        );
    }

    public McpProtocol.CallToolResult executeSkillTool(String toolName, Map<String, Object> arguments) {
        if (toolName == null || !toolName.startsWith("skill.")) {
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Unknown skill tool: " + toolName)),
                    true
            );
        }

        try {
            return switch (toolName) {
                case "skill.list_tools" -> handleListTools();
                case "skill.generate" -> handleGenerateSkill(arguments);
                default -> new McpProtocol.CallToolResult(
                        List.of(McpProtocol.Content.text("Unknown skill tool: " + toolName)),
                        true
                );
            };
        } catch (Exception e) {
            log.error("Failed to execute skill tool: {}", toolName, e);
            return new McpProtocol.CallToolResult(
                    List.of(McpProtocol.Content.text("Error: " + e.getMessage())),
                    true
            );
        }
    }

    private McpProtocol.CallToolResult handleListTools() {
        String toolList = skillGeneratorService.getToolListForUser();
        return new McpProtocol.CallToolResult(
                List.of(McpProtocol.Content.text(toolList)),
                false
        );
    }

    @SuppressWarnings("unchecked")
    private McpProtocol.CallToolResult handleGenerateSkill(Map<String, Object> arguments) throws IOException {
        if (arguments == null) {
            return error("Error: arguments are required");
        }

        String name = getRequiredString(arguments, "name");
        String description = getRequiredString(arguments, "description");
        String boundary = getRequiredString(arguments, "boundary");
        String example = getRequiredString(arguments, "example");
        if (name == null || description == null || boundary == null || example == null) {
            return error("Error: name, description, boundary and example must be non-empty strings");
        }
        
        List<String> selectedTools;
        Object toolsObj = arguments.get("selectedTools");
        if (toolsObj instanceof List<?> toolsList) {
            if (!toolsList.stream().allMatch(String.class::isInstance)) {
                return error("Error: selectedTools must be an array of strings");
            }
            selectedTools = (List<String>) toolsList;
        } else if (toolsObj instanceof String) {
            selectedTools = List.of((String) toolsObj);
        } else {
            return error("Error: selectedTools must be an array of strings");
        }

        SkillGeneratorService.FileExistsStrategy fileExistsStrategy = resolveFileExistsStrategy(arguments.get("fileExistsStrategy"));
        SkillGeneratorService.SkillInput input = new SkillGeneratorService.SkillInput(name, description, boundary, selectedTools, example, null);
        
        try {
            skillGeneratorService.generateSkill(input, fileExistsStrategy);
        } catch (SkillGeneratorService.SkillFileAlreadyExistsException e) {
            return error("Skill 文件已存在：" + e.getMessage()
                    + "。请询问使用者是生成副本还是覆盖；生成副本时重新调用 skill.generate 并传 fileExistsStrategy=copy，覆盖时传 fileExistsStrategy=overwrite。");
        }
        
        return new McpProtocol.CallToolResult(
                List.of(McpProtocol.Content.text("Skill 文件已生成到 skill/" + name + ".md")),
                false
        );
    }

    private String getRequiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }

    private SkillGeneratorService.FileExistsStrategy resolveFileExistsStrategy(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("fileExistsStrategy must be copy or overwrite");
        }
        return switch (text.trim().toLowerCase()) {
            case "copy" -> SkillGeneratorService.FileExistsStrategy.COPY;
            case "overwrite" -> SkillGeneratorService.FileExistsStrategy.OVERWRITE;
            default -> throw new IllegalArgumentException("fileExistsStrategy must be copy or overwrite");
        };
    }

    private McpProtocol.CallToolResult error(String message) {
        return new McpProtocol.CallToolResult(
                List.of(McpProtocol.Content.text(message)),
                true
        );
    }
}

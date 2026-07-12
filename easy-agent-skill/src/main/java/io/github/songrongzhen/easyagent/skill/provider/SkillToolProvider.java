package io.github.songrongzhen.easyagent.skill.provider;

import io.github.songrongzhen.easyagent.core.model.ParameterDefinition;
import io.github.songrongzhen.easyagent.core.model.ToolDefinition;
import io.github.songrongzhen.easyagent.core.spi.ToolProvider;

import java.util.Collection;
import java.util.List;

public class SkillToolProvider implements ToolProvider {

    public static final String SOURCE = "skill";
    public static final String CATEGORY = "skill";
    public static final String SKILL_TOOL_BEAN_NAME = "skillToolService";

    /**
     * 返回 Skill 模块提供的元工具定义。
     */
    @Override
    public Collection<ToolDefinition> provide() {
        return List.of(listToolsDefinition(), generateDefinition());
    }

    /**
     * 返回工具来源名称。
     */
    @Override
    public String source() {
        return SOURCE;
    }

    private ToolDefinition listToolsDefinition() {
        return new ToolDefinition(
                "skill.list_tools",
                "skill元技能获取项目中所有可用的 @EasyTool 工具列表，用于创建 Skill 时选择需要调用的工具",
                CATEGORY,
                SOURCE,
                priority(),
                SKILL_TOOL_BEAN_NAME,
                "listTools",
                List.of(),
                version(),
                true
        );
    }

    private ToolDefinition generateDefinition() {
        return new ToolDefinition(
                "skill.generate",
                "生成业务 Skill Markdown 文件到 skill/{name}.md",
                CATEGORY,
                SOURCE,
                priority(),
                SKILL_TOOL_BEAN_NAME,
                "generate",
                List.of(
                        new ParameterDefinition("name", "Skill 的名称", "String", true),
                        new ParameterDefinition("description", "Skill 的功能描述", "String", true),
                        new ParameterDefinition("boundary", "Skill 的使用边界和限制", "String", true),
                        new ParameterDefinition("selectedTools", "选择的工具名称列表", "array", true),
                        new ParameterDefinition("example", "使用示例", "String", true),
                        new ParameterDefinition("fileExistsStrategy", "同名文件已存在时的处理策略：copy 或 overwrite", "String", false)
                ),
                version(),
                true
        );
    }
}

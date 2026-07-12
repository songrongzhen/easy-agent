package io.github.songrongzhen.easyagent.skill.service;

import java.io.IOException;
import java.util.List;

public class SkillToolService {

    private final SkillGeneratorService skillGeneratorService;

    public SkillToolService(SkillGeneratorService skillGeneratorService) {
        this.skillGeneratorService = skillGeneratorService;
    }

    /**
     * 获取当前项目中可用于生成 Skill 的工具列表。
     */
    public String listTools() {
        return skillGeneratorService.getToolListForUser();
    }

    /**
     * 生成业务 Skill Markdown 文件。
     */
    public String generate(String name,
                           String description,
                           String boundary,
                           Object selectedTools,
                           String example,
                           String fileExistsStrategy) throws IOException {
        List<String> normalizedTools = normalizeSelectedTools(selectedTools);
        SkillGeneratorService.FileExistsStrategy strategy = resolveFileExistsStrategy(fileExistsStrategy);
        SkillGeneratorService.SkillInput input = new SkillGeneratorService.SkillInput(
                name,
                description,
                boundary,
                normalizedTools,
                example,
                null
        );

        try {
            skillGeneratorService.generateSkill(input, strategy);
        } catch (SkillGeneratorService.SkillFileAlreadyExistsException e) {
            throw new IllegalStateException("Skill 文件已存在：" + e.getMessage()
                    + "。请询问使用者是生成副本还是覆盖；生成副本时重新调用 skill.generate 并传 fileExistsStrategy=copy，覆盖时传 fileExistsStrategy=overwrite。");
        }

        return "Skill 文件已生成到 skill/" + name + ".md";
    }

    private List<String> normalizeSelectedTools(Object value) {
        if (value instanceof List<?> values) {
            if (!values.stream().allMatch(String.class::isInstance)) {
                throw new IllegalArgumentException("selectedTools 必须是字符串数组");
            }
            return values.stream().map(String.class::cast).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        throw new IllegalArgumentException("selectedTools 必须是字符串数组");
    }

    private SkillGeneratorService.FileExistsStrategy resolveFileExistsStrategy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "copy" -> SkillGeneratorService.FileExistsStrategy.COPY;
            case "overwrite" -> SkillGeneratorService.FileExistsStrategy.OVERWRITE;
            default -> throw new IllegalArgumentException("fileExistsStrategy 必须是 copy 或 overwrite");
        };
    }
}

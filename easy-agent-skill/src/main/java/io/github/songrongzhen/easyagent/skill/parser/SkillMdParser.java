package io.github.songrongzhen.easyagent.skill.parser;

import io.github.songrongzhen.easyagent.skill.model.SkillDefinition;
import io.github.songrongzhen.easyagent.skill.model.SkillStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkillMdParser {

    private static final Logger log = LoggerFactory.getLogger(SkillMdParser.class);

    public static SkillDefinition parse(String content, String sourcePath) {
        String name = extractHeaderValue(content, "name");
        String version = extractHeaderValue(content, "version");
        String description = extractHeaderValue(content, "description");
        String author = extractHeaderValue(content, "author");
        List<String> tags = extractListValue(content, "tags");
        List<String> requiredTools = extractListValue(content, "required_tools");
        List<SkillStep> steps = parseSteps(content);

        return new SkillDefinition(
                name,
                version,
                description,
                author,
                tags,
                requiredTools,
                steps,
                new HashMap<>(),
                content,
                sourcePath
        );
    }

    private static String extractHeaderValue(String content, String key) {
        String pattern = "<!-- " + key + ":";
        int start = content.indexOf(pattern);
        if (start == -1) {
            pattern = "<!-- " + key + " :";
            start = content.indexOf(pattern);
        }
        if (start == -1) {
            return "";
        }
        int valueStart = content.indexOf(":", start) + 1;
        int valueEnd = content.indexOf("-->", valueStart);
        if (valueEnd == -1) {
            return "";
        }
        return content.substring(valueStart, valueEnd).trim();
    }

    private static List<String> extractListValue(String content, String key) {
        String value = extractHeaderValue(content, key);
        if (value.isEmpty()) {
            return List.of();
        }
        return List.of(value.split(",\\s*"));
    }

    private static List<SkillStep> parseSteps(String content) {
        List<SkillStep> steps = new ArrayList<>();
        String[] lines = content.split("\n");
        int stepOrder = 0;
        boolean inStepSection = false;
        String currentStepName = null;
        String currentStepDesc = null;
        String currentStepAction = null;
        String currentStepTool = null;
        String currentStepInput = null;
        String currentStepOutput = null;
        String currentStepCondition = null;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("## Steps") || trimmed.startsWith("## steps")) {
                inStepSection = true;
                continue;
            }

            if (inStepSection && trimmed.startsWith("## ") && !trimmed.contains("Step")) {
                if (currentStepName != null) {
                    steps.add(new SkillStep(
                            stepOrder++, currentStepName, currentStepDesc,
                            currentStepAction, currentStepTool, currentStepInput,
                            currentStepOutput, currentStepCondition
                    ));
                }
                inStepSection = false;
                continue;
            }

            if (inStepSection && trimmed.startsWith("### ")) {
                if (currentStepName != null) {
                    steps.add(new SkillStep(
                            stepOrder++, currentStepName, currentStepDesc,
                            currentStepAction, currentStepTool, currentStepInput,
                            currentStepOutput, currentStepCondition
                    ));
                }
                currentStepName = trimmed.substring(4).trim();
                currentStepDesc = null;
                currentStepAction = null;
                currentStepTool = null;
                currentStepInput = null;
                currentStepOutput = null;
                currentStepCondition = null;
                continue;
            }

            if (currentStepName != null) {
                if (trimmed.startsWith("- **Description**:") || trimmed.startsWith("- **description**:")) {
                    currentStepDesc = extractField(trimmed);
                } else if (trimmed.startsWith("- **Action**:") || trimmed.startsWith("- **action**:")) {
                    currentStepAction = extractField(trimmed);
                } else if (trimmed.startsWith("- **Tool**:") || trimmed.startsWith("- **tool**:")) {
                    currentStepTool = extractField(trimmed);
                } else if (trimmed.startsWith("- **Input**:") || trimmed.startsWith("- **input**:")) {
                    currentStepInput = extractField(trimmed);
                } else if (trimmed.startsWith("- **Output**:") || trimmed.startsWith("- **output**:")) {
                    currentStepOutput = extractField(trimmed);
                } else if (trimmed.startsWith("- **Condition**:") || trimmed.startsWith("- **condition**:")) {
                    currentStepCondition = extractField(trimmed);
                }
            }
        }

        if (currentStepName != null) {
            steps.add(new SkillStep(
                    stepOrder, currentStepName, currentStepDesc,
                    currentStepAction, currentStepTool, currentStepInput,
                    currentStepOutput, currentStepCondition
            ));
        }

        return steps;
    }

    private static String extractField(String line) {
        int colonIndex = line.indexOf("**:");
        if (colonIndex == -1) {
            return line;
        }
        return line.substring(colonIndex + 3).trim();
    }
}

package io.github.songrongzhen.easyagent.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.songrongzhen.easyagent.core.executor.ToolExecutor;
import io.github.songrongzhen.easyagent.core.model.ToolInvocation;
import io.github.songrongzhen.easyagent.core.processor.ToolProviderRegistrar;
import io.github.songrongzhen.easyagent.core.registry.ToolRegistry;
import io.github.songrongzhen.easyagent.skill.config.EasyAgentSkillProperties;
import io.github.songrongzhen.easyagent.skill.provider.SkillToolProvider;
import io.github.songrongzhen.easyagent.skill.service.SkillGeneratorService;
import io.github.songrongzhen.easyagent.skill.service.SkillToolService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 工具执行契约的测试夹具与回归验证。 */
class SkillToolCompatibilityTest {

    @TempDir
    Path directory;

    @Test
    void selectedToolsStillAcceptsStringAndList() throws Exception {
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var registry = new ToolRegistry();
            var executor = new ToolExecutor(registry, context);
            var props = new EasyAgentSkillProperties();
            props.setSkillOutputPath(directory.toString());
            var service = new SkillToolService(new SkillGeneratorService(registry, props));
            context.getBeanFactory().registerSingleton("skillToolService", service);
            new ToolProviderRegistrar(registry, List.of(new SkillToolProvider()))
                .registerProviders();
            var schema = new ObjectMapper()
                .valueToTree(
                    registry.getRegisteredTool("skill.generate").inputSchema());
            assertFalse(schema.at("/properties/selectedTools").has("type"));
            for (Object selected : List.of("skill.list_tools", List.of("skill.list_tools"))) {
                String name = selected instanceof String ? "single" : "list";
                String json = new ObjectMapper()
                    .writeValueAsString(
                        Map.of(
                            "name",
                            name,
                            "description",
                            "desc",
                            "boundary",
                            "boundary",
                            "selectedTools",
                            selected,
                            "example",
                            "example"));
                var result = executor.execute(new ToolInvocation("skill.generate", json));
                assertTrue(result.success(), result.error());
                assertTrue(Files.exists(directory.resolve("skill/" + name + ".md")));
            }
        }
    }
}

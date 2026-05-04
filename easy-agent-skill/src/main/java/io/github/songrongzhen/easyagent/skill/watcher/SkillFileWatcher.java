package io.github.songrongzhen.easyagent.skill.watcher;

import io.github.songrongzhen.easyagent.skill.config.EasyAgentSkillProperties;
import io.github.songrongzhen.easyagent.skill.service.SkillLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SkillFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(SkillFileWatcher.class);

    private final EasyAgentSkillProperties properties;
    private final SkillLoaderService skillLoaderService;
    private WatchService watchService;
    private ExecutorService executorService;
    private volatile boolean running = false;

    public SkillFileWatcher(EasyAgentSkillProperties properties, SkillLoaderService skillLoaderService) {
        this.properties = properties;
        this.skillLoaderService = skillLoaderService;
    }

    public void start() {
        if (!properties.isHotReload()) {
            log.info("Skill hot-reload is disabled");
            return;
        }

        try {
            String skillPath = properties.getSkillPath();
            if (skillPath.startsWith("classpath:")) {
                log.info("Hot-reload is not supported for classpath resources, use file: prefix for hot-reload");
                return;
            }

            Path path = Paths.get(skillPath.replace("file:", ""));
            if (!Files.exists(path)) {
                log.warn("Skill directory does not exist: {}", path);
                return;
            }

            watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);

            running = true;
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "skill-file-watcher");
                t.setDaemon(true);
                return t;
            });

            executorService.submit(this::watchLoop);
            log.info("Skill file watcher started for path: {}", path);
        } catch (Exception e) {
            log.error("Failed to start skill file watcher", e);
        }
    }

    public void stop() {
        running = false;
        try {
            if (watchService != null) {
                watchService.close();
            }
            if (executorService != null) {
                executorService.shutdown();
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Failed to stop skill file watcher", e);
        }
    }

    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.poll(properties.getWatchInterval(), TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changedFile = (Path) event.context();
                    if (changedFile.toString().endsWith(".md")) {
                        log.info("Skill file changed: {} - {}", event.kind(), changedFile);
                        Thread.sleep(500);
                        skillLoaderService.reloadAllSkills();
                    }
                }

                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in skill file watcher", e);
            }
        }
    }
}

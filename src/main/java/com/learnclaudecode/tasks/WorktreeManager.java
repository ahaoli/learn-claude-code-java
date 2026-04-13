package com.learnclaudecode.tasks;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.common.WorkspacePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * worktree 管理器，对齐 s12 的目录隔离语义。
 */
public class WorktreeManager {
    private final WorkspacePaths paths;
    private final TaskManager taskManager;
    private final Path indexPath;
    private final Path eventsPath;

    /**
     * 初始化 worktree 管理器。
     *
     * @param paths 工作区路径工具
     * @param taskManager 任务管理器
     */
    public WorktreeManager(WorkspacePaths paths, TaskManager taskManager) {
        this.paths = paths;
        this.taskManager = taskManager;
        this.indexPath = paths.worktreesDir().resolve("index.json");
        this.eventsPath = paths.worktreesDir().resolve("events.jsonl");
        try {
            Files.createDirectories(paths.worktreesDir());
            if (!Files.exists(indexPath)) {
                Files.writeString(indexPath, JsonUtils.toPrettyJson(Map.of("worktrees", new ArrayList<>())), StandardCharsets.UTF_8);
            }
            if (!Files.exists(eventsPath)) {
                Files.writeString(eventsPath, "", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("初始化 worktree 目录失败", e);
        }
    }

    /**
     * 创建并绑定一个新的 worktree lane。
     *
     * @param name worktree 名称
     * @param taskId 关联任务 ID
     * @return worktree 信息
     */
    public synchronized String create(String name, int taskId) {
        Path worktreePath = paths.worktreesDir().resolve(name);
        try {
            Files.createDirectories(worktreePath);
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
        // 当前实现用独立目录模拟 worktree lane，并把它与任务 ID 绑定。
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("path", worktreePath.toString());
        item.put("branch", "wt/" + name);
        item.put("task_id", taskId);
        item.put("status", "active");
        List<Map<String, Object>> items = worktrees();
        items.add(item);
        saveIndex(items);
        // task -> worktree name -> items(indexPath)
        taskManager.bindWorktree(taskId, name, "");
        emit("worktree_created", taskId, item, null);
        return JsonUtils.toPrettyJson(item);
    }

    /**
     * 列出所有已记录的 worktree。
     *
     * @return worktree 列表 JSON
     */
    public synchronized String list() {
        return JsonUtils.toPrettyJson(Map.of("worktrees", worktrees()));
    }

    /**
     * 移除指定 worktree。
     *
     * @param name worktree 名称
     * @param keep 是否保留目录文件
     * @return 移除结果
     */
    public synchronized String remove(String name, boolean keep) {
        List<Map<String, Object>> items = worktrees();
        Map<String, Object> target = null;
        for (Map<String, Object> item : items) {
            if (name.equals(item.get("name"))) {
                target = item;
                break;
            }
        }
        if (target == null) {
            return "Error: Unknown worktree '" + name + "'";
        }
        if (!keep) {
            try {
                // 删除时按深度逆序遍历，确保先删文件后删目录。
                Path path = Path.of(String.valueOf(target.get("path")));
                if (Files.exists(path)) {
                    Files.walk(path)
                            .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            } catch (IOException ignored) {
            }
        }
        items.remove(target);
        saveIndex(items);
        Number taskId = (Number) target.get("task_id");
        if (taskId != null) {
            taskManager.unbindWorktree(taskId.intValue());
        }
        emit("worktree_removed", taskId == null ? -1 : taskId.intValue(), target, null);
        return "Removed worktree '" + name + "'" + (keep ? " (kept files)" : "");
    }

    /**
     * 读取最近的 worktree 生命周期事件。
     *
     * @param limit 返回条数上限
     * @return 事件 JSON 数组文本
     */
    public synchronized String recentEvents(int limit) {
        try {
            List<String> lines = Files.readAllLines(eventsPath, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - Math.max(1, Math.min(limit, 200)));
            return "[\n" + String.join(",\n", lines.subList(from, lines.size())) + "\n]";
        } catch (IOException e) {
            return "[]";
        }
    }

    /**
     * 从索引文件读取当前 worktree 列表。
     *
     * @return worktree 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> worktrees() {
        try {
            Map<String, Object> data = JsonUtils.fromJson(Files.readString(indexPath, StandardCharsets.UTF_8), Map.class);
            return (List<Map<String, Object>>) data.computeIfAbsent("worktrees", key -> new ArrayList<>());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * 保存 worktree 索引。
     *
     * @param items worktree 列表
     */
    private void saveIndex(List<Map<String, Object>> items) {
        try {
            Files.writeString(indexPath, JsonUtils.toPrettyJson(Map.of("worktrees", items)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("保存 worktree 索引失败", e);
        }
    }

    /**
     * 追加一条 worktree 生命周期事件。
     *
     * @param event 事件名
     * @param taskId 关联任务 ID
     * @param worktree worktree 信息
     * @param error 可选错误信息
     */
    private void emit(String event, int taskId, Map<String, Object> worktree, String error) {
        // 所有 worktree 事件都追加到 events.jsonl，方便排查隔离任务的生命周期。
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("ts", Instant.now().getEpochSecond());
        payload.put("task", Map.of("id", taskId));
        payload.put("worktree", worktree == null ? Map.of() : worktree);
        if (error != null && !error.isBlank()) {
            payload.put("error", error);
        }
        try {
            Files.writeString(eventsPath, JsonUtils.toJson(payload) + System.lineSeparator(), StandardCharsets.UTF_8,
                    Files.exists(eventsPath) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException ignored) {
        }
    }
}

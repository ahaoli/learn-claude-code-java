package com.learnclaudecode.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * 从任意文本中提取 ```json ... ``` 代码块内容。
 *
 * <p>支持以下全部场景：
 * <ol>
 *   <li>单行格式：{@code ```json {"a":1} ```}</li>
 *   <li>标准多行格式</li>
 *   <li>多层嵌套（块内部出现 ```json ... ``` 字符串，递归提取最内层）</li>
 *   <li>JSON 块嵌在一行文字中间，如：{@code 哈哈 ```json {"a":1} ``` hhh}</li>
 *   <li>两个独立 JSON 块之间有行内反引号（原始 Bug 场景）</li>
 *   <li>语言标记大小写不敏感（json / JSON / Json）</li>
 * </ol>
 *
 * <p>核心策略：
 * <ul>
 *   <li>找到 {@code ```json} 后，读取语言标记同行剩余内容：
 *       若同行有内容 → 单行模式，在同行找结束 {@code ```}；
 *       否则 → 多行模式，向后找"后缀只有空白"的 {@code ```}。</li>
 *   <li>结束 {@code ```} 判定：其后到行尾只有空白，不要求必须在行首
 *       （从而支持场景4）。</li>
 * </ul>
 */
public class MarkdownJsonExtractor {

    // ═══════════════════════════════════════════════════════════════
    // 公共 API
    // ═══════════════════════════════════════════════════════════════

    /**
     * 提取文本中最后一个 {@code ```json ... ```} 块的最内层内容（去首尾空白）。
     * 若不含任何合法块，返回空字符串。
     *
     * <p>嵌套时递归向内钻取，直到不再包含 {@code ```json} 块为止。
     */
    public static String extractMarkdownJson(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String result = extractInnermostJson(content);
        return result != null ? result : "";
    }

    /** 递归提取最内层 ```json 块的内容。若无任何块返回 null。 */
    private static String extractInnermostJson(String content) {
        List<String> blocks = extractAllJsonBlocks(content);
        if (blocks.isEmpty()) {
            return null;
        }
        String lastBlock = blocks.get(blocks.size() - 1);
        String inner = extractInnermostJson(lastBlock);
        return inner != null ? inner : lastBlock;
    }

    /**
     * 提取文本中所有 {@code ```json ... ```} 块的内容列表（按出现顺序）。
     */
    public static List<String> extractAllJsonBlocks(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return result;
        }

        final int n = content.length();
        int i = 0;

        while (i < n) {
            // ── 步骤1：找下一个 ``` ──────────────────────────────────
            int triplePos = content.indexOf("```", i);
            if (triplePos == -1) break;

            // ── 步骤2：读取语言标记及同行剩余内容 ────────────────────
            int afterTriple = triplePos + 3;
            int lineEnd = content.indexOf('\n', afterTriple); // 本行 \n 位置，-1 表示末行
            boolean hasNewline = (lineEnd != -1);
            // restOfLine：语言标记 + 同行内容（不含 \n）
            String restOfLine = hasNewline
                    ? content.substring(afterTriple, lineEnd)
                    : content.substring(afterTriple);

            // 拆分语言标记与其后内容
            // restOfLine 示例：
            //   "json"              → lang="json", afterLang=""
            //   "json {"a":1} ```" → lang="json", afterLang={"a":1} ```
            //   "JSON"             → lang="JSON", afterLang=""
            int spaceIdx = indexOfWhitespace(restOfLine);
            String lang;
            String afterLang; // 语言标记之后、同行的剩余文本（已 trim）
            if (spaceIdx == -1) {
                lang = restOfLine.trim();
                afterLang = "";
            } else {
                lang = restOfLine.substring(0, spaceIdx).trim();
                afterLang = restOfLine.substring(spaceIdx).trim();
            }

            if (!lang.equalsIgnoreCase("json")) {
                i = triplePos + 3;
                continue;
            }

            // ── 步骤3：区分单行 vs 多行 ──────────────────────────────
            String blockContent;

            if (!afterLang.isEmpty()) {
                // ===== 单行 / 行内格式：```json CONTENT ``` hhh =====
                // 在 afterLang 中找结束 ```
                int closeInAfterLang = afterLang.indexOf("```");
                if (closeInAfterLang != -1) {
                    blockContent = afterLang.substring(0, closeInAfterLang).trim();
                    // 定位原串中结束 ``` 的位置，以便继续扫描
                    // 从 afterTriple 开始，在原串里找到 afterLang 对应的起点
                    int afterLangStartInContent = content.indexOf(afterLang, afterTriple);
                    int closeInContent = afterLangStartInContent + closeInAfterLang;
                    i = closeInContent + 3;
                } else {
                    // 同行没找到结束 ```，退化为多行模式
                    int multilineStart = hasNewline ? lineEnd + 1 : n;
                    int closeTriple = findClosingTriple(content, multilineStart);
                    if (closeTriple == -1) {
                        i = n; // 未闭合，跳过
                        continue;
                    }
                    blockContent = (afterLang + "\n" + content.substring(multilineStart, closeTriple)).trim();
                    i = closeTriple + 3;
                }
            } else {
                // ===== 多行格式：```json\nCONTENT\n``` =====
                int blockStart = hasNewline ? lineEnd + 1 : n;
                int closeTriple = findClosingTriple(content, blockStart);
                if (closeTriple == -1) {
                    i = n; // 未闭合，跳过
                    continue;
                }
                blockContent = content.substring(blockStart, closeTriple).trim();
                i = closeTriple + 3;
            }

            if (!blockContent.isEmpty()) {
                result.add(blockContent);
            }
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部辅助
    // ═══════════════════════════════════════════════════════════════

    /** 返回字符串中第一个空白字符（空格/Tab）的索引，找不到返回 -1。 */
    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') return i;
        }
        return -1;
    }

    /**
     * 从 fromIndex 开始，找到有效结束 {@code ```} 的位置。
     *
     * <p>有效规则：{@code ```} 之后到行尾（或字符串尾）只有空白字符。
     * 不要求必须在行首，从而同时支持多行格式和行内格式。
     */
    private static int findClosingTriple(String s, int fromIndex) {
        int pos = fromIndex;
        final int n = s.length();
        while (pos < n) {
            int triplePos = s.indexOf("```", pos);
            if (triplePos == -1) return -1;
            if (isClosingTriple(s, triplePos)) return triplePos;
            pos = triplePos + 3;
        }
        return -1;
    }

    /**
     * 判断 triplePos 处的 {@code ```} 是否为有效结束符。
     * 规则：{@code ```} 之后到行尾只有空白字符。
     */
    private static boolean isClosingTriple(String s, int triplePos) {
        final int n = s.length();
        int afterTriple = triplePos + 3;
        for (int k = afterTriple; k < n; k++) {
            char c = s.charAt(k);
            if (c == '\n') break;
            if (c != ' ' && c != '\t' && c != '\r') return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 测试
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        run("场景1a - 单行格式（行内有前后文字）",
                "result: ```json {\"a\":1, \"b\":\"hello\"} ``` done");

        run("场景1b - 单行格式（行首无前缀）",
                "```json {\"single\":true} ```");

        run("场景2 - 标准多行格式",
                "```json\n{\"multi\":true,\n\"lines\":3}\n```");

        run("场景3 - 多层嵌套（递归提取最内层）",
                "```json\n{\n  \"desc\": \"use ```json {\\\"inner\\\":true} ``` syntax\",\n  \"ok\": true\n}\n```");

        run("场景4 - JSON 块嵌在一行文字中间",
                "哈哈哈哈哈 ，```json {\"inline\":true} ``` hhh");

        run("场景5 - 两个块之间有行内反引号（原始Bug复现）",
                "```json\n{\"first\":1}\n```\n"
                        + "说明：发现 `inputDetail` 为 `[]`\n"
                        + "```json\n{\"second\":2}\n```");

        run("场景6 - 语言标记大写",
                "```JSON\n{\"upper\":true}\n```");

        run("场景7 - 无任何块",
                "这是普通文本，没有代码块。");

        run("场景8 - 多个块，取最后一个",
                "first: ```json\n{\"order\":1}\n```\nsecond: ```json\n{\"order\":2}\n```");

        System.out.println("=== 场景8 - 所有块列表 ===");
        List<String> all = extractAllJsonBlocks(
                "first: ```json\n{\"order\":1}\n```\n 这是一个`测试 second: ```json\n{\"order\":2}\n```");
        for (int idx = 0; idx < all.size(); idx++) {
            System.out.println("[" + idx + "] " + all.get(idx));
        }
    }

    private static void run(String label, String input) {
        System.out.println("=== " + label + " ===");
        System.out.println("输入: " + input.replace("\n", "\\n"));
        System.out.println("输出: " + extractMarkdownJson(input));
        System.out.println();
    }
}
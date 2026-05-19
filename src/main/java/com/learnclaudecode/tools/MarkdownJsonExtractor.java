package com.learnclaudecode.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * 从任意文本中提取 Markdown fenced json 代码块。
 *
 * <p>设计目标：
 * <ul>
 *   <li>extractMarkdownJson：提取“最后一个 json 块”的内容（最常见的 LLM tool output 场景）。</li>
 *   <li>extractAllJsonBlocks：提取全部 json 块内容（按出现顺序）。</li>
 *   <li>支持单行/多行/行内前后缀文字/大小写 JSON 标签。</li>
 *   <li>支持嵌套：通过“先拿最后一个块，再在块内继续找最后一个块”实现递归下钻到最内层。</li>
 * </ul>
 */
public class MarkdownJsonExtractor {

    private static final String FENCE = "```";

    /**
     * 提取文本中最后一个 {@code ```json ... ```} 块的最内层内容（去首尾空白）。
     * 若不含任何合法块，返回空字符串。
     */
    public static String extractMarkdownJson(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String current = content;
        String lastFound = null;

        // 持续在“当前文本”里提取最后一个 json 块，直到不能再下钻。
        while (true) {
            String next = extractLastJsonBlockOnce(current);
            if (next == null) {
                break;
            }
            lastFound = next;
            current = next;
        }

        return lastFound == null ? "" : lastFound;
    }

    /**
     * 提取文本中所有 {@code ```json ... ```} 块内容（按出现顺序）。
     */
    public static List<String> extractAllJsonBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return blocks;
        }

        int scanFrom = 0;
        while (true) {
            int openPos = findNextJsonFence(content, scanFrom);
            if (openPos == -1) {
                break;
            }

            ParsedBlock parsed = parseJsonBlockAt(content, openPos);
            if (parsed == null) {
                // 防止死循环：至少向后推进 3 个字符
                scanFrom = openPos + FENCE.length();
                continue;
            }

            if (!parsed.content.isEmpty()) {
                blocks.add(parsed.content);
            }
            scanFrom = parsed.nextScanIndex;
        }

        return blocks;
    }

    /**
     * 在给定文本中仅提取“最后一个”json 块（不递归）。
     */
    private static String extractLastJsonBlockOnce(String content) {
        int openPos = findLastJsonFence(content);
        if (openPos == -1) {
            return null;
        }

        ParsedBlock parsed = parseJsonBlockAt(content, openPos);
        return parsed == null ? null : parsed.content;
    }

    /** 从 fromIndex 开始找下一个 json fence 起点。 */
    private static int findNextJsonFence(String content, int fromIndex) {
        int i = Math.max(0, fromIndex);
        while (true) {
            int fencePos = content.indexOf(FENCE, i);
            if (fencePos == -1) {
                return -1;
            }
            if (isJsonFence(content, fencePos)) {
                return fencePos;
            }
            i = fencePos + FENCE.length();
        }
    }

    /** 找最后一个 json fence 起点。 */
    private static int findLastJsonFence(String content) {
        int last = -1;
        int from = 0;
        while (true) {
            int next = findNextJsonFence(content, from);
            if (next == -1) {
                break;
            }
            last = next;
            from = next + FENCE.length();
        }
        return last;
    }

    /** 判断 fencePos 是否是 ```json（大小写不敏感）起点。 */
    private static boolean isJsonFence(String content, int fencePos) {
        int afterFence = fencePos + FENCE.length();
        int lineEnd = findLineEnd(content, afterFence);
        String rest = content.substring(afterFence, lineEnd);

        int ws = indexOfWhitespace(rest);
        String lang = ws == -1 ? rest.trim() : rest.substring(0, ws).trim();
        return lang.equalsIgnoreCase("json");
    }

    /**
     * 从指定的 json fence 起点解析块内容。
     *
     * <p>规则：
     * <ul>
     *   <li>若语言标记同行后还有内容（afterLang 非空），先尝试同一行内找结束 ```（单行模式）。</li>
     *   <li>若同行找不到结束 ```，则转多行模式，继续向后找“后缀仅空白”的结束 ```。</li>
     *   <li>多行模式下，结束 fence 可在行首或行中，但 fence 后到行尾必须只有空白。</li>
     * </ul>
     */
    private static ParsedBlock parseJsonBlockAt(String content, int openFencePos) {
        int n = content.length();
        int afterFence = openFencePos + FENCE.length();
        int lineEnd = findLineEnd(content, afterFence);
        boolean hasNewline = lineEnd < n;

        String restOfLine = content.substring(afterFence, lineEnd);
        int ws = indexOfWhitespace(restOfLine);
        String afterLang = ws == -1 ? "" : restOfLine.substring(ws).trim();

        // 单行优先：```json {...} ```
        if (!afterLang.isEmpty()) {
            int closeInline = afterLang.indexOf(FENCE);
            if (closeInline != -1) {
                String block = afterLang.substring(0, closeInline).trim();
                int afterLangStart = content.indexOf(afterLang, afterFence);
                int closeFencePos = afterLangStart + closeInline;
                return new ParsedBlock(block, closeFencePos + FENCE.length());
            }
        }

        // 多行模式（也覆盖“afterLang 非空但同行无闭合”的情况）
        int blockStart = hasNewline ? lineEnd + 1 : n;
        int closeFencePos = findClosingFence(content, blockStart);
        if (closeFencePos == -1) {
            // 无闭合：若是“单行无换行且 afterLang 非空”，退化返回 afterLang；否则解析失败
            if (!hasNewline && !afterLang.isEmpty()) {
                return new ParsedBlock(afterLang.trim(), n);
            }
            return null;
        }

        String multi = content.substring(blockStart, closeFencePos).trim();
        if (!afterLang.isEmpty()) {
            multi = (afterLang + "\n" + multi).trim();
        }
        return new ParsedBlock(multi, closeFencePos + FENCE.length());
    }

    /** 从 from 开始找有效结束 fence：其后到行尾仅空白。 */
    private static int findClosingFence(String content, int from) {
        int i = Math.max(0, from);
        while (true) {
            int pos = content.indexOf(FENCE, i);
            if (pos == -1) {
                return -1;
            }
            if (isClosingFence(content, pos)) {
                return pos;
            }
            i = pos + FENCE.length();
        }
    }

    /** 结束 fence 判定：``` 后到行尾只有空白。 */
    private static boolean isClosingFence(String content, int fencePos) {
        int i = fencePos + FENCE.length();
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == '\n') {
                return true;
            }
            if (c != ' ' && c != '\t' && c != '\r') {
                return false;
            }
            i++;
        }
        return true;
    }

    /** 返回 s 从 from 开始所在行的行尾索引（不含换行符）。 */
    private static int findLineEnd(String s, int from) {
        int pos = s.indexOf('\n', from);
        return pos == -1 ? s.length() : pos;
    }

    /** 返回字符串中第一个空白字符（空格/Tab）索引，找不到返回 -1。 */
    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') {
                return i;
            }
        }
        return -1;
    }

    private static class ParsedBlock {
        final String content;
        final int nextScanIndex;

        ParsedBlock(String content, int nextScanIndex) {
            this.content = content == null ? "" : content;
            this.nextScanIndex = nextScanIndex;
        }
    }

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

        run("场景9 - 多层嵌套（递归提取最内层）",
                "result: ```json\n```json {\"a\":1, \"b\":\"hello\"} ```\n``` done");

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

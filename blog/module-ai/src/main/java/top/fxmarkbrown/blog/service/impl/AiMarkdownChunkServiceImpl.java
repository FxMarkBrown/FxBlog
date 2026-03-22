package top.fxmarkbrown.blog.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.model.ai.AiMarkdownChunk;
import top.fxmarkbrown.blog.service.AiMarkdownChunkService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiMarkdownChunkServiceImpl implements AiMarkdownChunkService {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*([-*+]|\\d+\\.)\\s+.+$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("^!\\[[^]]*]\\(([^)]+)\\)\\s*$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern BOLD_ITALIC_PATTERN = Pattern.compile("\\*\\*\\*([^*]+?)\\*\\*\\*|___([^_]+?)___");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+?)\\*\\*|__([^_]+?)__");
    private static final Pattern STRIKE_PATTERN = Pattern.compile("~~(.+?)~~");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+?)`");
    private static final Pattern LINK_PATTERN = Pattern.compile("(?<!!)\\[([^]]+)]\\(([^)]+)\\)");
    private static final Pattern INLINE_IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern HTML_BLOCK_START_PATTERN = Pattern.compile(
            "^\\s*<(div|iframe|video|table|details|summary|figure|figcaption|blockquote|section|article|aside|pre)(\\s|>|/).*$",
            Pattern.CASE_INSENSITIVE
    );

    private final AiRagProperties aiRagProperties;

    public AiMarkdownChunkServiceImpl(AiRagProperties aiRagProperties) {
        this.aiRagProperties = aiRagProperties;
    }

    public List<AiMarkdownChunk> split(SysArticle article) {
        String content = resolveMarkdown(article);
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        List<AiMarkdownChunk> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        List<String> paragraphBuffer = new ArrayList<>();
        List<String> blockBuffer = new ArrayList<>();
        int[] chunkOrder = {0};
        int[] currentHeadingLevel = {1};
        String[] currentBlockType = {"paragraph"};
        boolean inFencedCode = false;
        boolean inMathBlock = false;
        boolean inTableBlock = false;
        boolean inHtmlBlock = false;

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String line : lines) {
            if (inTableBlock && !isTableRow(line)) {
                inTableBlock = false;
                flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
                currentBlockType[0] = "paragraph";
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (!inFencedCode && !inMathBlock && !inTableBlock && !inHtmlBlock && headingMatcher.matches()) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
                currentHeadingLevel[0] = headingMatcher.group(1).length();
                updateHeadingStack(headingStack, currentHeadingLevel[0], headingMatcher.group(2).trim());
                continue;
            }

            if (!inMathBlock && !inTableBlock && !inHtmlBlock && isFenceLine(line)) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                currentBlockType[0] = "code";
                blockBuffer.add(line);
                inFencedCode = !inFencedCode;
                if (!inFencedCode) {
                    flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
                }
                continue;
            }
            if (inFencedCode) {
                blockBuffer.add(line);
                continue;
            }

            if (!inTableBlock && !inHtmlBlock && isSingleLineMath(line)) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                currentBlockType[0] = "math";
                blockBuffer.add(line);
                flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
                continue;
            }
            if (!inMathBlock && !inTableBlock && !inHtmlBlock && isMathDelimiter(line)) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                currentBlockType[0] = "math";
                blockBuffer.add(line);
                inMathBlock = true;
                continue;
            }
            if (inMathBlock) {
                blockBuffer.add(line);
                if (isMathDelimiter(line)) {
                    inMathBlock = false;
                    flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
                }
                continue;
            }

            if (!inHtmlBlock && isHtmlBlockStart(line)) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                currentBlockType[0] = isVideoHtmlBlock(line) ? "video-ref" : "html";
                blockBuffer.add(line);
                if (isHtmlBlockClosed(line)) {
                    flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
                    currentBlockType[0] = "paragraph";
                } else {
                    inHtmlBlock = true;
                }
                continue;
            }
            if (inHtmlBlock) {
                blockBuffer.add(line);
                if (isHtmlBlockClosed(line)) {
                    inHtmlBlock = false;
                    flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
                    currentBlockType[0] = "paragraph";
                }
                continue;
            }

            if (isTableRow(line)) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                currentBlockType[0] = "table";
                blockBuffer.add(line);
                inTableBlock = true;
                continue;
            }

            if (!StringUtils.hasText(line)) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                continue;
            }

            if (IMAGE_PATTERN.matcher(line.strip()).matches()) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                addChunk(chunks, chunkOrder, headingStack, currentHeadingLevel[0], "image", line.strip());
                continue;
            }

            if (line.strip().startsWith(">")) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                addChunk(chunks, chunkOrder, headingStack, currentHeadingLevel[0], "quote", line.strip());
                continue;
            }

            if (LIST_PATTERN.matcher(line).matches()) {
                flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
                addChunk(chunks, chunkOrder, headingStack, currentHeadingLevel[0], "list", line.strip());
                continue;
            }

            paragraphBuffer.add(line);
        }

        flushParagraphBlock(chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder);
        flushSpecialBlock(chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder);
        return chunks;
    }

    private void flushParagraphBlock(List<AiMarkdownChunk> chunks,
                                     List<String> paragraphBuffer,
                                     List<String> headingStack,
                                     int headingLevel,
                                     int[] chunkOrder) {
        if (paragraphBuffer.isEmpty()) {
            return;
        }
        String text = String.join("\n", paragraphBuffer).trim();
        paragraphBuffer.clear();
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String part : splitLongText(text, aiRagProperties.getMaxChunkChars())) {
            addChunk(chunks, chunkOrder, headingStack, headingLevel, "paragraph", part);
        }
    }

    private void flushSpecialBlock(List<AiMarkdownChunk> chunks,
                                   List<String> blockBuffer,
                                   List<String> headingStack,
                                   int headingLevel,
                                   String blockType,
                                   int[] chunkOrder) {
        if (blockBuffer.isEmpty()) {
            return;
        }
        String text = String.join("\n", blockBuffer).trim();
        blockBuffer.clear();
        if (!StringUtils.hasText(text)) {
            return;
        }
        addChunk(chunks, chunkOrder, headingStack, headingLevel, blockType, text);
    }

    private void addChunk(List<AiMarkdownChunk> chunks,
                          int[] chunkOrder,
                          List<String> headingStack,
                          int headingLevel,
                          String blockType,
                          String rawText) {
        String cleaned = rawText.trim();
        if (!StringUtils.hasText(cleaned)) {
            return;
        }
        String sectionPath = headingStack.isEmpty() ? "未分节" : String.join(" > ", headingStack);
        String retrievalText = "标题路径：" + sectionPath + "\n块类型：" + blockType + "\n内容：\n"
                + normalizeInlineMarkdownForRetrieval(cleaned);
        String preview = cleaned.replace('\n', ' ');
        if (preview.length() > 120) {
            preview = preview.substring(0, 120) + "...";
        }
        chunks.add(new AiMarkdownChunk(
                chunkOrder[0]++,
                sectionPath,
                headingLevel,
                blockType,
                cleaned,
                retrievalText,
                preview,
                cleaned.contains("$$") || cleaned.contains("\\begin"),
                IMAGE_PATTERN.matcher(cleaned).find(),
                cleaned.contains("<iframe") || cleaned.contains(".mp4")
        ));
    }

    private List<String> splitLongText(String text, int maxChunkChars) {
        if (text.length() <= maxChunkChars) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[。！？；.!?])")) {
            String trimmed = sentence.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (!current.isEmpty() && current.length() + trimmed.length() > maxChunkChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(trimmed);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        if (result.isEmpty()) {
            result.add(text.substring(0, maxChunkChars));
        }
        return result;
    }

    private String normalizeInlineMarkdownForRetrieval(String text) {
        String normalized = text;
        normalized = INLINE_IMAGE_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "图片：" + safeInlineText(matchResult.group(1)));
        normalized = LINK_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "链接文本：" + safeInlineText(matchResult.group(1)));
        normalized = BOLD_ITALIC_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "强强调：" + firstNotBlank(matchResult.group(1), matchResult.group(2)));
        normalized = BOLD_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "强调：" + firstNotBlank(matchResult.group(1), matchResult.group(2)));
        normalized = INLINE_CODE_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "代码：" + matchResult.group(1));
        normalized = STRIKE_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "删除内容：" + matchResult.group(1));
        return normalized;
    }

    private String firstNotBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String safeInlineText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未命名";
    }

    private void updateHeadingStack(List<String> headingStack, int level, String heading) {
        while (headingStack.size() >= level) {
            headingStack.removeLast();
        }
        headingStack.add(heading);
    }

    private boolean isFenceLine(String line) {
        return line.strip().startsWith("```");
    }

    private boolean isSingleLineMath(String line) {
        String stripped = line.strip();
        return stripped.length() > 4 && stripped.startsWith("$$") && stripped.endsWith("$$");
    }

    private boolean isMathDelimiter(String line) {
        return line.strip().equals("$$");
    }

    private boolean isTableRow(String line) {
        return TABLE_ROW_PATTERN.matcher(line).matches();
    }

    private boolean isHtmlBlockStart(String line) {
        return HTML_BLOCK_START_PATTERN.matcher(line).matches();
    }

    private boolean isVideoHtmlBlock(String line) {
        String stripped = line.strip().toLowerCase();
        return stripped.startsWith("<iframe") || stripped.startsWith("<video");
    }

    private boolean isHtmlBlockClosed(String line) {
        String stripped = line.strip();
        return stripped.contains("</div>")
                || stripped.contains("</iframe>")
                || stripped.contains("</video>")
                || stripped.contains("</table>")
                || stripped.contains("</details>")
                || stripped.contains("</figure>")
                || stripped.contains("</blockquote>")
                || stripped.contains("</section>")
                || stripped.contains("</article>")
                || stripped.contains("</aside>")
                || stripped.contains("</pre>");
    }

    private String resolveMarkdown(SysArticle article) {
        if (article == null) {
            return "";
        }
        if (StringUtils.hasText(article.getContentMd())) {
            return article.getContentMd();
        }
        return article.getContent();
    }
}

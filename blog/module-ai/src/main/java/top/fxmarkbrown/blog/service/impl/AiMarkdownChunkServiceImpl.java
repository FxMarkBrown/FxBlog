package top.fxmarkbrown.blog.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysCategory;
import top.fxmarkbrown.blog.entity.SysTag;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysCategoryMapper;
import top.fxmarkbrown.blog.mapper.SysTagMapper;
import top.fxmarkbrown.blog.model.ai.AiChunkInternalLink;
import top.fxmarkbrown.blog.model.ai.AiChunkMediaRef;
import top.fxmarkbrown.blog.model.ai.AiChunkTaxonomyLink;
import top.fxmarkbrown.blog.model.ai.AiMarkdownChunk;
import top.fxmarkbrown.blog.service.AiMarkdownChunkService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiMarkdownChunkServiceImpl implements AiMarkdownChunkService {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*([-*+]|\\d+\\.)\\s+.+$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("^!\\[[^]]*]\\(([^)]+)\\)\\s*$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern ALIGN_BLOCK_OPEN_PATTERN = Pattern.compile("^\\s*:::\\s*align-(left|right|center)\\s*$");
    private static final Pattern ALIGN_BLOCK_CLOSE_PATTERN = Pattern.compile("^\\s*:::\\s*$");
    private static final Pattern BOLD_ITALIC_PATTERN = Pattern.compile("\\*\\*\\*([^*]+?)\\*\\*\\*|___([^_]+?)___");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+?)\\*\\*|__([^_]+?)__");
    private static final Pattern STRIKE_PATTERN = Pattern.compile("~~(.+?)~~");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+?)`");
    private static final Pattern LINK_PATTERN = Pattern.compile("(?<!!)\\[([^]]+)]\\(([^)]+)\\)");
    private static final Pattern INTERNAL_POST_LINK_PATTERN = Pattern.compile("^/post/(\\d+)(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)\\s]+)(?:\\s+\"([^\"]*)\")?\\)");
    private static final Pattern HTML_MEDIA_TAG_PATTERN = Pattern.compile("<(img|video|source|iframe)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTRIBUTE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*(['\"])(.*?)\\2", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_BLOCK_START_PATTERN = Pattern.compile(
            "^\\s*<(div|iframe|video|table|details|summary|figure|figcaption|blockquote|section|article|aside|pre)(\\s|>|/).*$",
            Pattern.CASE_INSENSITIVE
    );

    private final AiRagProperties aiRagProperties;
    private final SysArticleMapper articleMapper;
    private final SysCategoryMapper categoryMapper;
    private final SysTagMapper tagMapper;

    public AiMarkdownChunkServiceImpl(AiRagProperties aiRagProperties,
                                      SysArticleMapper articleMapper,
                                      SysCategoryMapper categoryMapper,
                                      SysTagMapper tagMapper) {
        this.aiRagProperties = aiRagProperties;
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
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
        Map<Long, String> articleTitleCache = new LinkedHashMap<>();
        List<AiChunkTaxonomyLink> taxonomyLinks = resolveTaxonomyLinks(article);
        int[] chunkOrder = {0};
        int[] currentHeadingLevel = {1};
        String[] currentBlockType = {"paragraph"};
        boolean inFencedCode = false;
        boolean inMathBlock = false;
        boolean inTableBlock = false;
        boolean inHtmlBlock = false;
        boolean inAlignBlock = false;
        boolean inImageBlock = false;
        boolean inQuoteBlock = false;
        boolean inListBlock = false;

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String line : lines) {
            if (inTableBlock && !isTableRow(line)) {
                inTableBlock = false;
                flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "paragraph";
            }

            if (inImageBlock && !isStandaloneImage(line)) {
                inImageBlock = false;
                flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "paragraph";
            }

            if (inQuoteBlock && !isQuoteLine(line)) {
                inQuoteBlock = false;
                flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "paragraph";
            }

            if (inListBlock && !isListLine(line)) {
                inListBlock = false;
                flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "paragraph";
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (!inFencedCode && !inMathBlock && !inTableBlock && !inHtmlBlock && headingMatcher.matches()) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentHeadingLevel[0] = headingMatcher.group(1).length();
                updateHeadingStack(headingStack, currentHeadingLevel[0], headingMatcher.group(2).trim());
                continue;
            }

            Matcher alignMatcher = ALIGN_BLOCK_OPEN_PATTERN.matcher(line);
            if (!inFencedCode && !inMathBlock && !inTableBlock && !inHtmlBlock && !inAlignBlock && alignMatcher.matches()) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "align-" + alignMatcher.group(1);
                inAlignBlock = true;
                continue;
            }
            if (inAlignBlock) {
                if (ALIGN_BLOCK_CLOSE_PATTERN.matcher(line).matches()) {
                    inAlignBlock = false;
                    flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                    currentBlockType[0] = "paragraph";
                } else {
                    blockBuffer.add(line);
                }
                continue;
            }

            if (!inMathBlock && !inTableBlock && !inHtmlBlock && isFenceLine(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "code";
                blockBuffer.add(line);
                inFencedCode = !inFencedCode;
                if (!inFencedCode) {
                    flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                }
                continue;
            }
            if (inFencedCode) {
                blockBuffer.add(line);
                continue;
            }

            if (!inTableBlock && !inHtmlBlock && isSingleLineMath(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "math";
                blockBuffer.add(line);
                flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                continue;
            }
            if (!inMathBlock && !inTableBlock && !inHtmlBlock && isMathDelimiter(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "math";
                blockBuffer.add(line);
                inMathBlock = true;
                continue;
            }
            if (inMathBlock) {
                blockBuffer.add(line);
                if (isMathDelimiter(line)) {
                    inMathBlock = false;
                    flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                }
                continue;
            }

            if (!inHtmlBlock && isHtmlBlockStart(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = isVideoHtmlBlock(line) ? "video-ref" : "html";
                blockBuffer.add(line);
                if (isHtmlBlockClosed(line)) {
                    flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
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
                    flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
                    currentBlockType[0] = "paragraph";
                }
                continue;
            }

            if (isTableRow(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "table";
                blockBuffer.add(line);
                inTableBlock = true;
                continue;
            }

            if (!StringUtils.hasText(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                continue;
            }

            if (isStandaloneImage(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "image-gallery";
                blockBuffer.add(line.strip());
                inImageBlock = true;
                continue;
            }

            if (isQuoteLine(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "quote";
                blockBuffer.add(line.strip());
                inQuoteBlock = true;
                continue;
            }

            if (isListLine(line)) {
                flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
                currentBlockType[0] = "list";
                blockBuffer.add(line.strip());
                inListBlock = true;
                continue;
            }

            paragraphBuffer.add(line);
        }

        flushParagraphBlock(article, chunks, paragraphBuffer, headingStack, currentHeadingLevel[0], chunkOrder, articleTitleCache, taxonomyLinks);
        flushSpecialBlock(article, chunks, blockBuffer, headingStack, currentHeadingLevel[0], currentBlockType[0], chunkOrder, articleTitleCache, taxonomyLinks);
        return chunks;
    }

    private void flushParagraphBlock(SysArticle article,
                                     List<AiMarkdownChunk> chunks,
                                     List<String> paragraphBuffer,
                                     List<String> headingStack,
                                     int headingLevel,
                                     int[] chunkOrder,
                                     Map<Long, String> articleTitleCache,
                                     List<AiChunkTaxonomyLink> taxonomyLinks) {
        if (paragraphBuffer.isEmpty()) {
            return;
        }
        String text = String.join("\n", paragraphBuffer).trim();
        paragraphBuffer.clear();
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String part : splitLongText(text, aiRagProperties.getMaxChunkChars())) {
            addChunk(article, chunks, chunkOrder, headingStack, headingLevel, "paragraph", part, articleTitleCache, taxonomyLinks);
        }
    }

    private void flushSpecialBlock(SysArticle article,
                                   List<AiMarkdownChunk> chunks,
                                   List<String> blockBuffer,
                                   List<String> headingStack,
                                   int headingLevel,
                                   String blockType,
                                   int[] chunkOrder,
                                   Map<Long, String> articleTitleCache,
                                   List<AiChunkTaxonomyLink> taxonomyLinks) {
        if (blockBuffer.isEmpty()) {
            return;
        }
        String text = String.join("\n", blockBuffer).trim();
        blockBuffer.clear();
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (blockType.startsWith("align-")) {
            text = normalizeAlignBlockContent(text);
            if (!StringUtils.hasText(text)) {
                return;
            }
        }
        addChunk(article, chunks, chunkOrder, headingStack, headingLevel, blockType, text, articleTitleCache, taxonomyLinks);
    }

    private void addChunk(SysArticle article,
                          List<AiMarkdownChunk> chunks,
                          int[] chunkOrder,
                          List<String> headingStack,
                          int headingLevel,
                          String blockType,
                          String rawText,
                          Map<Long, String> articleTitleCache,
                          List<AiChunkTaxonomyLink> taxonomyLinks) {
        String cleaned = rawText.trim();
        if (!StringUtils.hasText(cleaned)) {
            return;
        }
        String sectionPath = headingStack.isEmpty() ? "未分节" : String.join(" > ", headingStack);
        List<AiChunkInternalLink> internalLinks = extractInternalLinks(cleaned, articleTitleCache);
        List<AiChunkMediaRef> mediaRefs = extractMediaRefs(cleaned);
        String retrievalText = buildRetrievalText(article, sectionPath, blockType, cleaned, internalLinks, mediaRefs, taxonomyLinks);
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
                internalLinks,
                mediaRefs,
                taxonomyLinks,
                cleaned.contains("$$") || cleaned.contains("\\begin"),
                INLINE_IMAGE_PATTERN.matcher(cleaned).find(),
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

    private String buildRetrievalText(SysArticle article,
                                      String sectionPath,
                                      String blockType,
                                      String rawText,
                                      List<AiChunkInternalLink> internalLinks,
                                      List<AiChunkMediaRef> mediaRefs,
                                      List<AiChunkTaxonomyLink> taxonomyLinks) {
        StringBuilder builder = new StringBuilder();
        if (article != null) {
            appendIfPresent(builder, "文章标题", article.getTitle());
            appendIfPresent(builder, "文章摘要", article.getSummary());
            appendIfPresent(builder, "文章关键词", normalizeKeywords(article.getKeywords()));
            appendIfPresent(builder, "文章属性", buildArticleFlags(article));
            appendIfPresent(builder, "原文地址", article.getOriginalUrl());
            appendIfPresent(builder, "发布时间", article.getCreateTime() == null ? "" : article.getCreateTime().toString());
            appendIfPresent(builder, "更新时间", article.getUpdateTime() == null ? "" : article.getUpdateTime().toString());
        }
        appendIfPresent(builder, "标题路径", sectionPath);
        appendIfPresent(builder, "块类型", blockType);
        if (internalLinks != null && !internalLinks.isEmpty()) {
            appendIfPresent(builder, "站内跳转", formatInternalLinksForRetrieval(internalLinks));
        }
        if (mediaRefs != null && !mediaRefs.isEmpty()) {
            appendIfPresent(builder, "媒体资源", formatMediaRefsForRetrieval(mediaRefs));
        }
        if (taxonomyLinks != null && !taxonomyLinks.isEmpty()) {
            appendIfPresent(builder, "分类标签跳转", formatTaxonomyLinksForRetrieval(taxonomyLinks));
        }
        builder.append("内容：\n").append(normalizeInlineMarkdownForRetrieval(rawText, internalLinks));
        return builder.toString();
    }

    private String normalizeInlineMarkdownForRetrieval(String text, List<AiChunkInternalLink> internalLinks) {
        String normalized = text;
        normalized = INLINE_IMAGE_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "图片：" + safeInlineText(matchResult.group(1)));
        normalized = replaceInternalLinksForRetrieval(normalized, internalLinks);
        normalized = LINK_PATTERN.matcher(normalized).replaceAll(matchResult ->
                "链接文本：" + safeInlineText(matchResult.group(1)) + "；链接地址：" + safeInlineText(matchResult.group(2)));
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

    private String normalizeAlignBlockContent(String text) {
        return text == null ? "" : text.trim();
    }

    private List<AiChunkInternalLink> extractInternalLinks(String text, Map<Long, String> articleTitleCache) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<AiChunkInternalLink> internalLinks = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(text);
        while (matcher.find()) {
            String target = matcher.group(2) == null ? "" : matcher.group(2).trim();
            Matcher internalMatcher = INTERNAL_POST_LINK_PATTERN.matcher(target);
            if (!internalMatcher.matches()) {
                continue;
            }
            Long articleId = Long.parseLong(internalMatcher.group(1));
            String anchorText = safeInlineText(matcher.group(1));
            String articleTitle = resolveArticleTitle(articleId, articleTitleCache);
            internalLinks.add(new AiChunkInternalLink(
                    anchorText,
                    "/post/" + articleId,
                    articleId,
                    StringUtils.hasText(articleTitle) ? articleTitle : anchorText
            ));
        }
        return internalLinks.isEmpty() ? List.of() : List.copyOf(internalLinks);
    }

    private String resolveArticleTitle(Long articleId, Map<Long, String> articleTitleCache) {
        if (articleId == null) {
            return "";
        }
        if (articleTitleCache.containsKey(articleId)) {
            return articleTitleCache.get(articleId);
        }
        SysArticle targetArticle = articleMapper.selectById(articleId);
        String articleTitle = targetArticle == null ? "" : safeInlineText(targetArticle.getTitle());
        articleTitleCache.put(articleId, articleTitle);
        return articleTitle;
    }

    private String formatInternalLinksForRetrieval(List<AiChunkInternalLink> internalLinks) {
        return internalLinks.stream()
                .map(link -> "[" + safeInlineText(link.targetArticleTitle()) + "](" + safeInlineText(link.targetPath()) + ")")
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private String formatMediaRefsForRetrieval(List<AiChunkMediaRef> mediaRefs) {
        return mediaRefs.stream()
                .map(ref -> "[" + safeInlineText(ref.mediaType()) + "：" + safeInlineText(ref.displayText()) + "](" + safeInlineText(ref.sourceUrl()) + ")")
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private String formatTaxonomyLinksForRetrieval(List<AiChunkTaxonomyLink> taxonomyLinks) {
        return taxonomyLinks.stream()
                .map(link -> "[" + safeInlineText(link.displayName()) + "](" + safeInlineText(link.targetPath()) + ")")
                .distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private String replaceInternalLinksForRetrieval(String text, List<AiChunkInternalLink> internalLinks) {
        if (!StringUtils.hasText(text) || internalLinks == null || internalLinks.isEmpty()) {
            return text;
        }
        Map<String, AiChunkInternalLink> linksByPath = new LinkedHashMap<>();
        for (AiChunkInternalLink link : internalLinks) {
            if (link != null && StringUtils.hasText(link.targetPath())) {
                linksByPath.putIfAbsent(link.targetPath(), link);
            }
        }
        return LINK_PATTERN.matcher(text).replaceAll(matchResult -> {
            String target = matchResult.group(2) == null ? "" : matchResult.group(2).trim();
            AiChunkInternalLink link = linksByPath.get(target);
            if (link == null) {
                return matchResult.group(0);
            }
            return "站内跳转：[" + safeInlineText(link.targetArticleTitle()) + "](" + safeInlineText(link.targetPath()) + ")"
                    + "；引用文本：" + safeInlineText(matchResult.group(1));
        });
    }

    private List<AiChunkMediaRef> extractMediaRefs(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Map<String, AiChunkMediaRef> mediaRefs = new LinkedHashMap<>();
        Matcher markdownImageMatcher = MARKDOWN_IMAGE_PATTERN.matcher(text);
        while (markdownImageMatcher.find()) {
            String sourceUrl = normalizeMediaSource(markdownImageMatcher.group(2));
            if (!StringUtils.hasText(sourceUrl)) {
                continue;
            }
            String displayText = firstNonBlank(markdownImageMatcher.group(1), markdownImageMatcher.group(3), "图片");
            mediaRefs.putIfAbsent("image:" + sourceUrl, new AiChunkMediaRef("图片", sourceUrl, displayText, isLocalResource(sourceUrl)));
        }

        Matcher htmlTagMatcher = HTML_MEDIA_TAG_PATTERN.matcher(text);
        while (htmlTagMatcher.find()) {
            String tag = htmlTagMatcher.group();
            String tagName = htmlTagMatcher.group(1).toLowerCase();
            Map<String, String> attributes = parseHtmlAttributes(tag);
            String sourceUrl = normalizeMediaSource(attributes.get("src"));
            if (!StringUtils.hasText(sourceUrl)) {
                continue;
            }
            String mediaType = "img".equals(tagName) ? "图片" : "视频";
            String displayText = firstNonBlank(attributes.get("alt"), attributes.get("title"), mediaType);
            mediaRefs.putIfAbsent(tagName + ":" + sourceUrl,
                    new AiChunkMediaRef(mediaType, sourceUrl, displayText, isLocalResource(sourceUrl)));
        }
        return mediaRefs.isEmpty() ? List.of() : List.copyOf(mediaRefs.values());
    }

    private Map<String, String> parseHtmlAttributes(String tag) {
        Map<String, String> attributes = new HashMap<>();
        Matcher attributeMatcher = HTML_ATTRIBUTE_PATTERN.matcher(tag);
        while (attributeMatcher.find()) {
            attributes.put(attributeMatcher.group(1).toLowerCase(), attributeMatcher.group(3));
        }
        return attributes;
    }

    private String normalizeMediaSource(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return "";
        }
        String normalized = sourceUrl.trim();
        if (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("data:")
                || normalized.startsWith("blob:")
                || normalized.startsWith("/")) {
            return normalized;
        }
        return "/" + normalized;
    }

    private boolean isLocalResource(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return false;
        }
        String normalized = sourceUrl.trim().toLowerCase();
        return !normalized.startsWith("http://")
                && !normalized.startsWith("https://")
                && !normalized.startsWith("data:")
                && !normalized.startsWith("blob:");
    }

    private List<AiChunkTaxonomyLink> resolveTaxonomyLinks(SysArticle article) {
        if (article == null) {
            return List.of();
        }
        Map<String, AiChunkTaxonomyLink> taxonomyLinks = new LinkedHashMap<>();
        if (article.getCategoryId() != null) {
            SysCategory category = categoryMapper.selectById(article.getCategoryId());
            if (category != null && StringUtils.hasText(category.getName())) {
                String categoryName = category.getName().trim();
                String targetPath = "/categories?categoryName=" + encodeQueryValue(categoryName);
                taxonomyLinks.putIfAbsent("category:" + categoryName,
                        new AiChunkTaxonomyLink("category", targetPath, Long.valueOf(category.getId()), categoryName));
            }
        }
        if (article.getId() != null) {
            for (SysTag tag : resolveArticleTags(article.getId())) {
                if (tag == null || !StringUtils.hasText(tag.getName()) || tag.getId() == null) {
                    continue;
                }
                String tagName = tag.getName().trim();
                String targetPath = "/tags?tagId=" + tag.getId() + "&tagName=" + encodeQueryValue(tagName);
                taxonomyLinks.putIfAbsent("tag:" + tag.getId(),
                        new AiChunkTaxonomyLink("tag", targetPath, Long.valueOf(tag.getId()), tagName));
            }
        }
        return taxonomyLinks.isEmpty() ? List.of() : List.copyOf(taxonomyLinks.values());
    }

    private List<SysTag> resolveArticleTags(Long articleId) {
        return tagMapper.getTagByArticleId(articleId).stream()
                .map(tagVo -> {
                    SysTag tag = new SysTag();
                    tag.setId(tagVo.getId());
                    tag.setName(tagVo.getName());
                    return tag;
                })
                .toList();
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
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

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        builder.append(label).append("：").append(value.trim()).append("\n");
    }

    private String normalizeKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return "";
        }
        return keywords.replace(',', '、')
                .replace('，', '、')
                .replaceAll("\\s+", "")
                .replaceAll("、{2,}", "、")
                .replaceAll("^、|、$", "");
    }

    private String buildArticleFlags(SysArticle article) {
        if (article == null) {
            return "";
        }
        List<String> flags = new ArrayList<>();
        flags.add(Integer.valueOf(1).equals(article.getIsOriginal()) ? "原创" : "转载");
        if (Integer.valueOf(1).equals(article.getIsStick())) {
            flags.add("置顶");
        }
        if (Integer.valueOf(1).equals(article.getIsRecommend())) {
            flags.add("推荐");
        }
        if (Integer.valueOf(1).equals(article.getIsCarousel())) {
            flags.add("轮播");
        }
        return String.join("、", flags);
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

    private boolean isStandaloneImage(String line) {
        return IMAGE_PATTERN.matcher(line.strip()).matches();
    }

    private boolean isQuoteLine(String line) {
        return line.strip().startsWith(">");
    }

    private boolean isListLine(String line) {
        return LIST_PATTERN.matcher(line).matches();
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

package top.fxmarkbrown.blog.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiProperties;
import top.fxmarkbrown.blog.service.AiRerankService;
import top.fxmarkbrown.blog.utils.HttpUtil;
import top.fxmarkbrown.blog.utils.JsonUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRerankServiceSiliconFlowImpl implements AiRerankService {

    private static final String META_ARTICLE_TITLE = "articleTitle";
    private static final String META_DOCUMENT_TITLE = "documentTitle";
    private static final String META_NODE_TITLE = "nodeTitle";
    private static final String META_SECTION_PATH = "sectionPath";
    private static final String META_TITLE_PATH = "titlePath";
    private static final String META_BLOCK_TYPE = "blockType";
    private static final String META_CHUNK_TYPE = "chunkType";
    private static final String META_HEADING_LEVEL = "headingLevel";
    private static final String META_LEVEL = "level";
    private static final String META_CONTENT_PREVIEW = "contentPreview";
    private static final String META_RAW_MARKDOWN = "rawMarkdownFragment";
    private static final int MAX_RERANK_CONTENT_CHARS = 1600;

    private final AiProperties aiProperties;

    @Override
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        AiProperties.Rerank rerankConfig = aiProperties.getRerank();
        AiProperties.OpenAiCompatibleProvider provider = aiProperties.requireProvider(rerankConfig.getProvider());
        if (!rerankConfig.isEnabled()
                || !StringUtils.hasText(query)
                || documents == null
                || documents.size() <= 1
                || !StringUtils.hasText(provider.getApiKey())) {
            return documents == null ? List.of() : documents;
        }
        try {
            List<String> documentArray = new ArrayList<>();
            for (Document document : documents) {
                documentArray.add(buildRerankDocument(document));
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", rerankConfig.getModel());
            requestBody.put("query", query);
            requestBody.put("documents", documentArray);
            requestBody.put("top_n", Math.min(Math.max(topN, 1), documents.size()));
            requestBody.put("return_documents", false);

            Map<String, String> headers = Map.of("Authorization", "Bearer " + provider.getApiKey().trim());
            String response = HttpUtil.postJson(buildRerankUrl(), JsonUtil.toJsonString(requestBody),
                    rerankConfig.getTimeoutMillis(), headers);
            JsonNode responseObject = JsonUtil.readTree(response);
            JsonNode results = responseObject == null ? null : responseObject.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return documents;
            }

            List<RerankHit> hits = new ArrayList<>();
            for (JsonNode item : results) {
                if (item == null || item.isNull()) {
                    continue;
                }
                Integer index = item.hasNonNull("index") ? item.get("index").intValue() : null;
                Double score = item.hasNonNull("relevance_score") ? item.get("relevance_score").doubleValue() : null;
                if (index == null || index < 0 || index >= documents.size()) {
                    continue;
                }
                hits.add(new RerankHit(index, score == null ? 0D : score));
            }
            if (hits.isEmpty()) {
                return documents;
            }
            hits.sort(Comparator.comparingDouble(RerankHit::score).reversed());
            return hits.stream()
                    .map(hit -> documents.get(hit.index()))
                    .toList();
        } catch (Exception ex) {
            log.warn("RAG rerank 失败, query={}", query, ex);
            return documents;
        }
    }

    private String buildRerankDocument(Document document) {
        Object rawMarkdown = document.getMetadata().get(META_RAW_MARKDOWN);
        String blockType = firstNonBlank(
                safe(document.getMetadata().get(META_BLOCK_TYPE)),
                safe(document.getMetadata().get(META_CHUNK_TYPE))
        );
        int headingLevel = intValue(
                firstNonBlank(
                        safe(document.getMetadata().get(META_HEADING_LEVEL)),
                        safe(document.getMetadata().get(META_LEVEL))
                ),
                6
        );
        String rawContent = rawMarkdown != null ? rawMarkdown.toString() : safe(document.getText());
        String content = truncate(rawContent, MAX_RERANK_CONTENT_CHARS);
        return """
                文档标题：%s
                节点标题：%s
                标题路径：%s
                标题层级：H%s
                内容类型：%s
                检索价值：%s
                内容摘要：%s
                Markdown 片段：
                %s
                """.formatted(
                firstNonBlank(
                        safe(document.getMetadata().get(META_ARTICLE_TITLE)),
                        safe(document.getMetadata().get(META_DOCUMENT_TITLE))
                ),
                safe(document.getMetadata().get(META_NODE_TITLE)),
                firstNonBlank(
                        safe(document.getMetadata().get(META_SECTION_PATH)),
                        safe(document.getMetadata().get(META_TITLE_PATH))
                ),
                headingLevel,
                describeBlockType(blockType),
                describeRetrievalValue(blockType, headingLevel),
                safe(document.getMetadata().get(META_CONTENT_PREVIEW)),
                safe(content)
        ).trim();
    }

    private String buildRerankUrl() {
        String normalizedBaseUrl = aiProperties.requireProvider(aiProperties.getRerank().getProvider()).getBaseUrl().trim();
        if (normalizedBaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl + "/rerank";
        }
        if (normalizedBaseUrl.endsWith("/")) {
            return normalizedBaseUrl + "v1/rerank";
        }
        return normalizedBaseUrl + "/v1/rerank";
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String content, int maxChars) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "\n[片段已截断]";
    }

    private String describeBlockType(String blockType) {
        return switch (blockType) {
            case "paragraph" -> "正文段落";
            case "list" -> "列表要点";
            case "quote" -> "引用内容";
            case "math" -> "数学公式";
            case "table" -> "表格数据";
            case "code" -> "代码示例";
            case "image" -> "图片说明";
            case "video-ref" -> "视频引用";
            case "html" -> "HTML 片段";
            case "section" -> "章节节点";
            case "section-part" -> "章节节点片段";
            case "content" -> "文档内容块";
            case "content-part" -> "文档内容片段";
            default -> StringUtils.hasText(blockType) ? blockType : "未标注";
        };
    }

    private String describeRetrievalValue(String blockType, int headingLevel) {
        if (headingLevel <= 2 && ("paragraph".equals(blockType) || "list".equals(blockType))) {
            return "高，章节主干内容，通常更接近文章核心主题";
        }
        return switch (blockType) {
            case "paragraph" -> "高，正文事实与解释性内容";
            case "list" -> "高，结论和步骤常以列表出现";
            case "math" -> "中高，适合公式、定义和推导问题";
            case "table" -> "中高，适合对比、枚举和结构化信息";
            case "quote" -> "中，适合作为补充论据或原话引用";
            case "code" -> "中，适合实现细节和示例问题";
            case "image", "video-ref", "html" -> "中低，更多用于补充上下文";
            case "section", "section-part" -> "高，适合章节主题、结构定位和全局概览问题";
            case "content", "content-part" -> "高，适合局部事实、定义和细节问答";
            default -> "中，通用片段";
        };
    }

    private record RerankHit(int index, double score) {
    }
}

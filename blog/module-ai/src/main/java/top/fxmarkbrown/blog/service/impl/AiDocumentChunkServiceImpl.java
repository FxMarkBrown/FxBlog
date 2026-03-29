package top.fxmarkbrown.blog.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiRagProperties;
import top.fxmarkbrown.blog.model.ai.AiDocumentChunk;
import top.fxmarkbrown.blog.service.AiDocumentChunkService;
import top.fxmarkbrown.blog.vo.ai.AiDocumentSourceAnchorVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTreeNodeVo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiDocumentChunkServiceImpl implements AiDocumentChunkService {

    private final AiRagProperties aiRagProperties;

    public AiDocumentChunkServiceImpl(AiRagProperties aiRagProperties) {
        this.aiRagProperties = aiRagProperties;
    }

    @Override
    public List<AiDocumentChunk> split(AiDocumentTaskDetailVo detail, AiDocumentTreeNodeVo root) {
        if (detail == null || detail.getTaskId() == null || root == null) {
            return List.of();
        }
        List<AiDocumentChunk> chunks = new ArrayList<>();
        List<AiDocumentTreeNodeVo> trail = new ArrayList<>();
        collectChunks(detail, root, trail, chunks, 0);
        return List.copyOf(chunks);
    }

    private void collectChunks(AiDocumentTaskDetailVo detail,
                               AiDocumentTreeNodeVo node,
                               List<AiDocumentTreeNodeVo> trail,
                               List<AiDocumentChunk> chunks,
                               int depth) {
        if (node == null) {
            return;
        }
        trail.add(node);
        if (!isRootNode(node)) {
            chunks.addAll(buildNodeChunks(detail, node, trail, depth));
        }
        List<AiDocumentTreeNodeVo> children = node.getChildren() == null ? List.of() : node.getChildren();
        for (AiDocumentTreeNodeVo child : children) {
            collectChunks(detail, child, trail, chunks, depth + 1);
        }
        trail.removeLast();
    }

    private List<AiDocumentChunk> buildNodeChunks(AiDocumentTaskDetailVo detail,
                                                  AiDocumentTreeNodeVo node,
                                                  List<AiDocumentTreeNodeVo> trail,
                                                  int depth) {
        String nodeBody = buildNodeBody(node);
        if (!StringUtils.hasText(nodeBody)) {
            return List.of();
        }
        String titlePath = buildTitlePath(trail);
        String childOutline = buildChildOutline(node);
        String baseText = buildRetrievalText(detail, node, titlePath, childOutline, nodeBody);
        int maxChunkChars = Math.max(aiRagProperties.getMaxChunkChars(), 400);
        List<String> parts = splitLongText(nodeBody, maxChunkChars);
        List<AiDocumentChunk> chunks = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            String part = parts.get(index);
            String chunkId = UUID.nameUUIDFromBytes((detail.getTaskId() + "|" + node.getId() + "|" + index)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            String chunkType = parts.size() == 1 ? safeType(node.getType()) : safeType(node.getType()) + "-part";
            String retrievalText = parts.size() == 1
                    ? baseText
                    : buildRetrievalText(detail, node, titlePath, childOutline, part);
            chunks.add(new AiDocumentChunk(
                    chunkId,
                    detail.getTaskId(),
                    node.getId(),
                    node.getParentId(),
                    safeType(node.getType()),
                    safeText(node.getTitle(), "未命名节点"),
                    titlePath,
                    safeLevel(node),
                    depth,
                    index,
                    chunkType,
                    part,
                    retrievalText,
                    buildPreview(part),
                    resolvePageStart(node.getSourceAnchors()),
                    resolvePageEnd(node.getSourceAnchors()),
                    node.getSourceAnchors() == null ? List.of() : List.copyOf(node.getSourceAnchors())
            ));
        }
        return chunks;
    }

    private String buildRetrievalText(AiDocumentTaskDetailVo detail,
                                      AiDocumentTreeNodeVo node,
                                      String titlePath,
                                      String childOutline,
                                      String body) {
        List<String> parts = new ArrayList<>();
        appendIfPresent(parts, "文档标题", detail == null ? null : detail.getTitle());
        appendIfPresent(parts, "节点标题", node == null ? null : node.getTitle());
        appendIfPresent(parts, "标题路径", titlePath);
        appendIfPresent(parts, "节点类型", safeType(node == null ? null : node.getType()));
        appendIfPresent(parts, "节点摘要", node == null ? null : node.getSummary());
        appendIfPresent(parts, "直接子节点", childOutline);
        appendIfPresent(parts, "页码范围", formatPageRange(node == null ? null : node.getSourceAnchors()));
        parts.add("节点内容：\n" + body.trim());
        return String.join("\n", parts);
    }

    private String buildNodeBody(AiDocumentTreeNodeVo node) {
        if (node == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(node.getMarkdown())) {
            parts.add(node.getMarkdown().trim());
        }
        if (StringUtils.hasText(node.getSummary()) && !parts.contains(node.getSummary().trim())) {
            parts.add(node.getSummary().trim());
        }
        if (parts.isEmpty() && StringUtils.hasText(node.getTitle())) {
            parts.add(node.getTitle().trim());
        }
        return String.join("\n\n", parts).trim();
    }

    private String buildTitlePath(List<AiDocumentTreeNodeVo> trail) {
        List<String> titles = new ArrayList<>();
        for (AiDocumentTreeNodeVo node : trail) {
            if (node == null || isRootNode(node) || !StringUtils.hasText(node.getTitle())) {
                continue;
            }
            titles.add(node.getTitle().trim());
        }
        return titles.isEmpty() ? "未分节" : String.join(" > ", titles);
    }

    private String buildChildOutline(AiDocumentTreeNodeVo node) {
        if (node == null || node.getChildren() == null || node.getChildren().isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (AiDocumentTreeNodeVo child : node.getChildren().stream().limit(6).toList()) {
            String line = safeText(child.getTitle(), safeText(child.getSummary(), ""));
            if (!StringUtils.hasText(line) && StringUtils.hasText(child.getMarkdown())) {
                line = buildPreview(child.getMarkdown());
            }
            if (StringUtils.hasText(line)) {
                lines.add("- " + line.trim());
            }
        }
        return String.join("\n", lines);
    }

    private List<String> splitLongText(String text, int maxChunkChars) {
        String normalized = safeText(text, "");
        if (!StringUtils.hasText(normalized) || normalized.length() <= maxChunkChars) {
            return StringUtils.hasText(normalized) ? List.of(normalized) : List.of();
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String trimmed = paragraph.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (!current.isEmpty() && current.length() + trimmed.length() + 2 > maxChunkChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result.isEmpty() ? List.of(normalized.substring(0, maxChunkChars)) : List.copyOf(result);
    }

    private String buildPreview(String text) {
        String preview = safeText(text, "").replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (preview.length() > 120) {
            return preview.substring(0, 120) + "...";
        }
        return preview;
    }

    private boolean isRootNode(AiDocumentTreeNodeVo node) {
        return "document".equalsIgnoreCase(safeType(node == null ? null : node.getType()))
                && !StringUtils.hasText(node == null ? null : node.getParentId());
    }

    private String safeType(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "content";
    }

    private int safeLevel(AiDocumentTreeNodeVo node) {
        return node == null || node.getLevel() == null ? 0 : Math.max(node.getLevel(), 0);
    }

    private Integer resolvePageStart(List<AiDocumentSourceAnchorVo> anchors) {
        return anchors == null ? null : anchors.stream()
                .map(AiDocumentSourceAnchorVo::getPage)
                .filter(page -> page != null && page > 0)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private Integer resolvePageEnd(List<AiDocumentSourceAnchorVo> anchors) {
        return anchors == null ? null : anchors.stream()
                .map(AiDocumentSourceAnchorVo::getPage)
                .filter(page -> page != null && page > 0)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private String formatPageRange(List<AiDocumentSourceAnchorVo> anchors) {
        Integer pageStart = resolvePageStart(anchors);
        Integer pageEnd = resolvePageEnd(anchors);
        if (pageStart == null) {
            return "";
        }
        return pageStart.equals(pageEnd) ? "第 " + pageStart + " 页" : "第 " + pageStart + "-" + pageEnd + " 页";
    }

    private void appendIfPresent(List<String> lines, String label, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(label + "：" + value.trim());
        }
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}

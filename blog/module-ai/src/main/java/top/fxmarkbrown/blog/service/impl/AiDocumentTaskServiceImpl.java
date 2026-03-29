package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.config.ai.AiProperties;
import top.fxmarkbrown.blog.dto.ai.AiDocumentNodeAskDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskCreateDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskRenameDto;
import top.fxmarkbrown.blog.entity.SysAiDocumentResult;
import top.fxmarkbrown.blog.entity.SysAiDocumentTask;
import top.fxmarkbrown.blog.mapper.SysAiDocumentResultMapper;
import top.fxmarkbrown.blog.mapper.SysAiDocumentTaskMapper;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;
import top.fxmarkbrown.blog.service.AiChatModelService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.service.AiDocumentTaskService;
import top.fxmarkbrown.blog.utils.HttpUtil;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeAnswerVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeCitationVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentParseResultVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentSourceAnchorVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskListVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTreeNodeVo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentTaskServiceImpl implements AiDocumentTaskService {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final String MOCK_FIXTURE_MARKDOWN = "mock/mineru/current/full.md";
    private static final String MOCK_FIXTURE_CONTENT_LIST = "mock/mineru/current/content_list.json";
    private static final String MOCK_FIXTURE_PAYLOAD = "mock/mineru/current/task-detail.json";

    private final AiProperties aiProperties;
    private final AiChatModelService aiChatModelService;
    private final AiQuotaCoreService aiQuotaCoreService;
    private final SysAiDocumentTaskMapper documentTaskMapper;
    private final SysAiDocumentResultMapper documentResultMapper;

    @Override
    public List<AiDocumentTaskListVo> listTasks() {
        Long userId = StpUtil.getLoginIdAsLong();
        return documentTaskMapper.selectList(new LambdaQueryWrapper<SysAiDocumentTask>()
                        .eq(SysAiDocumentTask::getUserId, userId)
                        .orderByDesc(SysAiDocumentTask::getUpdateTime)
                        .orderByDesc(SysAiDocumentTask::getId))
                .stream()
                .map(this::toListVo)
                .toList();
    }

    @Override
    public AiDocumentTaskDetailVo getTaskDetail(Long taskId) {
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        return copyDetail(aggregate.detail());
    }

    @Override
    public AiDocumentParseResultVo getTaskResult(Long taskId) {
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        return copyResult(aggregate.result());
    }

    @Override
    public AiDocumentTaskDetailVo createTask(AiDocumentTaskCreateDto createDto) {
        AiProperties.Document document = aiProperties.getDocument();
        if (document == null || !document.isEnabled()) {
            throw new IllegalStateException("文档任务未启用");
        }

        AiProperties.Mineru mineru = document.getMineru();
        if (mineru == null || !mineru.isEnabled() || mineru.isMockMode()) {
            throw new IllegalStateException("文档真实解析尚未启用，请先完成 MinerU 配置");
        }
        return createRealMineruTask(createDto);
    }

    @Override
    public AiDocumentTaskDetailVo createLocalMockTask(AiDocumentTaskCreateDto createDto) {
        LocalDateTime now = LocalDateTime.now();
        String title = safeText(createDto == null ? null : createDto.getTitle(), "本地 Mock 文档任务");
        String fileName = safeText(createDto == null ? null : createDto.getFileName(), "mock-document.pdf");
        String sourceUrl = safeText(createDto == null ? null : createDto.getSourceUrl(), null);

        SysAiDocumentTask task = SysAiDocumentTask.builder()
                .userId(StpUtil.getLoginIdAsLong())
                .sourceFileId(safeText(createDto == null ? null : createDto.getSourceFileId(), null))
                .title(title)
                .status("PARSED")
                .provider("local-mock")
                .fileName(fileName)
                .sourceUrl(sourceUrl)
                .expireAt(resolveExpireAt(now))
                .createTime(now)
                .updateTime(now)
                .build();
        documentTaskMapper.insert(task);

        DocumentTaskAggregate aggregate = buildDynamicAggregate(task.getId(), title, fileName, sourceUrl, now);
        applyMockParseResult(aggregate);
        persistAggregate(aggregate, true);
        log.info("本地 Mock 文档任务已创建, taskId={}, title={}", task.getId(), title);
        return getTaskDetail(task.getId());
    }

    @Override
    public AiDocumentTaskDetailVo renameTask(Long taskId, AiDocumentTaskRenameDto renameDto) {
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        String normalizedTitle = safeText(renameDto == null ? null : renameDto.getTitle(), null);
        if (!StringUtils.hasText(normalizedTitle)) {
            throw new IllegalStateException("任务标题不能为空");
        }
        if (normalizedTitle.length() > 255) {
            throw new IllegalStateException("任务标题不能超过 255 个字符");
        }

        aggregate.detail().setTitle(normalizedTitle);
        aggregate.detail().setUpdateTime(LocalDateTime.now());
        aggregate.result().setTitle(normalizedTitle);
        if (aggregate.result().getRoot() != null) {
            aggregate.result().getRoot().setTitle(normalizedTitle);
        }
        persistAggregate(aggregate, false);
        return copyDetail(aggregate.detail());
    }

    @Override
    public void deleteTask(Long taskId) {
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        documentResultMapper.deleteById(aggregate.detail().getTaskId());
        documentTaskMapper.deleteById(aggregate.detail().getTaskId());
    }

    @Override
    public void handleMineruCallback(String checksum, String content) {
        AiProperties.Document document = aiProperties.getDocument();
        if (document == null || !document.isEnabled()) {
            throw new IllegalStateException("文档任务未启用");
        }
        AiProperties.Mineru mineru = document.getMineru();
        if (mineru == null || !mineru.isEnabled()) {
            throw new IllegalStateException("MinerU 未启用");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("callback content 不能为空");
        }
        verifyCallbackSignature(mineru, checksum, content);

        JsonNode payloadRoot = JsonUtil.readTree(content);
        JsonNode dataNode = payloadRoot != null && payloadRoot.has("data") ? payloadRoot.get("data") : payloadRoot;
        String remoteTaskId = firstText(dataNode,
                path("task_id"),
                path("taskId"),
                path("id"));
        if (!StringUtils.hasText(remoteTaskId)) {
            throw new IllegalStateException("callback content 缺少 task_id");
        }

        SysAiDocumentTask task = documentTaskMapper.selectOne(new LambdaQueryWrapper<SysAiDocumentTask>()
                .eq(SysAiDocumentTask::getRemoteTaskId, remoteTaskId)
                .last("limit 1"));
        if (task == null) {
            throw new IllegalStateException("未找到对应文档任务, remoteTaskId=" + remoteTaskId);
        }

        SysAiDocumentResult result = documentResultMapper.selectById(task.getId());
        DocumentTaskAggregate aggregate = toAggregate(task, result);
        applyMineruPayload(aggregate, dataNode, content, mineru);
        persistAggregate(aggregate, false);
        log.info("MinerU callback 已处理, taskId={}, remoteTaskId={}, status={}",
                aggregate.detail().getTaskId(), remoteTaskId, aggregate.detail().getStatus());
    }

    @Override
    public AiDocumentNodeAnswerVo askNode(Long taskId, String nodeId, AiDocumentNodeAskDto askDto) {
        if (!aiProperties.isEnabled()) {
            throw new IllegalStateException("AI 功能未启用");
        }
        String question = normalizeQuestion(askDto);
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        AiDocumentTreeNodeVo root = aggregate.result().getRoot();
        AiDocumentTreeNodeVo currentNode = findNode(root, nodeId);
        if (currentNode == null) {
            throw new IllegalStateException("未找到目标节点: " + nodeId);
        }

        long currentUserId = StpUtil.getLoginIdAsLong();
        aiQuotaCoreService.assertRequestQuota(currentUserId);

        List<AiDocumentTreeNodeVo> contextNodes = collectContextNodes(root, currentNode, askDto);
        String contextPayload = buildContextPayload(aggregate.detail(), currentNode, contextNodes);

        AiResolvedChatModel resolvedChatModel = aiChatModelService.getDefaultModel();
        ChatClient chatClient = aiChatModelService.getChatClient(resolvedChatModel);

        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(buildNodeAskSystemPrompt())
                    .user(buildNodeAskUserPrompt(aggregate.detail(), currentNode, question, contextPayload))
                    .options(OpenAiChatOptions.builder()
                            .model(resolvedChatModel.modelName())
                            .temperature(Math.min(resolvedChatModel.temperature(), 0.35D))
                            .build())
                    .call()
                    .chatResponse();

            String answer = extractAnswer(chatResponse);
            if (!StringUtils.hasText(answer)) {
                throw new IllegalStateException("AI 未返回有效内容");
            }

            Usage usage = chatResponse.getMetadata().getUsage();
            aiQuotaCoreService.consumeTokens(currentUserId, resolveConsumedTokens(question, answer, usage));

            AiDocumentNodeAnswerVo vo = new AiDocumentNodeAnswerVo();
            vo.setTaskId(taskId);
            vo.setNodeId(currentNode.getId());
            vo.setQuestion(question);
            vo.setAnswer(answer.trim());
            vo.setModelId(resolvedChatModel.modelId());
            vo.setContextNodeIds(contextNodes.stream().map(AiDocumentTreeNodeVo::getId).toList());
            vo.setCitations(buildCitations(contextNodes, currentNode));
            return vo;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("文档节点问答失败, taskId={}, nodeId={}", taskId, nodeId, ex);
            throw new IllegalStateException("文档节点问答失败，请稍后重试");
        }
    }

    private AiDocumentTaskDetailVo createRealMineruTask(AiDocumentTaskCreateDto createDto) {
        AiProperties.Mineru mineru = aiProperties.getDocument().getMineru();
        String sourceUrl = safeText(createDto == null ? null : createDto.getSourceUrl(), null);
        if (!StringUtils.hasText(sourceUrl)) {
            throw new IllegalStateException("真实 MinerU 任务要求 sourceUrl 可用");
        }
        validateMineruSourceUrl(sourceUrl);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("url", sourceUrl);
        requestBody.put("is_ocr", mineru.getOcr());
        requestBody.put("enable_formula", mineru.getEnableFormula());
        requestBody.put("enable_table", mineru.getEnableTable());
        if (StringUtils.hasText(mineru.getLanguage())) {
            requestBody.put("language", mineru.getLanguage().trim());
        }
        if (StringUtils.hasText(mineru.getCallbackUrl())) {
            requestBody.put("callback", mineru.getCallbackUrl().trim());
            if (!StringUtils.hasText(mineru.getCallbackSeed())) {
                throw new IllegalStateException("已配置 MinerU callback-url，但缺少 callback-seed");
            }
            requestBody.put("seed", mineru.getCallbackSeed().trim());
        }
        if (StringUtils.hasText(createDto == null ? null : createDto.getFileName())) {
            requestBody.put("filename", createDto.getFileName().trim());
        }
        if (StringUtils.hasText(createDto == null ? null : createDto.getTitle())) {
            requestBody.put("data_id", createDto.getTitle().trim());
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(mineru.getApiKey())) {
            headers.put("Authorization", "Bearer " + mineru.getApiKey().trim());
        }

        try {
            String response = HttpUtil.postJson(buildMineruSubmitUrl(mineru), JsonUtil.toJsonString(requestBody),
                    mineru.getTimeoutMillis(), headers);
            JsonNode root = JsonUtil.readTree(response);
            Integer responseCode = firstInt(root, path("code"));
            if (responseCode != null && responseCode != 0) {
                throw new IllegalStateException("MinerU 创建任务失败: " + safeText(firstText(root, path("msg")), "未知错误"));
            }
            String remoteTaskId = firstText(root,
                    path("data", "task_id"),
                    path("data", "taskId"),
                    path("data", "id"),
                    path("data"),
                    path("task_id"),
                    path("taskId"),
                    path("id"));
            if (!StringUtils.hasText(remoteTaskId)) {
                throw new IllegalStateException("MinerU 创建任务失败: 未返回 task_id");
            }

            LocalDateTime now = LocalDateTime.now();
            SysAiDocumentTask task = SysAiDocumentTask.builder()
                    .userId(StpUtil.getLoginIdAsLong())
                    .sourceFileId(safeText(createDto == null ? null : createDto.getSourceFileId(), null))
                    .title(safeText(createDto == null ? null : createDto.getTitle(), "未命名文档任务"))
                    .status("SUBMITTED")
                    .provider("mineru")
                    .remoteTaskId(safeText(remoteTaskId, null))
                    .fileName(safeText(createDto == null ? null : createDto.getFileName(), "untitled.pdf"))
                    .sourceUrl(sourceUrl)
                    .expireAt(resolveExpireAt(now))
                    .createTime(now)
                    .updateTime(now)
                    .build();
            documentTaskMapper.insert(task);

            DocumentTaskAggregate aggregate = buildDynamicAggregate(
                    task.getId(),
                    task.getTitle(),
                    task.getFileName(),
                    task.getSourceUrl(),
                    now
            );
            aggregate.detail().setStatus("SUBMITTED");
            aggregate.detail().setRemoteTaskId(task.getRemoteTaskId());
            aggregate.detail().setSourceFileId(task.getSourceFileId());
            aggregate.detail().setExpireAt(task.getExpireAt());
            aggregate.result().setMarkdown("""
                    # %s

                    文档已提交，当前等待解析完成。

                    远端任务号：%s
                    """.formatted(aggregate.detail().getTitle(), safeText(remoteTaskId, "-")));
            persistAggregate(aggregate, true);
            log.info("MinerU 文档任务已创建, taskId={}, remoteTaskId={}, title={}",
                    task.getId(), safeText(task.getRemoteTaskId(), "<empty>"), task.getTitle());
            return getTaskDetail(task.getId());
        } catch (Exception ex) {
            log.error("MinerU 文档任务创建失败, title={}", createDto == null ? null : createDto.getTitle(), ex);
            throw new IllegalStateException("MinerU 文档任务创建失败: " + ex.getMessage(), ex);
        }
    }

    private String buildMineruSubmitUrl(AiProperties.Mineru mineru) {
        String baseUrl = safeText(mineru.getBaseUrl(), "");
        String submitPath = safeText(mineru.getSubmitPath(), "/api/v4/extract/task");
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("blog.ai.document.mineru.base-url 未配置");
        }
        if (baseUrl.endsWith("/") && submitPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + submitPath;
        }
        if (!baseUrl.endsWith("/") && !submitPath.startsWith("/")) {
            return baseUrl + "/" + submitPath;
        }
        return baseUrl + submitPath;
    }

    private DocumentTaskAggregate requireAggregate(Long taskId) {
        if (taskId == null) {
            throw new IllegalStateException("文档任务 ID 不能为空");
        }
        Long currentUserId = StpUtil.getLoginIdAsLong();
        SysAiDocumentTask task = documentTaskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getUserId(), currentUserId)) {
            throw new IllegalStateException("未找到文档任务: " + taskId);
        }
        SysAiDocumentResult result = documentResultMapper.selectById(taskId);
        return toAggregate(task, result);
    }

    private void persistAggregate(DocumentTaskAggregate aggregate, boolean insertResultIfAbsent) {
        SysAiDocumentTask taskEntity = toTaskEntity(aggregate.detail(), aggregate.taskEntity());
        documentTaskMapper.updateById(taskEntity);

        SysAiDocumentResult resultEntity = toResultEntity(aggregate.result(), aggregate.resultEntity());
        if (insertResultIfAbsent || documentResultMapper.selectById(resultEntity.getTaskId()) == null) {
            documentResultMapper.insert(resultEntity);
        } else {
            documentResultMapper.updateById(resultEntity);
        }
    }

    private AiDocumentTaskListVo toListVo(SysAiDocumentTask task) {
        AiDocumentTaskListVo vo = new AiDocumentTaskListVo();
        vo.setTaskId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setSourceFileId(task.getSourceFileId());
        vo.setStatus(task.getStatus());
        vo.setFileName(task.getFileName());
        vo.setPageCount(task.getPageCount());
        vo.setParsed(Objects.equals(task.getStatus(), "PARSED"));
        vo.setExpireAt(task.getExpireAt());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private AiDocumentTaskDetailVo copyDetail(AiDocumentTaskDetailVo source) {
        return JsonUtil.convertValue(source, AiDocumentTaskDetailVo.class);
    }

    private AiDocumentParseResultVo copyResult(AiDocumentParseResultVo source) {
        return JsonUtil.convertValue(source, AiDocumentParseResultVo.class);
    }

    private DocumentTaskAggregate buildDynamicAggregate(Long taskId, String title, String fileName, String sourceUrl, LocalDateTime now) {
        AiDocumentTreeNodeVo root = createNode("doc-" + taskId + "-root", null, "document", title, 0,
                "这是一个新创建的文档任务，当前仍使用原型结构树展示。", """
                        # %s

                        当前任务已经创建，后续这里会替换为 MinerU 返回的真实结构树。
                        """.formatted(title), true);
        root.getChildren().add(createNode("doc-" + taskId + "-section-1", root.getId(), "section", "待解析结构", 1,
                "等待真实解析完成后，将替换为标题层级、内容块与原文锚点。", """
                        ## 待解析结构

                        当前节点是动态占位节点，用于验证画布和工作台流程。
                        """, false));

        AiDocumentTaskDetailVo detail = new AiDocumentTaskDetailVo();
        detail.setTaskId(taskId);
        detail.setTitle(title);
        detail.setStatus("PARSED");
        detail.setFileName(fileName);
        detail.setSourceUrl(sourceUrl);
        detail.setMarkdownUrl(resolveMarkdownResultPath(taskId));
        detail.setPageCount(1);
        detail.setRootNodeId(root.getId());
        detail.setExpireAt(resolveExpireAt(now));
        detail.setCreateTime(now);
        detail.setUpdateTime(now);

        AiDocumentParseResultVo result = new AiDocumentParseResultVo();
        result.setTaskId(taskId);
        result.setTitle(title);
        result.setMarkdown("""
                # %s

                当前任务已创建，等待解析结果。
                """.formatted(title));
        result.setRoot(root);
        return new DocumentTaskAggregate(
                detail,
                result,
                SysAiDocumentTask.builder().id(taskId).build(),
                emptyResultEntity(taskId)
        );
    }

    private void applyMockParseResult(DocumentTaskAggregate aggregate) {
        MockFixturePayload fixturePayload = loadMockFixturePayload();
        String markdown = fixturePayload.markdown();
        AiDocumentTreeNodeVo root;
        if (StringUtils.hasText(fixturePayload.contentListJson())) {
            JsonNode contentListNode = JsonUtil.readTree(fixturePayload.contentListJson());
            root = contentListNode != null && contentListNode.isArray() && !contentListNode.isEmpty()
                    ? buildTreeFromContentList(aggregate.detail().getTaskId(), aggregate.detail().getTitle(), contentListNode, markdown)
                    : buildTreeFromMarkdown(aggregate.detail().getTaskId(), aggregate.detail().getTitle(), markdown);
        } else {
            root = buildTreeFromMarkdown(aggregate.detail().getTaskId(), aggregate.detail().getTitle(), markdown);
        }
        aggregate.detail().setStatus("PARSED");
        aggregate.detail().setPageCount(fixturePayload.pageCount());
        aggregate.detail().setRootNodeId(root.getId());
        aggregate.detail().setMarkdownUrl(resolveMarkdownResultPath(aggregate.detail().getTaskId()));
        aggregate.detail().setUpdateTime(LocalDateTime.now());
        aggregate.result().setTitle(aggregate.detail().getTitle());
        aggregate.result().setMarkdown(markdown);
        aggregate.result().setRoot(root);
        aggregate.resultEntity().setMarkdown(markdown);
        aggregate.resultEntity().setRootJson(JsonUtil.toJsonString(root));
        aggregate.resultEntity().setContentListJson(fixturePayload.contentListJson());
        aggregate.resultEntity().setRawPayloadJson(fixturePayload.rawPayloadJson());
    }

    private MockFixturePayload loadMockFixturePayload() {
        String markdown = readClasspathText(MOCK_FIXTURE_MARKDOWN);
        String contentListJson = readClasspathText(MOCK_FIXTURE_CONTENT_LIST);
        String rawPayloadJson = readClasspathText(MOCK_FIXTURE_PAYLOAD);

        if (!StringUtils.hasText(markdown) && !StringUtils.hasText(contentListJson)) {
            throw new IllegalStateException("本地 Mock fixture 不存在，请先执行 tools/fetch_mineru_fixture.py 拉取真实 MinerU 数据");
        }

        Integer pageCount = firstInt(JsonUtil.readTree(rawPayloadJson),
                path("data", "extract_progress", "total_pages"),
                path("extract_progress", "total_pages"),
                path("data", "result", "page_count"),
                path("data", "page_count"),
                path("page_count"));

        return new MockFixturePayload(
                StringUtils.hasText(markdown) ? markdown.trim() : "",
                StringUtils.hasText(contentListJson) ? contentListJson : null,
                StringUtils.hasText(rawPayloadJson)
                        ? rawPayloadJson
                        : JsonUtil.toJsonString(Map.of("provider", "local-mock", "fixture", "external-mineru")),
                pageCount != null && pageCount > 0 ? pageCount : 6
        );
    }

    private String readClasspathText(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Mock fixture 失败: " + classpathLocation, ex);
        }
    }

    private void applyMineruPayload(DocumentTaskAggregate aggregate, JsonNode payloadRoot, String rawPayload, AiProperties.Mineru mineru) {
        JsonNode dataNode = payloadRoot != null && payloadRoot.has("data") ? payloadRoot.get("data") : payloadRoot;
        String normalizedStatus = normalizeRemoteStatus(firstText(dataNode,
                path("state"),
                path("status"),
                path("task", "status")));

        String markdown = firstText(dataNode,
                path("full_md"),
                path("result", "full_md"),
                path("result", "fullMd"),
                path("result", "markdown"),
                path("markdown"));
        if (StringUtils.hasText(markdown)) {
            aggregate.result().setMarkdown(markdown.trim());
        }

        JsonNode contentListNode = firstNode(dataNode,
                path("content_list"),
                path("result", "content_list"),
                path("result", "contentList"));

        AiDocumentTreeNodeVo remoteTree = null;
        if (contentListNode != null && contentListNode.isArray() && !contentListNode.isEmpty()) {
            remoteTree = buildTreeFromContentList(aggregate.detail().getTaskId(), aggregate.detail().getTitle(), contentListNode, markdown);
        } else if (isParsedMarkdownAvailable(markdown)) {
            remoteTree = buildTreeFromMarkdown(aggregate.detail().getTaskId(), aggregate.detail().getTitle(), markdown);
        }

        if (remoteTree != null) {
            aggregate.result().setRoot(remoteTree);
            aggregate.detail().setRootNodeId(remoteTree.getId());
        }
        if (contentListNode != null && !contentListNode.isNull()) {
            aggregate.resultEntity().setContentListJson(contentListNode.toString());
        }
        aggregate.resultEntity().setRawPayloadJson(rawPayload);

        Integer pageCount = firstInt(dataNode,
                path("extract_progress", "total_pages"),
                path("result", "page_count"),
                path("result", "pageCount"),
                path("page_count"));
        if (pageCount != null && pageCount > 0) {
            aggregate.detail().setPageCount(pageCount);
        }

        String fullZipUrl = firstText(dataNode, path("full_zip_url"));
        if (StringUtils.hasText(fullZipUrl)
                && (!StringUtils.hasText(aggregate.result().getMarkdown())
                || !StringUtils.hasText(aggregate.resultEntity().getContentListJson()))) {
            applyZipResultIfNecessary(aggregate, fullZipUrl, mineru);
        }

        if (!StringUtils.hasText(aggregate.result().getMarkdown()) && StringUtils.hasText(aggregate.resultEntity().getMarkdown())) {
            aggregate.result().setMarkdown(aggregate.resultEntity().getMarkdown());
        }
        if ((contentListNode == null || contentListNode.isNull()) && StringUtils.hasText(aggregate.resultEntity().getContentListJson())) {
            contentListNode = JsonUtil.readTree(aggregate.resultEntity().getContentListJson());
        }
        if ((remoteTree == null || aggregate.result().getRoot() == null) && contentListNode != null && contentListNode.isArray() && !contentListNode.isEmpty()) {
            remoteTree = buildTreeFromContentList(aggregate.detail().getTaskId(), aggregate.detail().getTitle(),
                    contentListNode, aggregate.result().getMarkdown());
            aggregate.result().setRoot(remoteTree);
            aggregate.detail().setRootNodeId(remoteTree.getId());
        }
        if ((remoteTree == null || aggregate.result().getRoot() == null)
                && isParsedMarkdownAvailable(aggregate.result().getMarkdown())) {
            remoteTree = buildTreeFromMarkdown(aggregate.detail().getTaskId(), aggregate.detail().getTitle(),
                    aggregate.result().getMarkdown());
            aggregate.result().setRoot(remoteTree);
            aggregate.detail().setRootNodeId(remoteTree.getId());
        }
        if (StringUtils.hasText(fullZipUrl)) {
            aggregate.resultEntity().setRawPayloadJson(
                    mergeRawPayloadWithZipUrl(aggregate.resultEntity().getRawPayloadJson(), rawPayload, fullZipUrl)
            );
        }

        aggregate.detail().setExpireAt(resolveExpireAt(LocalDateTime.now()));
        aggregate.detail().setMarkdownUrl(resolveMarkdownResultPath(aggregate.detail().getTaskId()));
        aggregate.detail().setUpdateTime(LocalDateTime.now());
        aggregate.taskEntity().setLastPolledAt(LocalDateTime.now());

        if ("FAILED".equals(normalizedStatus)) {
            aggregate.detail().setStatus("FAILED");
        } else if ("PARSED".equals(normalizedStatus)) {
            boolean materialized = hasMaterializedParseResult(aggregate);
            aggregate.detail().setStatus(materialized ? "PARSED" : "PROCESSING");
            if (!materialized) {
                throw new IllegalStateException("MinerU 回调已完成，但结果文件尚未成功落盘");
            }
        } else if (StringUtils.hasText(normalizedStatus)) {
            aggregate.detail().setStatus(normalizedStatus);
        }
    }

    private void applyZipResultIfNecessary(DocumentTaskAggregate aggregate, String fullZipUrl, AiProperties.Mineru mineru) {
        try {
            ZipDocumentPayload payload = downloadZipPayload(fullZipUrl, mineru);
            if (payload == null) {
                throw new IllegalStateException("MinerU 结果压缩包为空");
            }
            if (StringUtils.hasText(payload.markdown())) {
                aggregate.result().setMarkdown(payload.markdown().trim());
                aggregate.resultEntity().setMarkdown(payload.markdown().trim());
            }
            if (StringUtils.hasText(payload.contentListJson())) {
                aggregate.resultEntity().setContentListJson(payload.contentListJson());
            }
        } catch (Exception ex) {
            log.warn("下载 MinerU full_zip_url 失败, taskId={}, zipUrl={}",
                    aggregate.detail().getTaskId(), fullZipUrl, ex);
            throw new IllegalStateException("下载 MinerU 结果压缩包失败", ex);
        }
    }

    private ZipDocumentPayload downloadZipPayload(String fullZipUrl, AiProperties.Mineru mineru) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(mineru.getApiKey())) {
            headers.put("Authorization", "Bearer " + mineru.getApiKey().trim());
        }
        byte[] zipBytes = HttpUtil.getBytes(fullZipUrl, headers);
        if (zipBytes == null || zipBytes.length == 0) {
            return null;
        }
        String markdown = null;
        String contentListJson = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = safeText(entry.getName(), "");
                byte[] contentBytes = zipInputStream.readAllBytes();
                if (entryName.endsWith("/full.md") || "full.md".equals(entryName)) {
                    markdown = new String(contentBytes, StandardCharsets.UTF_8);
                } else if (entryName.endsWith("/content_list.json") || "content_list.json".equals(entryName)) {
                    contentListJson = new String(contentBytes, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("解析 MinerU 结果压缩包失败", ex);
        }
        return new ZipDocumentPayload(markdown, contentListJson);
    }

    private AiDocumentTreeNodeVo createNode(String id, String parentId, String type, String title, Integer level,
                                            String summary, String markdown, boolean expandable) {
        AiDocumentTreeNodeVo node = new AiDocumentTreeNodeVo();
        node.setId(id);
        node.setParentId(parentId);
        node.setType(type);
        node.setTitle(title);
        node.setLevel(level);
        node.setSummary(summary);
        node.setMarkdown(markdown);
        node.setExpandable(expandable);
        node.setChildren(new ArrayList<>());
        node.setSourceAnchors(new ArrayList<>());
        return node;
    }

    private AiDocumentSourceAnchorVo anchor(Integer page, List<Double> bbox, String textSnippet) {
        AiDocumentSourceAnchorVo anchor = new AiDocumentSourceAnchorVo();
        anchor.setPage(page);
        anchor.setBbox(bbox);
        anchor.setTextSnippet(textSnippet);
        return anchor;
    }

    private AiDocumentTreeNodeVo buildTreeFromContentList(Long taskId, String title, JsonNode contentListNode, String markdown) {
        AiDocumentTreeNodeVo root = createNode("doc-" + taskId + "-root", null, "document", safeText(title, "未命名文档"), 0,
                "根据 MinerU content_list 生成的结构树。", buildRootMarkdown(title, markdown), true);
        List<AiDocumentTreeNodeVo> headingStack = new ArrayList<>();
        int contentCounter = 0;
        int headingCounter = 0;

        for (JsonNode item : contentListNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            String text = extractContentText(item);
            int level = normalizeHeadingLevel(item.path("text_level").asInt(0));
            Integer page = item.hasNonNull("page_idx") ? item.get("page_idx").asInt() + 1 : null;
            List<Double> bbox = extractBbox(item);

            if (StringUtils.hasText(text) && level > 0) {
                AiDocumentTreeNodeVo headingNode = createNode(
                        "doc-" + taskId + "-heading-" + (++headingCounter),
                        null,
                        "section",
                        text.trim(),
                        level,
                        summarizeText(text),
                        "#".repeat(level) + " " + text.trim(),
                        true
                );
                attachAnchor(headingNode, page, bbox, text);

                while (headingStack.size() >= level) {
                    headingStack.removeLast();
                }
                AiDocumentTreeNodeVo parent = headingStack.isEmpty() ? root : headingStack.getLast();
                headingNode.setParentId(parent.getId());
                parent.getChildren().add(headingNode);
                headingStack.add(headingNode);
                continue;
            }

            if (!StringUtils.hasText(text)) {
                continue;
            }

            AiDocumentTreeNodeVo parent = headingStack.isEmpty() ? root : headingStack.getLast();
            String contentType = safeText(textAt(item, "type"), "content");
            AiDocumentTreeNodeVo contentNode = createNode(
                    "doc-" + taskId + "-content-" + (++contentCounter),
                    parent.getId(),
                    contentType,
                    summarizeTitleByType(contentType, text),
                    Math.max(1, headingStack.size() + 1),
                    summarizeText(text),
                    toMarkdownByType(contentType, text),
                    false
            );
            attachAnchor(contentNode, page, bbox, text);
            parent.getChildren().add(contentNode);
        }

        finalizeExpandability(root);
        return root;
    }

    private AiDocumentTreeNodeVo buildTreeFromMarkdown(Long taskId, String title, String markdown) {
        AiDocumentTreeNodeVo root = createNode("doc-" + taskId + "-root", null, "document", safeText(title, "未命名文档"), 0,
                "根据 Markdown 标题层级生成的结构树。", buildRootMarkdown(title, markdown), true);
        if (!StringUtils.hasText(markdown)) {
            return root;
        }

        List<AiDocumentTreeNodeVo> headingStack = new ArrayList<>();
        StringBuilder blockBuffer = new StringBuilder();
        int headingCounter = 0;
        int contentCounter = 0;

        for (String line : markdown.split("\\R")) {
            java.util.regex.Matcher matcher = HEADING_PATTERN.matcher(line.trim());
            if (!matcher.matches()) {
                blockBuffer.append(line).append('\n');
                continue;
            }

            contentCounter = flushMarkdownBlock(taskId, headingStack, root, blockBuffer, contentCounter);

            int level = matcher.group(1).length();
            String headingTitle = matcher.group(2).trim();
            AiDocumentTreeNodeVo headingNode = createNode(
                    "doc-" + taskId + "-heading-" + (++headingCounter),
                    null,
                    "section",
                    headingTitle,
                    level,
                    headingTitle,
                    line.trim(),
                    true
            );

            while (headingStack.size() >= level) {
                headingStack.removeLast();
            }
            AiDocumentTreeNodeVo parent = headingStack.isEmpty() ? root : headingStack.getLast();
            headingNode.setParentId(parent.getId());
            parent.getChildren().add(headingNode);
            headingStack.add(headingNode);
        }

        flushMarkdownBlock(taskId, headingStack, root, blockBuffer, contentCounter);
        finalizeExpandability(root);
        return root;
    }

    private int flushMarkdownBlock(Long taskId, List<AiDocumentTreeNodeVo> headingStack, AiDocumentTreeNodeVo root,
                                   StringBuilder blockBuffer, int contentCounter) {
        String content = blockBuffer.toString().trim();
        blockBuffer.setLength(0);
        if (!StringUtils.hasText(content)) {
            return contentCounter;
        }
        AiDocumentTreeNodeVo parent = headingStack.isEmpty() ? root : headingStack.getLast();
        AiDocumentTreeNodeVo contentNode = createNode(
                "doc-" + taskId + "-content-" + (++contentCounter),
                parent.getId(),
                "content",
                summarizeTitleByType("content", content),
                Math.max(1, headingStack.size() + 1),
                summarizeText(content),
                content,
                false
        );
        parent.getChildren().add(contentNode);
        return contentCounter;
    }

    private void finalizeExpandability(AiDocumentTreeNodeVo node) {
        if (node == null) {
            return;
        }
        if (node.getChildren() == null) {
            node.setChildren(new ArrayList<>());
        }
        for (AiDocumentTreeNodeVo child : node.getChildren()) {
            finalizeExpandability(child);
        }
        node.setExpandable(!node.getChildren().isEmpty());
    }

    private void attachAnchor(AiDocumentTreeNodeVo node, Integer page, List<Double> bbox, String text) {
        if (node == null || page == null || page <= 0 || bbox == null || bbox.size() < 4) {
            return;
        }
        node.getSourceAnchors().add(anchor(page, bbox, summarizeText(text)));
    }

    private String buildNodeAskSystemPrompt() {
        return """
                你是博客文档工作台内的节点问答助手。
                你的任务是仅依据给定的文档节点上下文回答问题，不要臆造未出现在上下文中的事实。
                回答要求：
                1. 优先直接回答，再补充依据。
                2. 如果上下文不足，明确说明“当前节点上下文不足以确定”。
                3. 不要编造页码、作者意图或额外章节。
                4. 回答保持中文，简洁但信息完整。
                """;
    }

    private String buildNodeAskUserPrompt(AiDocumentTaskDetailVo detail, AiDocumentTreeNodeVo currentNode,
                                          String question, String contextPayload) {
        return """
                文档标题：%s
                当前节点：%s
                当前节点类型：%s

                用户问题：
                %s

                文档上下文：
                %s
                """.formatted(
                safeText(detail == null ? null : detail.getTitle(), "未命名文档"),
                safeText(currentNode == null ? null : currentNode.getTitle(), "未命名节点"),
                safeText(currentNode == null ? null : currentNode.getType(), "section"),
                question,
                contextPayload
        );
    }

    private List<AiDocumentTreeNodeVo> collectContextNodes(AiDocumentTreeNodeVo root, AiDocumentTreeNodeVo currentNode,
                                                           AiDocumentNodeAskDto askDto) {
        List<AiDocumentTreeNodeVo> result = new ArrayList<>();
        LinkedHashMap<String, AiDocumentTreeNodeVo> ordered = new LinkedHashMap<>();
        List<AiDocumentTreeNodeVo> ancestors = collectAncestors(root, currentNode.getId());
        for (AiDocumentTreeNodeVo ancestor : ancestors) {
            ordered.putIfAbsent(ancestor.getId(), ancestor);
        }
        ordered.putIfAbsent(currentNode.getId(), currentNode);
        appendSubtree(currentNode, ordered, 0, 2);

        if (askDto != null && askDto.getSelectedNodeIds() != null) {
            for (String selectedNodeId : askDto.getSelectedNodeIds()) {
                if (!StringUtils.hasText(selectedNodeId)) {
                    continue;
                }
                AiDocumentTreeNodeVo selectedNode = findNode(root, selectedNodeId);
                if (selectedNode != null) {
                    ordered.putIfAbsent(selectedNode.getId(), selectedNode);
                }
            }
        }

        int maxChars = Math.max(4000, aiProperties.getMaxArticleContextChars());
        int totalChars = 0;
        for (AiDocumentTreeNodeVo node : ordered.values()) {
            int nodeChars = measureNodeContent(node);
            if (!result.isEmpty() && totalChars + nodeChars > maxChars) {
                break;
            }
            result.add(node);
            totalChars += nodeChars;
        }
        return result;
    }

    private void appendSubtree(AiDocumentTreeNodeVo node, LinkedHashMap<String, AiDocumentTreeNodeVo> ordered,
                               int depth, int maxDepth) {
        if (node == null || depth > maxDepth || node.getChildren() == null) {
            return;
        }
        for (AiDocumentTreeNodeVo child : node.getChildren()) {
            ordered.putIfAbsent(child.getId(), child);
            appendSubtree(child, ordered, depth + 1, maxDepth);
        }
    }

    private List<AiDocumentTreeNodeVo> collectAncestors(AiDocumentTreeNodeVo root, String nodeId) {
        List<AiDocumentTreeNodeVo> trail = new ArrayList<>();
        if (collectNodeTrail(root, nodeId, trail)) {
            if (!trail.isEmpty()) {
                trail.removeLast();
            }
            return trail;
        }
        return List.of();
    }

    private boolean collectNodeTrail(AiDocumentTreeNodeVo current, String nodeId, List<AiDocumentTreeNodeVo> trail) {
        if (current == null) {
            return false;
        }
        trail.add(current);
        if (Objects.equals(current.getId(), nodeId)) {
            return true;
        }
        for (AiDocumentTreeNodeVo child : current.getChildren()) {
            if (collectNodeTrail(child, nodeId, trail)) {
                return true;
            }
        }
        trail.removeLast();
        return false;
    }

    private AiDocumentTreeNodeVo findNode(AiDocumentTreeNodeVo root, String nodeId) {
        if (root == null || !StringUtils.hasText(nodeId)) {
            return null;
        }
        if (Objects.equals(root.getId(), nodeId)) {
            return root;
        }
        for (AiDocumentTreeNodeVo child : root.getChildren()) {
            AiDocumentTreeNodeVo matched = findNode(child, nodeId);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private int measureNodeContent(AiDocumentTreeNodeVo node) {
        String content = safeText(node == null ? null : node.getMarkdown(), safeText(node == null ? null : node.getSummary(), ""));
        return content.length() + safeText(node == null ? null : node.getTitle(), "").length() + 32;
    }

    private String buildContextPayload(AiDocumentTaskDetailVo detail, AiDocumentTreeNodeVo currentNode, List<AiDocumentTreeNodeVo> contextNodes) {
        StringBuilder builder = new StringBuilder();
        builder.append("文档任务 ID: ").append(detail == null ? "-" : detail.getTaskId()).append('\n');
        builder.append("当前聚焦节点 ID: ").append(currentNode == null ? "-" : safeText(currentNode.getId(), "-")).append("\n\n");
        for (AiDocumentTreeNodeVo node : contextNodes) {
            builder.append("### 节点 ").append(safeText(node.getId(), "-")).append('\n');
            builder.append("- 标题: ").append(safeText(node.getTitle(), "未命名节点")).append('\n');
            builder.append("- 类型: ").append(safeText(node.getType(), "section")).append('\n');
            builder.append("- 层级: ").append(node.getLevel() == null ? "-" : node.getLevel()).append('\n');
            builder.append("- 摘要: ").append(safeText(node.getSummary(), "无")).append('\n');
            if (node.getSourceAnchors() != null && !node.getSourceAnchors().isEmpty()) {
                AiDocumentSourceAnchorVo anchor = node.getSourceAnchors().getFirst();
                builder.append("- 原文定位: 页码 ").append(anchor.getPage() == null ? "-" : anchor.getPage());
                if (StringUtils.hasText(anchor.getTextSnippet())) {
                    builder.append("，片段 ").append(anchor.getTextSnippet().trim());
                }
                builder.append('\n');
            }
            builder.append("- Markdown:\n");
            builder.append(trimContextBlock(safeText(node.getMarkdown(), safeText(node.getSummary(), "无")))).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String trimContextBlock(String content) {
        String normalized = safeText(content, "");
        if (normalized.length() <= 1200) {
            return normalized;
        }
        return normalized.substring(0, 1200) + "\n[节点内容已截断]";
    }

    private String normalizeQuestion(AiDocumentNodeAskDto askDto) {
        String question = askDto == null ? null : askDto.getQuestion();
        if (!StringUtils.hasText(question)) {
            throw new IllegalStateException("问题不能为空");
        }
        String normalized = question.trim();
        if (normalized.length() > 1000) {
            throw new IllegalStateException("问题不能超过 1000 个字符");
        }
        return normalized;
    }

    private String extractAnswer(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        } else {
            chatResponse.getResult();
        }
        return safeText(chatResponse.getResult().getOutput().getText(), null);
    }

    private long resolveConsumedTokens(String question, String answer, Usage usage) {
        if (usage != null) {
            usage.getTotalTokens();
            if (usage.getTotalTokens() > 0) {
                return usage.getTotalTokens();
            }
        }
        int estimatedChars = safeText(question, "").length() + safeText(answer, "").length();
        return Math.max(estimatedChars / 2L, 400L);
    }

    private List<AiDocumentNodeCitationVo> buildCitations(List<AiDocumentTreeNodeVo> contextNodes, AiDocumentTreeNodeVo currentNode) {
        List<AiDocumentNodeCitationVo> citations = new ArrayList<>();
        for (AiDocumentTreeNodeVo node : contextNodes.stream().limit(6).toList()) {
            AiDocumentNodeCitationVo citation = new AiDocumentNodeCitationVo();
            citation.setNodeId(node.getId());
            citation.setTitle(node.getTitle());
            citation.setLevel(node.getLevel());
            citation.setType(node.getType());
            citation.setRelation(Objects.equals(node.getId(), currentNode.getId())
                    ? "current"
                    : inferCitationRelation(node));
            citations.add(citation);
        }
        return citations;
    }

    private String inferCitationRelation(AiDocumentTreeNodeVo node) {
        String normalizedType = safeText(node == null ? null : node.getType(), "section").toLowerCase(Locale.ROOT);
        return switch (normalizedType) {
            case "document" -> "document";
            case "content", "table", "image", "list", "code" -> "detail";
            default -> "outline";
        };
    }

    private String buildRootMarkdown(String title, String markdown) {
        if (StringUtils.hasText(markdown)) {
            return markdown.length() > 800 ? markdown.substring(0, 800) + "\n\n[Markdown 已截断]" : markdown;
        }
        return "# " + safeText(title, "未命名文档");
    }

    private LocalDateTime resolveExpireAt(LocalDateTime baseTime) {
        int retentionDays = Math.max(1, aiProperties.getDocument().getRetentionDays());
        return (baseTime == null ? LocalDateTime.now() : baseTime).plusDays(retentionDays);
    }

    private String resolveMarkdownResultPath(Long taskId) {
        return taskId == null ? null : "/api/ai/document/tasks/%d/result".formatted(taskId);
    }

    private int normalizeHeadingLevel(int rawLevel) {
        if (rawLevel <= 0) {
            return 0;
        }
        return Math.min(rawLevel, 6);
    }

    private String summarizeText(String text) {
        String normalized = safeText(text, "");
        if (normalized.length() <= 72) {
            return normalized;
        }
        return normalized.substring(0, 72) + "...";
    }

    private String summarizeTitleByType(String type, String text) {
        String normalized = summarizeText(text);
        return switch (safeText(type, "content")) {
            case "table" -> "表格节点";
            case "image" -> "图片节点";
            case "list" -> "列表节点";
            case "code" -> "代码节点";
            default -> normalized;
        };
    }

    private String toMarkdownByType(String type, String text) {
        String normalizedType = safeText(type, "content");
        if ("code".equals(normalizedType)) {
            return "```\n" + text.trim() + "\n```";
        }
        return text.trim();
    }

    private String extractContentText(JsonNode item) {
        String directText = firstText(item,
                path("text"),
                path("content"),
                path("raw_text"),
                path("caption"));
        if (StringUtils.hasText(directText)) {
            return directText;
        }
        JsonNode lines = item.get("lines");
        if (lines != null && lines.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode line : lines) {
                String content = textAt(line, "text");
                if (StringUtils.hasText(content)) {
                    parts.add(content.trim());
                }
            }
            if (!parts.isEmpty()) {
                return String.join("\n", parts);
            }
        }
        return null;
    }

    private List<Double> extractBbox(JsonNode item) {
        JsonNode bboxNode = item.get("bbox");
        if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() < 4) {
            return null;
        }
        List<Double> bbox = new ArrayList<>();
        for (JsonNode value : bboxNode) {
            bbox.add(value.asDouble());
        }
        return bbox;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void validateMineruSourceUrl(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl.trim());
            String scheme = safeText(uri.getScheme(), "").toLowerCase(Locale.ROOT);
            String host = safeText(uri.getHost(), "").toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalStateException("MinerU 仅支持公网 http/https 文件 URL");
            }
            if (!StringUtils.hasText(host) || "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
                throw new IllegalStateException("MinerU 仅支持公网可访问的文件 URL，当前文件地址不可被官方服务访问");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("MinerU 仅支持有效的公网文件 URL");
        }
    }

    private void verifyCallbackSignature(AiProperties.Mineru mineru, String checksum, String content) {
        String uid = safeText(mineru.getCallbackUid(), null);
        String seed = safeText(mineru.getCallbackSeed(), null);
        if (!StringUtils.hasText(uid) || !StringUtils.hasText(seed)) {
            log.warn("MinerU callback 验签配置不完整，已跳过 checksum 校验");
            return;
        }
        if (!StringUtils.hasText(checksum)) {
            throw new IllegalStateException("callback checksum 不能为空");
        }
        String expected = sha256Hex(uid + seed + content);
        if (!expected.equalsIgnoreCase(checksum.trim())) {
            throw new IllegalStateException("callback checksum 校验失败");
        }
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("计算 callback checksum 失败", ex);
        }
    }

    private boolean hasMaterializedParseResult(DocumentTaskAggregate aggregate) {
        if (aggregate == null) {
            return false;
        }
        if (StringUtils.hasText(aggregate.resultEntity().getContentListJson())) {
            return true;
        }
        if (isParsedMarkdownAvailable(aggregate.resultEntity().getMarkdown())
                || isParsedMarkdownAvailable(aggregate.result().getMarkdown())) {
            return true;
        }
        return hasNonPlaceholderTree(aggregate.result().getRoot());
    }

    private boolean hasNonPlaceholderTree(AiDocumentTreeNodeVo root) {
        if (root == null || root.getChildren() == null || root.getChildren().isEmpty()) {
            return false;
        }
        return root.getChildren().stream()
                .filter(Objects::nonNull)
                .anyMatch(child -> !Objects.equals("待解析结构", safeText(child.getTitle(), "")));
    }

    private boolean isParsedMarkdownAvailable(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return false;
        }
        String normalized = markdown.trim();
        return !normalized.contains("当前任务已创建，等待接入真实解析结果")
                && !normalized.contains("文档已提交至 MinerU，当前等待异步解析完成");
    }

    private String[] path(String... segments) {
        return segments;
    }

    private String firstText(JsonNode root, String[]... paths) {
        for (String[] path : paths) {
            String value = textAt(root, path);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer firstInt(JsonNode root, String[]... paths) {
        for (String[] path : paths) {
            JsonNode current = nodeAt(root, path);
            if (current != null && current.isNumber()) {
                return current.asInt();
            }
        }
        return null;
    }

    private JsonNode firstNode(JsonNode root, String[]... paths) {
        for (String[] path : paths) {
            JsonNode current = nodeAt(root, path);
            if (current != null && !current.isNull()) {
                return current;
            }
        }
        return null;
    }

    private String normalizeRemoteStatus(String rawStatus) {
        if (!StringUtils.hasText(rawStatus)) {
            return null;
        }
        String status = rawStatus.trim().toUpperCase();
        return switch (status) {
            case "SUCCESS", "SUCCEEDED", "DONE", "COMPLETED", "FINISHED", "PARSED" -> "PARSED";
            case "FAILED", "ERROR", "CANCELED", "CANCELLED" -> "FAILED";
            case "PENDING", "QUEUED", "CREATED", "SUBMITTED" -> "SUBMITTED";
            default -> "PROCESSING";
        };
    }

    private String mergeRawPayloadWithZipUrl(String existingPayload, String latestResponse, String fullZipUrl) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("latestResponse", JsonUtil.readTree(latestResponse));
        wrapper.put("previousPayload", JsonUtil.readTree(existingPayload));
        wrapper.put("fullZipUrl", fullZipUrl);
        return JsonUtil.toJsonString(wrapper);
    }

    private JsonNode nodeAt(JsonNode root, String... path) {
        JsonNode current = root;
        for (String segment : path) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private String textAt(JsonNode root, String... path) {
        JsonNode current = nodeAt(root, path);
        return current == null || current.isNull() ? null : current.asText();
    }

    private DocumentTaskAggregate toAggregate(SysAiDocumentTask task, SysAiDocumentResult result) {
        AiDocumentTaskDetailVo detail = new AiDocumentTaskDetailVo();
        detail.setTaskId(task.getId());
        detail.setTitle(task.getTitle());
        detail.setSourceFileId(task.getSourceFileId());
        detail.setStatus(task.getStatus());
        detail.setRemoteTaskId(task.getRemoteTaskId());
        detail.setFileName(task.getFileName());
        detail.setSourceUrl(task.getSourceUrl());
        detail.setMarkdownUrl(task.getMarkdownUrl());
        detail.setPageCount(task.getPageCount());
        detail.setRootNodeId(task.getRootNodeId());
        detail.setExpireAt(task.getExpireAt());
        detail.setCreateTime(task.getCreateTime());
        detail.setUpdateTime(task.getUpdateTime());

        AiDocumentParseResultVo resultVo = new AiDocumentParseResultVo();
        resultVo.setTaskId(task.getId());
        resultVo.setTitle(task.getTitle());
        if (result != null) {
            resultVo.setMarkdown(result.getMarkdown());
            AiDocumentTreeNodeVo root = JsonUtil.readValue(result.getRootJson(), AiDocumentTreeNodeVo.class);
            if (root == null && StringUtils.hasText(result.getContentListJson())) {
                JsonNode contentListNode = JsonUtil.readTree(result.getContentListJson());
                if (contentListNode != null && contentListNode.isArray() && !contentListNode.isEmpty()) {
                    root = buildTreeFromContentList(task.getId(), task.getTitle(), contentListNode, result.getMarkdown());
                }
            }
            if (root == null && isParsedMarkdownAvailable(result.getMarkdown())) {
                root = buildTreeFromMarkdown(task.getId(), task.getTitle(), result.getMarkdown());
            }
            resultVo.setRoot(root);
        }
        return new DocumentTaskAggregate(detail, resultVo, task, result == null ? emptyResultEntity(task.getId()) : result);
    }

    private SysAiDocumentTask toTaskEntity(AiDocumentTaskDetailVo detail, SysAiDocumentTask original) {
        return SysAiDocumentTask.builder()
                .id(detail.getTaskId())
                .userId(original == null || original.getUserId() == null ? StpUtil.getLoginIdAsLong() : original.getUserId())
                .sourceFileId(detail.getSourceFileId())
                .title(detail.getTitle())
                .status(detail.getStatus())
                .provider(original == null || !StringUtils.hasText(original.getProvider()) ? "mineru" : original.getProvider())
                .remoteTaskId(detail.getRemoteTaskId())
                .fileName(detail.getFileName())
                .sourceUrl(detail.getSourceUrl())
                .markdownUrl(detail.getMarkdownUrl())
                .pageCount(detail.getPageCount())
                .rootNodeId(detail.getRootNodeId())
                .expireAt(detail.getExpireAt())
                .lastPolledAt(original == null ? null : original.getLastPolledAt())
                .createTime(detail.getCreateTime())
                .updateTime(detail.getUpdateTime())
                .build();
    }

    private SysAiDocumentResult toResultEntity(AiDocumentParseResultVo resultVo, SysAiDocumentResult original) {
        SysAiDocumentResult resultEntity = original == null ? emptyResultEntity(resultVo.getTaskId()) : original;
        resultEntity.setTaskId(resultVo.getTaskId());
        resultEntity.setMarkdown(resultVo.getMarkdown());
        resultEntity.setRootJson(resultVo.getRoot() == null ? null : JsonUtil.toJsonString(resultVo.getRoot()));
        if (resultEntity.getCreateTime() == null) {
            resultEntity.setCreateTime(LocalDateTime.now());
        }
        resultEntity.setUpdateTime(LocalDateTime.now());
        return resultEntity;
    }

    private SysAiDocumentResult emptyResultEntity(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        return SysAiDocumentResult.builder()
                .taskId(taskId)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private record DocumentTaskAggregate(AiDocumentTaskDetailVo detail,
                                         AiDocumentParseResultVo result,
                                         SysAiDocumentTask taskEntity,
                                         SysAiDocumentResult resultEntity) {
    }

    private record ZipDocumentPayload(String markdown, String contentListJson) {
    }

    private record MockFixturePayload(String markdown,
                                      String contentListJson,
                                      String rawPayloadJson,
                                      int pageCount) {
    }
}

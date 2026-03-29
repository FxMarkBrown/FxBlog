package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
import top.fxmarkbrown.blog.service.AiDocumentVectorIndexService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.service.AiDocumentTaskService;
import top.fxmarkbrown.blog.model.ai.AiDocumentChunkHit;
import top.fxmarkbrown.blog.utils.HttpUtil;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeAnswerVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentContextBudgetVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentContextNodeVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentContextPlanVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentKnowledgeFlowEdgeVo;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final ObjectProvider<AiDocumentVectorIndexService> documentVectorIndexServiceProvider;
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
        syncTaskVectorIndexIfParsed(aggregate);
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
        syncTaskVectorIndexIfParsed(aggregate);
        return copyDetail(aggregate.detail());
    }

    @Override
    public void deleteTask(Long taskId) {
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        deletePersistedTask(aggregate.taskEntity());
    }

    @Override
    public int cleanupExpiredTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<SysAiDocumentTask> expiredTasks = documentTaskMapper.selectList(new LambdaQueryWrapper<SysAiDocumentTask>()
                .isNotNull(SysAiDocumentTask::getExpireAt)
                .lt(SysAiDocumentTask::getExpireAt, now)
                .orderByAsc(SysAiDocumentTask::getExpireAt)
                .orderByAsc(SysAiDocumentTask::getId));
        int deleted = 0;
        for (SysAiDocumentTask expiredTask : expiredTasks) {
            try {
                deletePersistedTask(expiredTask);
                deleted++;
            } catch (Exception ex) {
                log.warn("清理过期文档任务失败, taskId={}", expiredTask.getId(), ex);
            }
        }
        return deleted;
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
        syncTaskVectorIndexIfParsed(aggregate);
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

        ContextCompilation contextCompilation = compileContextFlow(aggregate.detail(), root, currentNode, question, askDto);
        String contextPayload = buildContextPayload(aggregate.detail(), currentNode, contextCompilation);

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
            vo.setContextNodeIds(contextCompilation.usedCandidates().stream().map(ContextCandidate::nodeId).toList());
            vo.setCitations(buildCitations(contextCompilation.usedCandidates(), currentNode));
            vo.setContextPlan(contextCompilation.contextPlan());
            vo.setBudgetReport(contextCompilation.budgetReport());
            vo.setUsedNodes(contextCompilation.usedCandidates().stream().map(this::toContextNodeVo).toList());
            vo.setCandidateNodes(contextCompilation.candidates().stream().map(this::toContextNodeVo).toList());
            vo.setKnowledgeFlowEdges(contextCompilation.knowledgeFlowEdges());
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

    private void deletePersistedTask(SysAiDocumentTask taskEntity) {
        if (taskEntity == null || taskEntity.getId() == null) {
            throw new IllegalStateException("待删除文档任务不存在");
        }
        deleteTaskVectorCollection(taskEntity.getId());
        documentResultMapper.deleteById(taskEntity.getId());
        documentTaskMapper.deleteById(taskEntity.getId());
    }

    private void deleteTaskVectorCollection(Long taskId) {
        Optional.ofNullable(documentVectorIndexServiceProvider.getIfAvailable())
                .filter(AiDocumentVectorIndexService::isReady)
                .ifPresent(service -> service.deleteTaskIndex(taskId));
    }

    private void syncTaskVectorIndexIfParsed(DocumentTaskAggregate aggregate) {
        if (aggregate == null || aggregate.detail() == null || aggregate.result() == null) {
            return;
        }
        if (!Objects.equals(aggregate.detail().getStatus(), "PARSED") || aggregate.result().getRoot() == null) {
            return;
        }
        Optional.ofNullable(documentVectorIndexServiceProvider.getIfAvailable())
                .filter(AiDocumentVectorIndexService::isReady)
                .ifPresent(service -> {
                    try {
                        service.syncTaskIndex(aggregate.detail(), aggregate.result());
                    } catch (Exception ex) {
                        log.warn("文档任务向量索引同步失败, taskId={}", aggregate.detail().getTaskId(), ex);
                    }
                });
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
                "根据 MinerU 真实 content_list 生成的结构树。", buildRootMarkdown(title, markdown), true);
        List<AiDocumentTreeNodeVo> headingStack = new ArrayList<>();
        StringBuilder contentTextBuffer = new StringBuilder();
        StringBuilder contentMarkdownBuffer = new StringBuilder();
        List<AiDocumentSourceAnchorVo> contentAnchors = new ArrayList<>();
        int contentCounter = 0;
        int headingCounter = 0;

        for (MineruContentBlock block : flattenMineruContentBlocks(contentListNode)) {
            String text = block.text();
            int level = block.level();
            Integer page = block.page();
            List<Double> bbox = block.bbox();

            if (StringUtils.hasText(text) && level > 0) {
                contentCounter = flushContentListBlock(taskId, headingStack, root, contentTextBuffer, contentMarkdownBuffer, contentAnchors, contentCounter);
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

            String contentType = safeText(block.type(), "content");
            appendBufferedBlock(contentTextBuffer, contentMarkdownBuffer, contentType, text);
            if (page != null && page > 0 && bbox != null && bbox.size() >= 4) {
                contentAnchors.add(anchor(page, bbox, summarizeText(text)));
            }
        }

        flushContentListBlock(taskId, headingStack, root, contentTextBuffer, contentMarkdownBuffer, contentAnchors, contentCounter);
        finalizeExpandability(root);
        return root;
    }

    private int flushContentListBlock(Long taskId, List<AiDocumentTreeNodeVo> headingStack, AiDocumentTreeNodeVo root,
                                      StringBuilder contentTextBuffer, StringBuilder contentMarkdownBuffer,
                                      List<AiDocumentSourceAnchorVo> contentAnchors, int contentCounter) {
        String contentText = contentTextBuffer.toString().trim();
        String contentMarkdown = contentMarkdownBuffer.toString().trim();
        contentTextBuffer.setLength(0);
        contentMarkdownBuffer.setLength(0);

        if (!StringUtils.hasText(contentText) && !StringUtils.hasText(contentMarkdown)) {
            contentAnchors.clear();
            return contentCounter;
        }

        AiDocumentTreeNodeVo parent = headingStack.isEmpty() ? root : headingStack.getLast();
        String markdown = StringUtils.hasText(contentMarkdown) ? contentMarkdown : contentText;
        String summaryText = StringUtils.hasText(contentText) ? contentText : markdown;
        AiDocumentTreeNodeVo contentNode = createNode(
                "doc-" + taskId + "-content-" + (++contentCounter),
                parent.getId(),
                "content",
                summarizeTitleByType("content", summaryText),
                Math.max(1, headingStack.size() + 1),
                summarizeText(summaryText),
                markdown,
                false
        );
        if (!contentAnchors.isEmpty()) {
            contentNode.getSourceAnchors().addAll(contentAnchors);
        }
        contentAnchors.clear();
        parent.getChildren().add(contentNode);
        return contentCounter;
    }

    private List<MineruContentBlock> flattenMineruContentBlocks(JsonNode contentListNode) {
        if (contentListNode == null || !contentListNode.isArray()) {
            return List.of();
        }

        List<MineruContentBlock> blocks = new ArrayList<>();
        int pageIndex = 0;
        for (JsonNode pageNode : contentListNode) {
            pageIndex += 1;
            if (pageNode == null || pageNode.isNull()) {
                continue;
            }
            if (pageNode.isArray()) {
                for (JsonNode item : pageNode) {
                    MineruContentBlock block = toMineruContentBlock(item, pageIndex);
                    if (block != null) {
                        blocks.add(block);
                    }
                }
                continue;
            }

            MineruContentBlock block = toMineruContentBlock(pageNode, null);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private MineruContentBlock toMineruContentBlock(JsonNode item, Integer fallbackPage) {
        if (item == null || item.isNull() || !item.isObject()) {
            return null;
        }

        String type = safeText(textAt(item, "type"), "content").toLowerCase(Locale.ROOT);
        Integer page = item.hasNonNull("page_idx") ? item.get("page_idx").asInt() + 1 : fallbackPage;
        List<Double> bbox = extractBbox(item);
        int level = "title".equals(type) ? normalizeHeadingLevel(item.path("content").path("level").asInt(0)) : 0;
        String text = extractMineruBlockText(item, type);

        if (!StringUtils.hasText(text) && !"image".equals(type) && !"table".equals(type)) {
            return null;
        }

        return new MineruContentBlock(type, level, safeText(text, null), page, bbox);
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

    private ContextCompilation compileContextFlow(AiDocumentTaskDetailVo detail,
                                                  AiDocumentTreeNodeVo root,
                                                  AiDocumentTreeNodeVo currentNode,
                                                  String question,
                                                  AiDocumentNodeAskDto askDto) {
        String queryMode = resolveQueryMode(askDto, question);
        int descendantDepth = resolveDescendantDepth(askDto, queryMode);
        boolean includeAncestorSiblings = resolveIncludeAncestorSiblings(askDto, queryMode);
        boolean includeSemanticBridges = resolveIncludeSemanticBridges(askDto, queryMode);
        int maxBridgeNodes = resolveMaxBridgeNodes(askDto, queryMode);

        List<AiDocumentTreeNodeVo> trail = new ArrayList<>();
        collectNodeTrail(root, currentNode.getId(), trail);
        List<AiDocumentTreeNodeVo> ancestors = new ArrayList<>(trail);
        if (!ancestors.isEmpty()) {
            ancestors.removeLast();
        }

        List<AiDocumentTreeNodeVo> descendants = collectDescendants(currentNode, 1, descendantDepth);
        List<AiDocumentTreeNodeVo> selectedNodes = resolveSelectedNodes(root, currentNode, askDto);
        List<AiDocumentTreeNodeVo> ancestorSiblingNodes = includeAncestorSiblings
                ? collectAncestorSiblingNodes(trail)
                : List.of();

        LinkedHashMap<String, ContextCandidate> candidateMap = new LinkedHashMap<>();
        addCandidate(candidateMap, currentNode, "current", 1.0D, "当前聚焦节点");

        for (int i = 0; i < ancestors.size(); i++) {
            AiDocumentTreeNodeVo ancestor = ancestors.get(i);
            double weight = Math.max(0.68D, 0.9D - (ancestors.size() - i - 1) * 0.06D);
            addCandidate(candidateMap, ancestor, "ancestor", weight, "当前节点的祖先路径");
        }

        for (AiDocumentTreeNodeVo descendant : descendants) {
            int relativeDepth = Math.max(1, Math.max(0, safeLevel(descendant) - safeLevel(currentNode)));
            double weight = Math.max(0.6D, 0.86D - relativeDepth * 0.08D);
            addCandidate(candidateMap, descendant, "descendant", weight, "当前节点子树中的下级内容");
        }

        for (AiDocumentTreeNodeVo selectedNode : selectedNodes) {
            addCandidate(candidateMap, selectedNode, "selected", 0.88D, "用户显式选中的补充节点");
        }

        for (AiDocumentTreeNodeVo siblingNode : ancestorSiblingNodes) {
            addCandidate(candidateMap, siblingNode, "ancestor_sibling", 0.74D, "祖先节点下的关键兄弟节点");
        }

        if (includeSemanticBridges) {
            Set<String> excludedNodeIds = new LinkedHashSet<>(candidateMap.keySet());
            for (ContextCandidate bridgeCandidate : collectSemanticBridgeCandidates(detail, root, question, currentNode, excludedNodeIds, maxBridgeNodes)) {
                addCandidate(candidateMap, bridgeCandidate.node(), bridgeCandidate.relation(), bridgeCandidate.weight(), bridgeCandidate.reason());
            }
        }

        List<ContextCandidate> candidates = new ArrayList<>(candidateMap.values());
        int maxChars = Math.max(4000, aiProperties.getMaxArticleContextChars());
        int candidateChars = 0;
        int usedChars = 0;
        int truncatedNodeCount = 0;
        List<ContextCandidate> usedCandidates = new ArrayList<>();
        for (ContextCandidate candidate : candidates) {
            int nodeChars = measureNodeContent(candidate.node());
            candidateChars += nodeChars;
            if (usedCandidates.isEmpty() || usedChars + nodeChars <= maxChars) {
                usedCandidates.add(candidate);
                usedChars += nodeChars;
            } else {
                truncatedNodeCount += 1;
            }
        }

        AiDocumentContextPlanVo contextPlan = new AiDocumentContextPlanVo();
        contextPlan.setQueryMode(queryMode);
        contextPlan.setCurrentNodeId(currentNode.getId());
        contextPlan.setDescendantDepth(descendantDepth);
        contextPlan.setMaxBridgeNodes(maxBridgeNodes);
        contextPlan.setAncestorCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "ancestor")).count());
        contextPlan.setDescendantCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "descendant")).count());
        contextPlan.setAncestorSiblingCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "ancestor_sibling")).count());
        contextPlan.setSelectedCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "selected")).count());
        contextPlan.setSemanticBridgeCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "semantic_bridge")).count());
        contextPlan.setTotalCandidateCount(candidates.size());
        contextPlan.setTotalUsedCount(usedCandidates.size());

        AiDocumentContextBudgetVo budgetReport = new AiDocumentContextBudgetVo();
        budgetReport.setMaxChars(maxChars);
        budgetReport.setCandidateChars(candidateChars);
        budgetReport.setUsedChars(usedChars);
        budgetReport.setRemainingChars(Math.max(0, maxChars - usedChars));
        budgetReport.setTruncatedNodeCount(truncatedNodeCount);

        List<AiDocumentKnowledgeFlowEdgeVo> knowledgeFlowEdges = buildKnowledgeFlowEdges(currentNode, trail, usedCandidates);
        return new ContextCompilation(detail, queryMode, currentNode, candidates, usedCandidates, contextPlan, budgetReport, knowledgeFlowEdges);
    }

    private void addCandidate(LinkedHashMap<String, ContextCandidate> candidateMap,
                              AiDocumentTreeNodeVo node,
                              String relation,
                              double weight,
                              String reason) {
        if (node == null || !StringUtils.hasText(node.getId())) {
            return;
        }
        ContextCandidate nextCandidate = new ContextCandidate(node, node.getId(), relation, weight, reason);
        ContextCandidate existing = candidateMap.get(node.getId());
        if (existing == null) {
            candidateMap.put(node.getId(), nextCandidate);
            return;
        }
        if (relationPriority(nextCandidate.relation()) > relationPriority(existing.relation())
                || (relationPriority(nextCandidate.relation()) == relationPriority(existing.relation())
                && nextCandidate.weight() > existing.weight())) {
            candidateMap.put(node.getId(), nextCandidate);
        }
    }

    private int relationPriority(String relation) {
        return switch (safeText(relation, "")) {
            case "current" -> 100;
            case "selected" -> 90;
            case "ancestor" -> 80;
            case "descendant" -> 70;
            case "ancestor_sibling" -> 60;
            case "semantic_bridge" -> 50;
            default -> 0;
        };
    }

    private String resolveQueryMode(AiDocumentNodeAskDto askDto, String question) {
        String specifiedMode = safeText(askDto == null ? null : askDto.getQueryMode(), null);
        if (StringUtils.hasText(specifiedMode)) {
            String normalizedMode = specifiedMode.toLowerCase(Locale.ROOT);
            if (List.of("explain", "compare", "locate", "reason", "summarize").contains(normalizedMode)) {
                return normalizedMode;
            }
        }
        return inferQueryMode(question);
    }

    private String inferQueryMode(String question) {
        String normalized = safeText(question, "").toLowerCase(Locale.ROOT);
        if (normalized.contains("比较") || normalized.contains("区别") || normalized.contains("不同") || normalized.contains("优缺点")) {
            return "compare";
        }
        if (normalized.contains("原文") || normalized.contains("页") || normalized.contains("位置") || normalized.contains("哪里")) {
            return "locate";
        }
        if (normalized.contains("总结") || normalized.contains("概括") || normalized.contains("梳理")) {
            return "summarize";
        }
        if (normalized.contains("为什么") || normalized.contains("原因") || normalized.contains("推导") || normalized.contains("证明")) {
            return "reason";
        }
        return "explain";
    }

    private int resolveDescendantDepth(AiDocumentNodeAskDto askDto, String queryMode) {
        Integer specifiedDepth = askDto == null ? null : askDto.getDescendantDepth();
        if (specifiedDepth != null) {
            return Math.max(0, Math.min(specifiedDepth, 4));
        }
        return switch (queryMode) {
            case "summarize" -> 3;
            case "compare", "locate" -> 1;
            default -> 2;
        };
    }

    private boolean resolveIncludeAncestorSiblings(AiDocumentNodeAskDto askDto, String queryMode) {
        if (askDto != null && askDto.getIncludeAncestorSiblings() != null) {
            return askDto.getIncludeAncestorSiblings();
        }
        return "compare".equals(queryMode) || "reason".equals(queryMode);
    }

    private boolean resolveIncludeSemanticBridges(AiDocumentNodeAskDto askDto, String queryMode) {
        if (askDto != null && askDto.getIncludeSemanticBridges() != null) {
            return askDto.getIncludeSemanticBridges();
        }
        return !"locate".equals(queryMode);
    }

    private int resolveMaxBridgeNodes(AiDocumentNodeAskDto askDto, String queryMode) {
        Integer specifiedCount = askDto == null ? null : askDto.getMaxBridgeNodes();
        if (specifiedCount != null) {
            return Math.max(0, Math.min(specifiedCount, 6));
        }
        return switch (queryMode) {
            case "compare" -> 3;
            case "locate", "summarize" -> 1;
            default -> 2;
        };
    }

    private List<AiDocumentTreeNodeVo> resolveSelectedNodes(AiDocumentTreeNodeVo root,
                                                            AiDocumentTreeNodeVo currentNode,
                                                            AiDocumentNodeAskDto askDto) {
        if (askDto == null || askDto.getSelectedNodeIds() == null || askDto.getSelectedNodeIds().isEmpty()) {
            return List.of();
        }
        List<AiDocumentTreeNodeVo> selectedNodes = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        visited.add(currentNode.getId());
        for (String selectedNodeId : askDto.getSelectedNodeIds()) {
            if (!StringUtils.hasText(selectedNodeId) || !visited.add(selectedNodeId.trim())) {
                continue;
            }
            AiDocumentTreeNodeVo selectedNode = findNode(root, selectedNodeId.trim());
            if (selectedNode != null) {
                selectedNodes.add(selectedNode);
            }
        }
        return selectedNodes;
    }

    private List<AiDocumentTreeNodeVo> collectDescendants(AiDocumentTreeNodeVo node, int depth, int maxDepth) {
        if (node == null || maxDepth <= 0 || node.getChildren() == null || node.getChildren().isEmpty()) {
            return List.of();
        }
        List<AiDocumentTreeNodeVo> descendants = new ArrayList<>();
        for (AiDocumentTreeNodeVo child : node.getChildren()) {
            descendants.add(child);
            if (depth < maxDepth) {
                descendants.addAll(collectDescendants(child, depth + 1, maxDepth));
            }
        }
        return descendants;
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

    private String buildContextPayload(AiDocumentTaskDetailVo detail,
                                       AiDocumentTreeNodeVo currentNode,
                                       ContextCompilation contextCompilation) {
        StringBuilder builder = new StringBuilder();
        builder.append("文档任务 ID: ").append(detail == null ? "-" : detail.getTaskId()).append('\n');
        builder.append("当前聚焦节点 ID: ").append(currentNode == null ? "-" : safeText(currentNode.getId(), "-")).append("\n\n");
        builder.append("问答模式: ").append(contextCompilation.queryMode()).append('\n');
        builder.append("候选节点数: ").append(contextCompilation.contextPlan().getTotalCandidateCount()).append('\n');
        builder.append("实际入选节点数: ").append(contextCompilation.contextPlan().getTotalUsedCount()).append("\n\n");
        appendContextLane(builder, "Lane A - 当前节点", contextCompilation.usedCandidates(), "current");
        appendContextLane(builder, "Lane B - 当前节点子树", contextCompilation.usedCandidates(), "descendant");
        appendContextLane(builder, "Lane C - 祖先路径", contextCompilation.usedCandidates(), "ancestor");
        appendContextLane(builder, "Lane D - 显式选择节点", contextCompilation.usedCandidates(), "selected");
        appendContextLane(builder, "Lane E - 祖先兄弟与语义桥接", contextCompilation.usedCandidates(), "ancestor_sibling", "semantic_bridge");
        return builder.toString().trim();
    }

    private void appendContextLane(StringBuilder builder,
                                   String laneTitle,
                                   List<ContextCandidate> candidates,
                                   String... relations) {
        List<String> relationList = List.of(relations);
        List<ContextCandidate> laneCandidates = candidates.stream()
                .filter(candidate -> relationList.contains(candidate.relation()))
                .toList();
        if (laneCandidates.isEmpty()) {
            return;
        }
        builder.append("## ").append(laneTitle).append('\n');
        for (ContextCandidate candidate : laneCandidates) {
            appendContextNodeBlock(builder, candidate);
        }
        builder.append('\n');
    }

    private void appendContextNodeBlock(StringBuilder builder, ContextCandidate candidate) {
        AiDocumentTreeNodeVo node = candidate.node();
        builder.append("### 节点 ").append(safeText(node.getId(), "-")).append('\n');
        builder.append("- 标题: ").append(safeText(node.getTitle(), "未命名节点")).append('\n');
        builder.append("- 类型: ").append(safeText(node.getType(), "section")).append('\n');
        builder.append("- 层级: ").append(node.getLevel() == null ? "-" : node.getLevel()).append('\n');
        builder.append("- 关系: ").append(candidate.relation()).append('\n');
        builder.append("- 纳入原因: ").append(safeText(candidate.reason(), "上下文补充")).append('\n');
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

    private AiDocumentContextNodeVo toContextNodeVo(ContextCandidate candidate) {
        AiDocumentTreeNodeVo node = candidate.node();
        AiDocumentContextNodeVo vo = new AiDocumentContextNodeVo();
        vo.setNodeId(candidate.nodeId());
        vo.setTitle(node.getTitle());
        vo.setLevel(node.getLevel());
        vo.setType(node.getType());
        vo.setRelation(candidate.relation());
        vo.setWeight(candidate.weight());
        vo.setReason(candidate.reason());
        vo.setSummary(node.getSummary());
        if (node.getSourceAnchors() != null && !node.getSourceAnchors().isEmpty()) {
            vo.setPage(node.getSourceAnchors().getFirst().getPage());
        }
        return vo;
    }

    private List<AiDocumentNodeCitationVo> buildCitations(List<ContextCandidate> usedCandidates, AiDocumentTreeNodeVo currentNode) {
        List<AiDocumentNodeCitationVo> citations = new ArrayList<>();
        for (ContextCandidate candidate : usedCandidates.stream().limit(6).toList()) {
            AiDocumentTreeNodeVo node = candidate.node();
            AiDocumentNodeCitationVo citation = new AiDocumentNodeCitationVo();
            citation.setNodeId(node.getId());
            citation.setTitle(node.getTitle());
            citation.setLevel(node.getLevel());
            citation.setType(node.getType());
            citation.setRelation(Objects.equals(node.getId(), currentNode.getId())
                    ? "current"
                    : candidate.relation());
            citations.add(citation);
        }
        return citations;
    }

    private List<AiDocumentTreeNodeVo> collectAncestorSiblingNodes(List<AiDocumentTreeNodeVo> trail) {
        if (trail == null || trail.size() < 2) {
            return List.of();
        }
        List<AiDocumentTreeNodeVo> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (int i = 0; i < trail.size() - 1; i++) {
            AiDocumentTreeNodeVo ancestor = trail.get(i);
            AiDocumentTreeNodeVo pathChild = trail.get(i + 1);
            List<AiDocumentTreeNodeVo> children = ancestor.getChildren() == null ? List.of() : ancestor.getChildren();
            boolean hasStructureChildren = children.stream().anyMatch(this::isStructuralNode);
            for (AiDocumentTreeNodeVo child : children) {
                if (Objects.equals(child.getId(), pathChild.getId())) {
                    continue;
                }
                if (hasStructureChildren && !isStructuralNode(child)) {
                    continue;
                }
                if (visited.add(child.getId())) {
                    result.add(child);
                }
            }
        }
        return result;
    }

    private boolean isStructuralNode(AiDocumentTreeNodeVo node) {
        String type = safeText(node == null ? null : node.getType(), "").toLowerCase(Locale.ROOT);
        return "document".equals(type) || "section".equals(type) || "subsection".equals(type);
    }

    private List<ContextCandidate> collectSemanticBridgeCandidates(AiDocumentTaskDetailVo detail,
                                                                   AiDocumentTreeNodeVo root,
                                                                   String question,
                                                                   AiDocumentTreeNodeVo currentNode,
                                                                   Set<String> excludedNodeIds,
                                                                   int maxBridgeNodes) {
        if (maxBridgeNodes <= 0) {
            return List.of();
        }
        List<ContextCandidate> bridgeCandidates = new ArrayList<>();
        if (detail != null && detail.getTaskId() != null) {
            bridgeCandidates.addAll(collectVectorBridgeCandidates(detail.getTaskId(), root, excludedNodeIds, question, maxBridgeNodes));
        }
        if (bridgeCandidates.size() >= maxBridgeNodes) {
            return bridgeCandidates.subList(0, maxBridgeNodes);
        }
        String questionText = safeText(question, "");
        String currentContext = joinNonBlank("\n",
                currentNode == null ? null : currentNode.getTitle(),
                currentNode == null ? null : currentNode.getSummary(),
                currentNode == null ? null : currentNode.getMarkdown());

        Set<String> excludedIds = new LinkedHashSet<>(excludedNodeIds);
        bridgeCandidates.forEach(candidate -> excludedIds.add(candidate.nodeId()));
        List<ContextCandidate> heuristicCandidates = new ArrayList<>();
        for (AiDocumentTreeNodeVo node : flattenTree(root)) {
            if (node == null || excludedIds.contains(node.getId())) {
                continue;
            }
            double score = scoreSemanticBridge(questionText, currentContext, node);
            if (score <= 0.12D) {
                continue;
            }
            heuristicCandidates.add(new ContextCandidate(
                    node,
                    node.getId(),
                    "semantic_bridge",
                    Math.min(0.86D, 0.48D + score * 0.42D),
                    "语义桥接补充节点"
            ));
        }
        heuristicCandidates.sort((left, right) -> Double.compare(right.weight(), left.weight()));
        for (ContextCandidate heuristicCandidate : heuristicCandidates) {
            if (bridgeCandidates.size() >= maxBridgeNodes) {
                break;
            }
            bridgeCandidates.add(heuristicCandidate);
        }
        if (bridgeCandidates.size() <= maxBridgeNodes) {
            return bridgeCandidates;
        }
        return bridgeCandidates.subList(0, maxBridgeNodes);
    }

    private List<ContextCandidate> collectVectorBridgeCandidates(Long taskId,
                                                                 AiDocumentTreeNodeVo root,
                                                                 Set<String> excludedNodeIds,
                                                                 String question,
                                                                 int maxBridgeNodes) {
        AiDocumentVectorIndexService vectorIndexService = documentVectorIndexServiceProvider.getIfAvailable();
        if (vectorIndexService == null || !vectorIndexService.isReady()) {
            return List.of();
        }
        List<AiDocumentChunkHit> hits;
        try {
            hits = vectorIndexService.searchRelevantChunks(taskId, question, Math.max(maxBridgeNodes * 2, maxBridgeNodes));
        } catch (Exception ex) {
            log.warn("文档任务语义桥接向量检索失败, taskId={}", taskId, ex);
            return List.of();
        }
        if (hits.isEmpty()) {
            return List.of();
        }
        List<ContextCandidate> candidates = new ArrayList<>();
        for (AiDocumentChunkHit hit : hits) {
            if (hit == null || !StringUtils.hasText(hit.nodeId()) || excludedNodeIds.contains(hit.nodeId())) {
                continue;
            }
            AiDocumentTreeNodeVo node = findNode(root, hit.nodeId());
            if (node == null) {
                continue;
            }
            double weight = Math.max(0.64D, 0.88D - (hit.rank() - 1) * 0.06D);
            String reason = StringUtils.hasText(hit.titlePath())
                    ? "向量召回补充节点：" + hit.titlePath()
                    : "向量召回补充节点";
            candidates.add(new ContextCandidate(
                    node,
                    node.getId(),
                    "semantic_bridge",
                    weight,
                    reason
            ));
            if (candidates.size() >= maxBridgeNodes) {
                break;
            }
        }
        return candidates;
    }

    private double scoreSemanticBridge(String questionText, String currentContext, AiDocumentTreeNodeVo candidateNode) {
        String candidateText = joinNonBlank("\n", candidateNode.getTitle(), candidateNode.getSummary(), candidateNode.getMarkdown());
        if (!StringUtils.hasText(candidateText)) {
            return 0D;
        }
        String normalizedQuestion = normalizeComparableText(questionText);
        String normalizedCurrent = normalizeComparableText(currentContext);
        String normalizedCandidate = normalizeComparableText(candidateText);
        if (!StringUtils.hasText(normalizedCandidate)) {
            return 0D;
        }
        double questionOverlap = overlapRatio(normalizedQuestion, normalizedCandidate);
        double currentOverlap = overlapRatio(normalizedCurrent, normalizedCandidate);
        double titleBonus = 0D;
        String candidateTitle = normalizeComparableText(candidateNode.getTitle());
        if (StringUtils.hasText(candidateTitle)
                && (normalizedQuestion.contains(candidateTitle) || normalizedCurrent.contains(candidateTitle))) {
            titleBonus = 0.18D;
        }
        return Math.min(1D, questionOverlap * 0.55D + currentOverlap * 0.45D + titleBonus);
    }

    private String normalizeComparableText(String rawText) {
        String normalized = safeText(rawText, "").toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isLetterOrDigit(current) || (current >= 0x4E00 && current <= 0x9FFF)) {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private double overlapRatio(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return 0D;
        }
        Set<Character> leftSet = new LinkedHashSet<>();
        for (int i = 0; i < left.length(); i++) {
            leftSet.add(left.charAt(i));
        }
        Set<Character> rightSet = new LinkedHashSet<>();
        for (int i = 0; i < right.length(); i++) {
            rightSet.add(right.charAt(i));
        }
        int overlap = 0;
        for (Character token : leftSet) {
            if (rightSet.contains(token)) {
                overlap += 1;
            }
        }
        return overlap / (double) Math.max(1, Math.min(leftSet.size(), rightSet.size()));
    }

    private List<AiDocumentKnowledgeFlowEdgeVo> buildKnowledgeFlowEdges(AiDocumentTreeNodeVo currentNode,
                                                                        List<AiDocumentTreeNodeVo> trail,
                                                                        List<ContextCandidate> usedCandidates) {
        Map<String, AiDocumentKnowledgeFlowEdgeVo> edgeMap = new LinkedHashMap<>();
        Set<String> usedNodeIds = usedCandidates.stream()
                .map(ContextCandidate::nodeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (int i = 0; i < trail.size() - 1; i++) {
            AiDocumentTreeNodeVo parent = trail.get(i);
            AiDocumentTreeNodeVo child = trail.get(i + 1);
            if (!usedNodeIds.contains(parent.getId()) || !usedNodeIds.contains(child.getId())) {
                continue;
            }
            addKnowledgeEdge(edgeMap, parent.getId(), child.getId(), "structural_parent", 1.0D, "文档树祖先路径");
        }

        for (ContextCandidate candidate : usedCandidates) {
            if (Objects.equals(candidate.nodeId(), currentNode.getId())) {
                continue;
            }
            switch (candidate.relation()) {
                case "descendant" -> addKnowledgeEdge(edgeMap, currentNode.getId(), candidate.nodeId(), "extends", candidate.weight(), candidate.reason());
                case "ancestor_sibling" -> addKnowledgeEdge(edgeMap, candidate.nodeId(), currentNode.getId(), "compares", candidate.weight(), candidate.reason());
                case "selected" -> addKnowledgeEdge(edgeMap, candidate.nodeId(), currentNode.getId(), "supports", candidate.weight(), candidate.reason());
                case "semantic_bridge" -> addKnowledgeEdge(edgeMap, candidate.nodeId(), currentNode.getId(), "bridges", candidate.weight(), candidate.reason());
                case "ancestor" -> addKnowledgeEdge(edgeMap, candidate.nodeId(), currentNode.getId(), "explains", candidate.weight(), candidate.reason());
                default -> {
                }
            }
        }
        return new ArrayList<>(edgeMap.values());
    }

    private void addKnowledgeEdge(Map<String, AiDocumentKnowledgeFlowEdgeVo> edgeMap,
                                  String fromNodeId,
                                  String toNodeId,
                                  String edgeType,
                                  double weight,
                                  String reason) {
        if (!StringUtils.hasText(fromNodeId) || !StringUtils.hasText(toNodeId) || Objects.equals(fromNodeId, toNodeId)) {
            return;
        }
        String edgeKey = fromNodeId + "->" + toNodeId + ":" + edgeType;
        AiDocumentKnowledgeFlowEdgeVo edgeVo = new AiDocumentKnowledgeFlowEdgeVo();
        edgeVo.setFromNodeId(fromNodeId);
        edgeVo.setToNodeId(toNodeId);
        edgeVo.setEdgeType(edgeType);
        edgeVo.setWeight(weight);
        edgeVo.setReason(reason);
        edgeMap.putIfAbsent(edgeKey, edgeVo);
    }

    private int safeLevel(AiDocumentTreeNodeVo node) {
        return node == null || node.getLevel() == null ? 0 : node.getLevel();
    }

    private List<AiDocumentTreeNodeVo> flattenTree(AiDocumentTreeNodeVo root) {
        if (root == null) {
            return List.of();
        }
        List<AiDocumentTreeNodeVo> nodes = new ArrayList<>();
        nodes.add(root);
        if (root.getChildren() != null) {
            for (AiDocumentTreeNodeVo child : root.getChildren()) {
                nodes.addAll(flattenTree(child));
            }
        }
        return nodes;
    }

    private String buildRootMarkdown(String title, String markdown) {
        if (StringUtils.hasText(markdown)) {
            return markdown;
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
            case "equation_interline", "equation_inline" -> "公式节点";
            default -> normalized;
        };
    }

    private String toMarkdownByType(String type, String text) {
        String normalizedType = safeText(type, "content");
        return switch (normalizedType) {
            case "code" -> "```\n" + text.trim() + "\n```";
            case "equation_interline", "equation_inline" -> "$$\n" + text.trim() + "\n$$";
            default -> text.trim();
        };
    }

    private void appendBufferedBlock(StringBuilder textBuffer, StringBuilder markdownBuffer, String type, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }

        String normalizedText = text.trim();
        String normalizedMarkdown = toMarkdownByType(type, normalizedText).trim();

        if (!textBuffer.isEmpty()) {
            textBuffer.append("\n\n");
        }
        textBuffer.append(normalizedText);

        if (!markdownBuffer.isEmpty()) {
            markdownBuffer.append("\n\n");
        }
        markdownBuffer.append(normalizedMarkdown);
    }

    private String extractMineruBlockText(JsonNode item, String type) {
        JsonNode contentNode = item == null ? null : item.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }

        return switch (safeText(type, "content")) {
            case "title" -> extractMineruText(contentNode.path("title_content"), "\n");
            case "paragraph" -> extractMineruText(contentNode.path("paragraph_content"), "\n");
            case "list" -> extractMineruText(contentNode.path("list_items"), "\n");
            case "code" -> extractMineruText(contentNode.path("code_content"), "\n");
            case "equation_interline", "equation_inline" -> safeText(textAt(contentNode, "math_content"), null);
            case "image" -> joinNonBlank("\n",
                    "图片",
                    extractMineruText(contentNode.path("image_caption"), "\n"),
                    extractMineruText(contentNode.path("image_footnote"), "\n"));
            case "table" -> {
                String html = safeText(textAt(contentNode, "html"), null);
                if (StringUtils.hasText(html)) {
                    yield html;
                }
                yield joinNonBlank("\n",
                        "表格",
                        extractMineruText(contentNode.path("table_caption"), "\n"),
                        extractMineruText(contentNode.path("table_footnote"), "\n"));
            }
            default -> extractMineruText(contentNode, "\n");
        };
    }

    private String extractMineruText(JsonNode node, String separator) {
        List<String> parts = new ArrayList<>();
        collectMineruText(node, parts);
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(separator, parts).trim();
    }

    private void collectMineruText(JsonNode node, List<String> parts) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectMineruText(child, parts);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if ("text".equals(node.path("type").asText()) && node.hasNonNull("content") && node.get("content").isTextual()) {
            String text = node.get("content").asText().trim();
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
            return;
        }
        if (node.hasNonNull("math_content") && node.get("math_content").isTextual()) {
            String text = node.get("math_content").asText().trim();
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
            return;
        }

        String[] orderedFields = {
                "title_content",
                "paragraph_content",
                "list_items",
                "item_content",
                "code_content",
                "table_caption",
                "table_footnote",
                "image_caption",
                "image_footnote",
                "html",
                "text",
                "content"
        };
        for (String field : orderedFields) {
            if (node.has(field)) {
                collectMineruText(node.get(field), parts);
            }
        }
    }

    private String joinNonBlank(String separator, String... values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized.isEmpty() ? null : String.join(separator, normalized);
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
            AiDocumentTreeNodeVo root = null;
            if (StringUtils.hasText(result.getContentListJson())) {
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

    private record ContextCandidate(AiDocumentTreeNodeVo node,
                                    String nodeId,
                                    String relation,
                                    double weight,
                                    String reason) {
    }

    private record ContextCompilation(AiDocumentTaskDetailVo detail,
                                      String queryMode,
                                      AiDocumentTreeNodeVo currentNode,
                                      List<ContextCandidate> candidates,
                                      List<ContextCandidate> usedCandidates,
                                      AiDocumentContextPlanVo contextPlan,
                                      AiDocumentContextBudgetVo budgetReport,
                                      List<AiDocumentKnowledgeFlowEdgeVo> knowledgeFlowEdges) {
    }

    private record MineruContentBlock(String type,
                                      int level,
                                      String text,
                                      Integer page,
                                      List<Double> bbox) {
    }

    private record ZipDocumentPayload(String markdown, String contentListJson) {
    }

    private record MockFixturePayload(String markdown,
                                      String contentListJson,
                                      String rawPayloadJson,
                                      int pageCount) {
    }
}

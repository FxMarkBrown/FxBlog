package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.config.ai.AiProperties;
import top.fxmarkbrown.blog.dto.ai.AiDocumentNodeAskDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskCreateDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskRenameDto;
import top.fxmarkbrown.blog.entity.SysAiDocumentNodeMessage;
import top.fxmarkbrown.blog.entity.SysAiDocumentNodeThread;
import top.fxmarkbrown.blog.entity.SysAiDocumentResult;
import top.fxmarkbrown.blog.entity.SysAiDocumentTask;
import top.fxmarkbrown.blog.mapper.SysAiDocumentNodeMessageMapper;
import top.fxmarkbrown.blog.mapper.SysAiDocumentNodeThreadMapper;
import top.fxmarkbrown.blog.mapper.SysAiDocumentResultMapper;
import top.fxmarkbrown.blog.mapper.SysAiDocumentTaskMapper;
import top.fxmarkbrown.blog.model.ai.AiDocumentChunkHit;
import top.fxmarkbrown.blog.model.ai.AiResolvedChatModel;
import top.fxmarkbrown.blog.service.*;
import top.fxmarkbrown.blog.utils.HttpUtil;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.ai.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentTaskServiceImpl implements AiDocumentTaskService {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");

    private final AiProperties aiProperties;
    private final AiChatService aiChatService;
    private final AiChatModelService aiChatModelService;
    private final AiModelQuotaBillingService aiModelQuotaBillingService;
    private final AiQuotaCoreService aiQuotaCoreService;
    private final ObjectProvider<AiDocumentVectorIndexService> documentVectorIndexServiceProvider;
    private final SysAiDocumentNodeThreadMapper documentNodeThreadMapper;
    private final SysAiDocumentNodeMessageMapper documentNodeMessageMapper;
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
    public AiDocumentNodeThreadVo getNodeThread(Long taskId, String nodeId) {
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        AiDocumentTreeNodeVo currentNode = findNode(aggregate.result().getRoot(), nodeId);
        if (currentNode == null) {
            throw new IllegalStateException("未找到目标节点: " + nodeId);
        }
        SysAiDocumentNodeThread thread = findNodeThread(taskId, nodeId, StpUtil.getLoginIdAsLong());
        return toNodeThreadVo(thread);
    }

    @Override
    public IPage<AiDocumentNodeMessageVo> pageNodeMessages(Long taskId, String nodeId) {
        DocumentTaskAggregate aggregate = requireAggregate(taskId);
        AiDocumentTreeNodeVo currentNode = findNode(aggregate.result().getRoot(), nodeId);
        if (currentNode == null) {
            throw new IllegalStateException("未找到目标节点: " + nodeId);
        }
        SysAiDocumentNodeThread thread = findNodeThread(taskId, nodeId, StpUtil.getLoginIdAsLong());
        if (thread == null) {
            Page<AiDocumentNodeMessageVo> emptyPage = PageUtil.getPage();
            emptyPage.setTotal(0);
            emptyPage.setRecords(List.of());
            return emptyPage;
        }
        Page<SysAiDocumentNodeMessage> page = documentNodeMessageMapper.selectPage(
                PageUtil.getPage(),
                new LambdaQueryWrapper<SysAiDocumentNodeMessage>()
                        .eq(SysAiDocumentNodeMessage::getThreadId, thread.getId())
                        .orderByAsc(SysAiDocumentNodeMessage::getCreateTime)
                        .orderByAsc(SysAiDocumentNodeMessage::getId)
        );
        Page<AiDocumentNodeMessageVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toNodeMessageVo).toList());
        return result;
    }

    @Override
    public AiDocumentTaskDetailVo createTask(AiDocumentTaskCreateDto createDto) {
        AiProperties.Document document = aiProperties.getDocument();
        if (document == null || !document.isEnabled()) {
            throw new IllegalStateException("文档任务未启用");
        }

        AiProperties.Mineru mineru = document.getMineru();
        if (mineru == null || !mineru.isEnabled()) {
            throw new IllegalStateException("MinerU 解析未启用，请先完成配置");
        }
        return createRealMineruTask(createDto);
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
    public SseEmitter streamAskNode(Long taskId, String nodeId, AiDocumentNodeAskDto askDto) {
        if (!aiProperties.isEnabled()) {
            throw new IllegalStateException("AI 功能未启用");
        }
        NodeAskPreparation preparation = prepareNodeAsk(taskId, nodeId, askDto);
        SysAiDocumentNodeThread thread = touchNodeThread(preparation);
        saveNodeUserMessage(thread.getId(), preparation.question());
        ChatClient chatClient = aiChatModelService.getChatClient(preparation.resolvedChatModel());
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        AtomicReference<StringBuilder> answerBuilder = new AtomicReference<>(new StringBuilder());
        AtomicInteger tokensIn = new AtomicInteger(0);
        AtomicInteger tokensOut = new AtomicInteger(0);
        AtomicInteger totalTokens = new AtomicInteger(0);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();

        emitter.onCompletion(() -> closeNodeAskStream(emitterClosed, disposableRef));
        emitter.onTimeout(() -> closeNodeAskStream(emitterClosed, disposableRef));
        emitter.onError(_ -> closeNodeAskStream(emitterClosed, disposableRef));

        sendNodeAskStreamEvent(
                emitter,
                emitterClosed,
                disposableRef,
                "meta",
                nodeStreamEvent("meta", null, buildNodeAnswerVo(preparation, thread.getId(), null), null, null, null, null)
        );

        Disposable disposable = chatClient.prompt()
                .system(buildNodeAskSystemPrompt())
                .user(buildNodeAskUserPrompt(
                        preparation.aggregate().detail(),
                        preparation.currentNode(),
                        preparation.question(),
                        preparation.contextPayload()
                ))
                .options(OpenAiChatOptions.builder()
                        .model(preparation.resolvedChatModel().modelName())
                        .temperature(Math.min(preparation.resolvedChatModel().temperature(), 0.35D))
                        .streamUsage(true)
                        .build())
                .stream()
                .chatResponse()
                .subscribe(
                        chatResponse -> handleNodeAskStreamChunk(
                                emitter,
                                emitterClosed,
                                disposableRef,
                                chatResponse,
                                answerBuilder,
                                tokensIn,
                                tokensOut,
                                totalTokens
                        ),
                        error -> {
                            log.error("文档节点流式问答失败, taskId={}, nodeId={}", taskId, nodeId, error);
                            sendNodeAskStreamEvent(
                                    emitter,
                                    emitterClosed,
                                    disposableRef,
                                    "error",
                                    nodeStreamEvent("error", null, null, error.getMessage(), null, null, null)
                            );
                            emitter.complete();
                        },
                        () -> completeNodeAskStream(
                                emitter,
                                emitterClosed,
                                disposableRef,
                                preparation,
                                thread,
                                answerBuilder,
                                tokensIn,
                                tokensOut,
                                totalTokens
                        )
                );
        disposableRef.set(disposable);
        return emitter;
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
            if (!StringUtils.hasText(mineru.getCallbackUid())) {
                throw new IllegalStateException("已配置 MinerU callback-url，但缺少 callback-uid");
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

    private NodeAskPreparation prepareNodeAsk(Long taskId, String nodeId, AiDocumentNodeAskDto askDto) {
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
        return new NodeAskPreparation(
                aggregate,
                root,
                currentNode,
                question,
                currentUserId,
                contextCompilation,
                contextPayload,
                resolveNodeAskModel(askDto)
        );
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

    private AiResolvedChatModel resolveNodeAskModel(AiDocumentNodeAskDto askDto) {
        String modelId = askDto == null ? null : askDto.getModelId();
        if (!StringUtils.hasText(modelId)) {
            return aiChatModelService.getDefaultModel();
        }
        return aiChatModelService.requireModel(modelId.trim());
    }

    private String buildNodeAskSystemPrompt() {
        return """
                你是博客文档工作台内的节点问答助手。
                你的任务是仅依据给定的文档节点上下文回答问题，不要臆造未出现在上下文中的事实。
                回答要求：
                1. 优先直接回答，再补充依据。
                2. 如果上下文不足，明确说明“当前节点上下文不足以确定”。
                3. 不要编造页码、作者意图或额外章节。
                4. 不要输出内部 nodeId、taskId 或数据库标识。
                5. 如果需要提醒用户去看画布证据，优先使用上下文里提供的“引用标签”或“箭头标签”，例如“看箭头 A1”或“看引用 R2”，不要编造新的标签。
                6. 只有在消歧确有必要时才补充节点标题，且标题应作为标签后的补充说明，而不是替代标签。
                7. 回答保持中文，简洁但信息完整。
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
        List<AiDocumentTreeNodeVo> selectedNodes = resolveSelectedNodes(root, currentNode, askDto);
        boolean includePeerContext = resolveIncludePeerContext(askDto, queryMode, selectedNodes);
        boolean enableRetrieval = resolveEnableRetrieval(askDto);
        int retrievedNodeBudget = resolveRetrievedNodeBudget(queryMode, enableRetrieval, resolveMaxRetrievedNodes(askDto, queryMode));

        List<AiDocumentTreeNodeVo> trail = new ArrayList<>();
        collectNodeTrail(root, currentNode.getId(), trail);
        List<AiDocumentTreeNodeVo> anchorNodes = buildAnchorNodes(currentNode, selectedNodes);
        int ancestorSupportDepth = resolveAncestorSupportDepth(queryMode);
        int currentDescendantDepth = resolveCurrentDescendantSupportDepth(queryMode, descendantDepth);
        int selectedDescendantDepth = resolveSelectedDescendantSupportDepth(queryMode, descendantDepth, selectedNodes);

        LinkedHashMap<String, ContextCandidate> candidateMap = new LinkedHashMap<>();
        addCandidate(candidateMap, currentNode, "current", 1.0D, "当前聚焦节点", currentNode.getId());

        for (AiDocumentTreeNodeVo selectedNode : selectedNodes) {
            addCandidate(candidateMap, selectedNode, "selected", 0.95D, "用户显式选中的对照节点", selectedNode.getId());
        }

        Set<String> excludedNodeIds = new LinkedHashSet<>(candidateMap.keySet());
        List<ContextCandidate> retrievedCandidates = collectRetrievedCandidates(
                detail,
                root,
                question,
                currentNode,
                selectedNodes,
                excludedNodeIds,
                retrievedNodeBudget
        );
        for (ContextCandidate retrievedCandidate : retrievedCandidates) {
            addCandidate(candidateMap,
                    retrievedCandidate.node(),
                    retrievedCandidate.relation(),
                    retrievedCandidate.weight(),
                    retrievedCandidate.reason(),
                    retrievedCandidate.anchorNodeId());
            excludedNodeIds.add(retrievedCandidate.nodeId());
        }

        for (ContextCandidate ancestorCandidate : collectAncestorSupportCandidates(root, anchorNodes, ancestorSupportDepth, candidateMap.keySet())) {
            addCandidate(candidateMap,
                    ancestorCandidate.node(),
                    ancestorCandidate.relation(),
                    ancestorCandidate.weight(),
                    ancestorCandidate.reason(),
                    ancestorCandidate.anchorNodeId());
        }

        for (ContextCandidate descendantCandidate : collectDescendantSupportCandidates(currentNode, currentDescendantDepth, "descendant", "当前节点的直接下文", currentNode.getId(), candidateMap.keySet())) {
            addCandidate(candidateMap,
                    descendantCandidate.node(),
                    descendantCandidate.relation(),
                    descendantCandidate.weight(),
                    descendantCandidate.reason(),
                    descendantCandidate.anchorNodeId());
        }

        for (AiDocumentTreeNodeVo selectedNode : selectedNodes) {
            for (ContextCandidate descendantCandidate : collectDescendantSupportCandidates(
                    selectedNode,
                    selectedDescendantDepth,
                    "selected_descendant",
                    "显式对照节点的直接下文",
                    selectedNode.getId(),
                    candidateMap.keySet()
            )) {
                addCandidate(candidateMap,
                        descendantCandidate.node(),
                        descendantCandidate.relation(),
                        descendantCandidate.weight(),
                        descendantCandidate.reason(),
                        descendantCandidate.anchorNodeId());
            }
        }

        if (includePeerContext) {
            for (ContextCandidate peerCandidate : collectPeerSupportCandidates(root, question, retrievedCandidates, candidateMap.keySet(), 2)) {
                addCandidate(candidateMap,
                        peerCandidate.node(),
                        peerCandidate.relation(),
                        peerCandidate.weight(),
                        peerCandidate.reason(),
                        peerCandidate.anchorNodeId());
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
        contextPlan.setMaxRetrievedNodes(retrievedNodeBudget);
        contextPlan.setAncestorCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "ancestor")).count());
        contextPlan.setDescendantCount((int) candidates.stream()
                .filter(candidate -> Objects.equals(candidate.relation(), "descendant") || Objects.equals(candidate.relation(), "selected_descendant"))
                .count());
        contextPlan.setPeerContextCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "peer_context")).count());
        contextPlan.setSelectedCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "selected")).count());
        contextPlan.setRetrievedCount((int) candidates.stream().filter(candidate -> Objects.equals(candidate.relation(), "retrieved")).count());
        contextPlan.setTotalCandidateCount(candidates.size());
        contextPlan.setTotalUsedCount(usedCandidates.size());

        AiDocumentContextBudgetVo budgetReport = new AiDocumentContextBudgetVo();
        budgetReport.setMaxChars(maxChars);
        budgetReport.setCandidateChars(candidateChars);
        budgetReport.setUsedChars(usedChars);
        budgetReport.setRemainingChars(Math.max(0, maxChars - usedChars));
        budgetReport.setTruncatedNodeCount(truncatedNodeCount);

        List<AiDocumentKnowledgeFlowEdgeVo> knowledgeFlowEdges = buildKnowledgeFlowEdges(currentNode, trail, usedCandidates, queryMode);
        return new ContextCompilation(detail, queryMode, currentNode, candidates, usedCandidates, contextPlan, budgetReport, knowledgeFlowEdges);
    }

    private void addCandidate(LinkedHashMap<String, ContextCandidate> candidateMap,
                              AiDocumentTreeNodeVo node,
                              String relation,
                              double weight,
                              String reason,
                              String anchorNodeId) {
        if (node == null || !StringUtils.hasText(node.getId())) {
            return;
        }
        ContextCandidate nextCandidate = new ContextCandidate(node, node.getId(), relation, weight, reason, anchorNodeId);
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
            case "selected" -> 95;
            case "retrieved" -> 90;
            case "selected_descendant" -> 80;
            case "ancestor" -> 75;
            case "descendant" -> 70;
            case "peer_context" -> 60;
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

    private boolean resolveIncludePeerContext(AiDocumentNodeAskDto askDto,
                                              String queryMode,
                                              List<AiDocumentTreeNodeVo> selectedNodes) {
        if (askDto != null && askDto.getIncludePeerContext() != null) {
            return askDto.getIncludePeerContext();
        }
        return "compare".equals(queryMode) && (selectedNodes == null || selectedNodes.isEmpty());
    }

    private boolean resolveEnableRetrieval(AiDocumentNodeAskDto askDto) {
        if (askDto != null && askDto.getEnableRetrieval() != null) {
            return askDto.getEnableRetrieval();
        }
        return true;
    }

    private int resolveMaxRetrievedNodes(AiDocumentNodeAskDto askDto, String queryMode) {
        Integer specifiedCount = askDto == null ? null : askDto.getMaxRetrievedNodes();
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

    private List<AiDocumentTreeNodeVo> buildAnchorNodes(AiDocumentTreeNodeVo currentNode,
                                                        List<AiDocumentTreeNodeVo> selectedNodes) {
        LinkedHashMap<String, AiDocumentTreeNodeVo> anchors = new LinkedHashMap<>();
        if (currentNode != null && StringUtils.hasText(currentNode.getId())) {
            anchors.put(currentNode.getId(), currentNode);
        }
        if (selectedNodes != null) {
            for (AiDocumentTreeNodeVo selectedNode : selectedNodes) {
                if (selectedNode == null || !StringUtils.hasText(selectedNode.getId())) {
                    continue;
                }
                anchors.putIfAbsent(selectedNode.getId(), selectedNode);
            }
        }
        return new ArrayList<>(anchors.values());
    }

    private int resolveRetrievedNodeBudget(String queryMode, boolean enableRetrieval, int configuredBudget) {
        if (!enableRetrieval) {
            return 0;
        }
        int baseBudget = switch (safeText(queryMode, "explain")) {
            case "locate" -> 2;
            case "compare" -> 3;
            default -> 4;
        };
        int normalizedConfiguredBudget = Math.max(0, configuredBudget);
        return normalizedConfiguredBudget > 0 ? Math.max(baseBudget, normalizedConfiguredBudget) : baseBudget;
    }

    private int resolveAncestorSupportDepth(String queryMode) {
        return switch (safeText(queryMode, "explain")) {
            case "compare", "locate" -> 0;
            default -> 1;
        };
    }

    private int resolveCurrentDescendantSupportDepth(String queryMode, int configuredDepth) {
        int cap = switch (safeText(queryMode, "explain")) {
            case "locate" -> 0;
            case "summarize" -> 2;
            default -> 1;
        };
        return Math.min(Math.max(configuredDepth, 0), cap);
    }

    private int resolveSelectedDescendantSupportDepth(String queryMode, int configuredDepth, List<AiDocumentTreeNodeVo> selectedNodes) {
        if (selectedNodes == null || selectedNodes.isEmpty()) {
            return 0;
        }
        int cap = switch (safeText(queryMode, "explain")) {
            case "compare", "summarize" -> 1;
            default -> 0;
        };
        return Math.min(Math.max(configuredDepth, 0), cap);
    }

    private List<ContextCandidate> collectRetrievedCandidates(AiDocumentTaskDetailVo detail,
                                                              AiDocumentTreeNodeVo root,
                                                              String question,
                                                              AiDocumentTreeNodeVo currentNode,
                                                              List<AiDocumentTreeNodeVo> selectedNodes,
                                                              Set<String> excludedNodeIds,
                                                              int budget) {
        if (budget <= 0 || root == null) {
            return List.of();
        }
        List<RetrievalQuery> queries = buildRetrievalQueries(question, currentNode, selectedNodes);
        List<ContextCandidate> vectorCandidates = collectVectorRetrievedCandidates(detail, root, queries, excludedNodeIds, budget);
        if (!vectorCandidates.isEmpty()) {
            return vectorCandidates;
        }
        return collectLexicalRetrievedCandidates(root, queries, excludedNodeIds, budget);
    }

    private List<RetrievalQuery> buildRetrievalQueries(String question,
                                                       AiDocumentTreeNodeVo currentNode,
                                                       List<AiDocumentTreeNodeVo> selectedNodes) {
        List<RetrievalQuery> queries = new ArrayList<>();
        appendRetrievalQuery(queries, question, currentNode == null ? null : currentNode.getId(), 1.0D, "问题检索");
        appendRetrievalQuery(
                queries,
                joinNonBlank("\n", question, currentNode == null ? null : currentNode.getTitle(), currentNode == null ? null : currentNode.getSummary()),
                currentNode == null ? null : currentNode.getId(),
                0.96D,
                "当前节点对齐检索"
        );
        if (selectedNodes != null) {
            for (AiDocumentTreeNodeVo selectedNode : selectedNodes) {
                appendRetrievalQuery(
                        queries,
                        joinNonBlank("\n", question, selectedNode == null ? null : selectedNode.getTitle(), selectedNode == null ? null : selectedNode.getSummary()),
                        selectedNode == null ? null : selectedNode.getId(),
                        0.94D,
                        "显式节点对齐检索"
                );
                appendRetrievalQuery(
                        queries,
                        joinNonBlank("\n",
                                question,
                                currentNode == null ? null : currentNode.getTitle(),
                                selectedNode == null ? null : selectedNode.getTitle()),
                        selectedNode == null ? null : selectedNode.getId(),
                        0.98D,
                        "当前节点与显式节点联合检索"
                );
            }
        }
        return queries;
    }

    private void appendRetrievalQuery(List<RetrievalQuery> queries,
                                      String queryText,
                                      String anchorNodeId,
                                      double baseWeight,
                                      String reasonPrefix) {
        if (!StringUtils.hasText(queryText)) {
            return;
        }
        String normalizedQuery = queryText.trim();
        if (queries.stream().anyMatch(query -> Objects.equals(query.text(), normalizedQuery))) {
            return;
        }
        queries.add(new RetrievalQuery(normalizedQuery, anchorNodeId, baseWeight, reasonPrefix));
    }

    private List<ContextCandidate> collectVectorRetrievedCandidates(AiDocumentTaskDetailVo detail,
                                                                    AiDocumentTreeNodeVo root,
                                                                    List<RetrievalQuery> queries,
                                                                    Set<String> excludedNodeIds,
                                                                    int budget) {
        if (detail == null || detail.getTaskId() == null || queries.isEmpty()) {
            return List.of();
        }
        AiDocumentVectorIndexService vectorIndexService = documentVectorIndexServiceProvider.getIfAvailable();
        if (vectorIndexService == null || !vectorIndexService.isReady()) {
            return List.of();
        }
        LinkedHashMap<String, ContextCandidate> retrieved = new LinkedHashMap<>();
        int perQueryTopK = Math.max(budget * 2, 4);
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            RetrievalQuery query = queries.get(queryIndex);
            List<AiDocumentChunkHit> hits;
            try {
                hits = vectorIndexService.searchRelevantChunks(detail.getTaskId(), query.text(), perQueryTopK);
            } catch (Exception ex) {
                log.warn("文档任务上下文向量检索失败, taskId={}, query={}", detail.getTaskId(), query.text(), ex);
                continue;
            }
            for (AiDocumentChunkHit hit : hits) {
                if (hit == null || !StringUtils.hasText(hit.nodeId()) || excludedNodeIds.contains(hit.nodeId())) {
                    continue;
                }
                AiDocumentTreeNodeVo node = findNode(root, hit.nodeId());
                if (node == null) {
                    continue;
                }
                double weight = Math.max(0.58D, query.baseWeight() - (hit.rank() - 1) * 0.07D - queryIndex * 0.03D);
                String reason = StringUtils.hasText(hit.titlePath())
                        ? query.reasonPrefix() + "：" + hit.titlePath()
                        : query.reasonPrefix();
                mergeRetrievedCandidate(retrieved, new ContextCandidate(
                        node,
                        node.getId(),
                        "retrieved",
                        weight,
                        reason,
                        query.anchorNodeId()
                ));
                if (retrieved.size() >= budget) {
                    return new ArrayList<>(retrieved.values());
                }
            }
        }
        return new ArrayList<>(retrieved.values());
    }

    private List<ContextCandidate> collectLexicalRetrievedCandidates(AiDocumentTreeNodeVo root,
                                                                     List<RetrievalQuery> queries,
                                                                     Set<String> excludedNodeIds,
                                                                     int budget) {
        if (queries.isEmpty()) {
            return List.of();
        }
        List<ContextCandidate> candidates = new ArrayList<>();
        for (AiDocumentTreeNodeVo node : flattenTree(root)) {
            if (node == null || !StringUtils.hasText(node.getId()) || excludedNodeIds.contains(node.getId())) {
                continue;
            }
            RetrievalQuery bestQuery = null;
            double bestScore = 0D;
            for (RetrievalQuery query : queries) {
                double score = scoreLexicalRetrieval(query.text(), node);
                if (score > bestScore) {
                    bestScore = score;
                    bestQuery = query;
                }
            }
            if (bestQuery == null || bestScore < 0.14D) {
                continue;
            }
            candidates.add(new ContextCandidate(
                    node,
                    node.getId(),
                    "retrieved",
                    Math.min(0.82D, 0.52D + bestScore * 0.34D),
                    bestQuery.reasonPrefix() + "：词法回退召回",
                    bestQuery.anchorNodeId()
            ));
        }
        candidates.sort((left, right) -> Double.compare(right.weight(), left.weight()));
        if (candidates.size() <= budget) {
            return candidates;
        }
        return candidates.subList(0, budget);
    }

    private void mergeRetrievedCandidate(Map<String, ContextCandidate> retrieved, ContextCandidate candidate) {
        if (candidate == null || !StringUtils.hasText(candidate.nodeId())) {
            return;
        }
        ContextCandidate existing = retrieved.get(candidate.nodeId());
        if (existing == null || candidate.weight() > existing.weight()) {
            retrieved.put(candidate.nodeId(), candidate);
        }
    }

    private List<ContextCandidate> collectAncestorSupportCandidates(AiDocumentTreeNodeVo root,
                                                                    List<AiDocumentTreeNodeVo> anchorNodes,
                                                                    int maxDepth,
                                                                    Set<String> excludedNodeIds) {
        if (root == null || maxDepth <= 0 || anchorNodes == null || anchorNodes.isEmpty()) {
            return List.of();
        }
        List<ContextCandidate> candidates = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (AiDocumentTreeNodeVo anchorNode : anchorNodes) {
            List<AiDocumentTreeNodeVo> trail = new ArrayList<>();
            if (!collectNodeTrail(root, anchorNode.getId(), trail) || trail.size() < 2) {
                continue;
            }
            int added = 0;
            for (int index = trail.size() - 2; index >= 0 && added < maxDepth; index--) {
                AiDocumentTreeNodeVo ancestor = trail.get(index);
                if (ancestor == null || isRootNode(ancestor) || excludedNodeIds.contains(ancestor.getId()) || !visited.add(ancestor.getId())) {
                    continue;
                }
                double weight = Math.max(0.64D, 0.82D - added * 0.08D);
                candidates.add(new ContextCandidate(
                        ancestor,
                        ancestor.getId(),
                        "ancestor",
                        weight,
                        "围绕锚点补充最近祖先",
                        anchorNode.getId()
                ));
                added++;
            }
        }
        return candidates;
    }

    private List<ContextCandidate> collectDescendantSupportCandidates(AiDocumentTreeNodeVo anchorNode,
                                                                      int maxDepth,
                                                                      String relation,
                                                                      String reason,
                                                                      String anchorNodeId,
                                                                      Set<String> excludedNodeIds) {
        if (anchorNode == null || maxDepth <= 0) {
            return List.of();
        }
        List<ContextCandidate> candidates = new ArrayList<>();
        for (AiDocumentTreeNodeVo descendant : collectDescendants(anchorNode, 1, maxDepth)) {
            if (descendant == null || excludedNodeIds.contains(descendant.getId())) {
                continue;
            }
            int relativeDepth = Math.max(1, Math.max(0, safeLevel(descendant) - safeLevel(anchorNode)));
            double weight = Math.max(0.58D, 0.84D - relativeDepth * 0.08D);
            candidates.add(new ContextCandidate(descendant, descendant.getId(), relation, weight, reason, anchorNodeId));
        }
        return candidates;
    }

    private List<ContextCandidate> collectPeerSupportCandidates(AiDocumentTreeNodeVo root,
                                                                String question,
                                                                List<ContextCandidate> retrievedCandidates,
                                                                Set<String> excludedNodeIds,
                                                                int budget) {
        if (root == null || budget <= 0 || retrievedCandidates == null || retrievedCandidates.isEmpty()) {
            return List.of();
        }
        List<ContextCandidate> peers = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (ContextCandidate retrievedCandidate : retrievedCandidates.stream().limit(2).toList()) {
            AiDocumentTreeNodeVo retrievedNode = retrievedCandidate.node();
            if (retrievedNode == null || !StringUtils.hasText(retrievedNode.getParentId())) {
                continue;
            }
            AiDocumentTreeNodeVo parent = findNode(root, retrievedNode.getParentId());
            if (parent == null || parent.getChildren() == null) {
                continue;
            }
            List<AiDocumentTreeNodeVo> siblings = new ArrayList<>(parent.getChildren());
            siblings.sort((left, right) -> Double.compare(scoreLexicalRetrieval(question, right), scoreLexicalRetrieval(question, left)));
            for (AiDocumentTreeNodeVo sibling : siblings) {
                if (sibling == null
                        || Objects.equals(sibling.getId(), retrievedNode.getId())
                        || excludedNodeIds.contains(sibling.getId())
                        || !visited.add(sibling.getId())
                        || scoreLexicalRetrieval(question, sibling) < 0.12D) {
                    continue;
                }
                peers.add(new ContextCandidate(
                        sibling,
                        sibling.getId(),
                        "peer_context",
                        0.62D,
                        "围绕主召回结果补充同层对照内容",
                        retrievedCandidate.anchorNodeId()
                ));
                if (peers.size() >= budget) {
                    return peers;
                }
            }
        }
        return peers;
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
        appendVisualReferenceIndex(builder, currentNode, contextCompilation);
        appendContextLane(builder, "Lane A - 当前节点", contextCompilation.usedCandidates(), "current");
        appendContextLane(builder, "Lane B - 显式选择节点", contextCompilation.usedCandidates(), "selected");
        appendContextLane(builder, "Lane C - RAG 主召回", contextCompilation.usedCandidates(), "retrieved");
        appendContextLane(builder, "Lane D - 祖先框架", contextCompilation.usedCandidates(), "ancestor");
        appendContextLane(builder, "Lane E - 当前节点与显式节点下文", contextCompilation.usedCandidates(), "descendant", "selected_descendant");
        appendContextLane(builder, "Lane F - 同层补充", contextCompilation.usedCandidates(), "peer_context");
        return builder.toString().trim();
    }

    private void appendVisualReferenceIndex(StringBuilder builder,
                                            AiDocumentTreeNodeVo currentNode,
                                            ContextCompilation contextCompilation) {
        List<ContextCandidate> usedCandidates = contextCompilation.usedCandidates();
        if (usedCandidates == null || usedCandidates.isEmpty()) {
            return;
        }
        builder.append("## 画布引用标签\n");
        for (int index = 0; index < usedCandidates.size(); index++) {
            ContextCandidate candidate = usedCandidates.get(index);
            AiDocumentTreeNodeVo node = candidate.node();
            builder.append("- ")
                    .append(buildCitationDisplayLabel(index))
                    .append(": ")
                    .append(safeText(node == null ? null : node.getTitle(), "未命名节点"))
                    .append("（")
                    .append(describeCandidateRelation(candidate, currentNode))
                    .append("）\n");
        }

        List<AiDocumentKnowledgeFlowEdgeVo> edges = contextCompilation.knowledgeFlowEdges();
        if (edges != null && !edges.isEmpty()) {
            Map<String, String> nodeTitleMap = usedCandidates.stream().collect(Collectors.toMap(
                    ContextCandidate::nodeId,
                    candidate -> safeText(candidate.node() == null ? null : candidate.node().getTitle(), "未命名节点"),
                    (left, _) -> left,
                    LinkedHashMap::new
            ));
            builder.append("## 画布箭头标签\n");
            for (AiDocumentKnowledgeFlowEdgeVo edge : edges) {
                builder.append("- ")
                        .append(safeText(edge.getDisplayLabel(), "箭头"))
                        .append(": ")
                        .append(safeText(nodeTitleMap.get(edge.getFromNodeId()), "未命名节点"))
                        .append(" -> ")
                        .append(safeText(nodeTitleMap.get(edge.getToNodeId()), "未命名节点"))
                        .append("（")
                        .append(describeKnowledgeEdgeType(edge.getEdgeType()))
                        .append("）\n");
            }
        }
        builder.append('\n');
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

    private void handleNodeAskStreamChunk(SseEmitter emitter,
                                          AtomicBoolean emitterClosed,
                                          AtomicReference<Disposable> disposableRef,
                                          ChatResponse chatResponse,
                                          AtomicReference<StringBuilder> answerBuilder,
                                          AtomicInteger tokensIn,
                                          AtomicInteger tokensOut,
                                          AtomicInteger totalTokens) {
        String answerDelta = extractAnswer(chatResponse);
        if (StringUtils.hasText(answerDelta)) {
            answerBuilder.get().append(answerDelta);
        }
        Usage usage = aiChatService.extractUsage(chatResponse);
        if (usage != null) {
            tokensIn.set(defaultInt(usage.getPromptTokens()));
            tokensOut.set(defaultInt(usage.getCompletionTokens()));
            totalTokens.set(defaultInt(usage.getTotalTokens()));
        }
        if (!StringUtils.hasText(answerDelta)) {
            return;
        }
        sendNodeAskStreamEvent(
                emitter,
                emitterClosed,
                disposableRef,
                "delta",
                nodeStreamEvent("delta", answerDelta, null, null, zeroToNull(tokensIn.get()), zeroToNull(tokensOut.get()), zeroToNull(totalTokens.get()))
        );
    }

    private void completeNodeAskStream(SseEmitter emitter,
                                       AtomicBoolean emitterClosed,
                                       AtomicReference<Disposable> disposableRef,
                                       NodeAskPreparation preparation,
                                       SysAiDocumentNodeThread thread,
                                       AtomicReference<StringBuilder> answerBuilder,
                                       AtomicInteger tokensIn,
                                       AtomicInteger tokensOut,
                                       AtomicInteger totalTokens) {
        String answer = answerBuilder.get().toString().trim();
        if (!StringUtils.hasText(answer)) {
            answer = "模型未返回有效内容";
        }
        aiQuotaCoreService.consumeTokens(
                preparation.currentUserId(),
                aiModelQuotaBillingService.resolveBilledTokens(
                        resolveConsumedTokens(preparation.question(), answer, tokensIn.get(), tokensOut.get(), totalTokens.get()),
                        preparation.resolvedChatModel()
                ),
                preparation.aggregate().detail().getTitle(),
                "文档节点问答消耗"
        );
        AiDocumentNodeAnswerVo answerVo = buildNodeAnswerVo(preparation, thread.getId(), answer);
        saveNodeAssistantMessage(
                thread.getId(),
                answerVo,
                zeroToNull(tokensIn.get()),
                zeroToNull(tokensOut.get())
        );
        sendNodeAskStreamEvent(
                emitter,
                emitterClosed,
                disposableRef,
                "done",
                nodeStreamEvent(
                        "done",
                        null,
                        answerVo,
                        null,
                        zeroToNull(tokensIn.get()),
                        zeroToNull(tokensOut.get()),
                        zeroToNull(totalTokens.get())
                )
        );
        emitter.complete();
    }

    private SysAiDocumentNodeThread findNodeThread(Long taskId, String nodeId, Long userId) {
        if (taskId == null || userId == null || !StringUtils.hasText(nodeId)) {
            return null;
        }
        return documentNodeThreadMapper.selectOne(new LambdaQueryWrapper<SysAiDocumentNodeThread>()
                .eq(SysAiDocumentNodeThread::getTaskId, taskId)
                .eq(SysAiDocumentNodeThread::getUserId, userId)
                .eq(SysAiDocumentNodeThread::getNodeId, nodeId.trim())
                .last("limit 1"));
    }

    private SysAiDocumentNodeThread touchNodeThread(NodeAskPreparation preparation) {
        LocalDateTime now = LocalDateTime.now();
        SysAiDocumentNodeThread thread = findNodeThread(
                preparation.aggregate().detail().getTaskId(),
                preparation.currentNode().getId(),
                preparation.currentUserId()
        );
        String summary = buildNodeThreadSummary(preparation.currentNode());
        if (thread == null) {
            thread = SysAiDocumentNodeThread.builder()
                    .taskId(preparation.aggregate().detail().getTaskId())
                    .userId(preparation.currentUserId())
                    .nodeId(preparation.currentNode().getId())
                    .title(safeText(preparation.currentNode().getTitle(), "节点对话"))
                    .summary(summary)
                    .modelProvider(preparation.resolvedChatModel().providerName())
                    .modelName(preparation.resolvedChatModel().modelName())
                    .lastMessageAt(now)
                    .build();
            documentNodeThreadMapper.insert(thread);
            return thread;
        }
        thread.setTitle(safeText(preparation.currentNode().getTitle(), "节点对话"));
        thread.setSummary(summary);
        thread.setModelProvider(preparation.resolvedChatModel().providerName());
        thread.setModelName(preparation.resolvedChatModel().modelName());
        thread.setLastMessageAt(now);
        documentNodeThreadMapper.updateById(thread);
        return thread;
    }

    private String buildNodeThreadSummary(AiDocumentTreeNodeVo node) {
        String summary = safeText(node == null ? null : node.getSummary(), null);
        if (StringUtils.hasText(summary)) {
            return summary;
        }
        String markdown = safeText(node == null ? null : node.getMarkdown(), null);
        if (!StringUtils.hasText(markdown)) {
            return safeText(node == null ? null : node.getTitle(), "文档节点问答");
        }
        return summarizeText(markdown.replace('\n', ' ').replace('\r', ' '));
    }

    private void saveNodeUserMessage(Long threadId, String question) {
        if (threadId == null || !StringUtils.hasText(question)) {
            return;
        }
        documentNodeMessageMapper.insert(SysAiDocumentNodeMessage.builder()
                .threadId(threadId)
                .role(Constants.AI_MESSAGE_ROLE_USER)
                .content(question.trim())
                .build());
    }

    private void saveNodeAssistantMessage(Long threadId,
                                          AiDocumentNodeAnswerVo answerVo,
                                          Integer tokensIn,
                                          Integer tokensOut) {
        if (threadId == null || answerVo == null || !StringUtils.hasText(answerVo.getAnswer())) {
            return;
        }
        documentNodeMessageMapper.insert(SysAiDocumentNodeMessage.builder()
                .threadId(threadId)
                .role(Constants.AI_MESSAGE_ROLE_ASSISTANT)
                .content(answerVo.getAnswer().trim())
                .tokensIn(tokensIn)
                .tokensOut(tokensOut)
                .quotePayload(buildNodeQuotePayload(answerVo))
                .build());
        documentNodeThreadMapper.updateById(SysAiDocumentNodeThread.builder()
                .id(threadId)
                .lastMessageAt(LocalDateTime.now())
                .build());
    }

    private String buildNodeQuotePayload(AiDocumentNodeAnswerVo answerVo) {
        if (answerVo == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(answerVo.getModelId())) {
            payload.put("modelId", answerVo.getModelId());
        }
        if (answerVo.getContextNodeIds() != null && !answerVo.getContextNodeIds().isEmpty()) {
            payload.put("contextNodeIds", answerVo.getContextNodeIds());
        }
        List<String> selectedNodeIds = extractSelectedNodeIds(answerVo);
        if (!selectedNodeIds.isEmpty()) {
            payload.put("selectedNodeIds", selectedNodeIds);
        }
        if (answerVo.getCitations() != null && !answerVo.getCitations().isEmpty()) {
            payload.put("citations", answerVo.getCitations());
        }
        if (answerVo.getContextPlan() != null) {
            payload.put("contextPlan", answerVo.getContextPlan());
        }
        if (answerVo.getBudgetReport() != null) {
            payload.put("budgetReport", answerVo.getBudgetReport());
        }
        if (answerVo.getUsedNodes() != null && !answerVo.getUsedNodes().isEmpty()) {
            payload.put("usedNodes", answerVo.getUsedNodes());
        }
        if (answerVo.getCandidateNodes() != null && !answerVo.getCandidateNodes().isEmpty()) {
            payload.put("candidateNodes", answerVo.getCandidateNodes());
        }
        if (answerVo.getKnowledgeFlowEdges() != null && !answerVo.getKnowledgeFlowEdges().isEmpty()) {
            payload.put("knowledgeFlowEdges", answerVo.getKnowledgeFlowEdges());
        }
        return payload.isEmpty() ? null : JsonUtil.toJsonString(payload);
    }

    private List<String> extractSelectedNodeIds(AiDocumentNodeAnswerVo answerVo) {
        Set<String> selectedNodeIds = new LinkedHashSet<>();
        if (answerVo == null) {
            return List.of();
        }
        collectSelectedNodeIds(selectedNodeIds, answerVo.getUsedNodes());
        collectSelectedNodeIds(selectedNodeIds, answerVo.getCandidateNodes());
        return new ArrayList<>(selectedNodeIds);
    }

    private void collectSelectedNodeIds(Set<String> selectedNodeIds, List<AiDocumentContextNodeVo> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (AiDocumentContextNodeVo node : nodes) {
            if (node == null || !Objects.equals(node.getRelation(), "selected") || !StringUtils.hasText(node.getNodeId())) {
                continue;
            }
            selectedNodeIds.add(node.getNodeId());
        }
    }

    private void sendNodeAskStreamEvent(SseEmitter emitter,
                                        AtomicBoolean emitterClosed,
                                        AtomicReference<Disposable> disposableRef,
                                        String eventName,
                                        AiDocumentNodeStreamEventVo event) {
        if (emitterClosed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(event));
        } catch (IOException | IllegalStateException ex) {
            closeNodeAskStream(emitterClosed, disposableRef);
            log.warn("文档节点流事件发送失败, event={}", eventName, ex);
        }
    }

    private void closeNodeAskStream(AtomicBoolean emitterClosed, AtomicReference<Disposable> disposableRef) {
        emitterClosed.set(true);
        Disposable disposable = disposableRef.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private AiDocumentNodeStreamEventVo nodeStreamEvent(String type,
                                                        String content,
                                                        AiDocumentNodeAnswerVo answer,
                                                        String errorMessage,
                                                        Integer tokensIn,
                                                        Integer tokensOut,
                                                        Integer totalTokens) {
        AiDocumentNodeStreamEventVo event = new AiDocumentNodeStreamEventVo();
        event.setType(type);
        event.setContent(content);
        event.setAnswer(answer);
        event.setErrorMessage(errorMessage);
        event.setTokensIn(tokensIn);
        event.setTokensOut(tokensOut);
        event.setTotalTokens(totalTokens);
        return event;
    }

    private AiDocumentNodeAnswerVo buildNodeAnswerVo(NodeAskPreparation preparation, Long threadId, String answer) {
        AiDocumentNodeAnswerVo vo = new AiDocumentNodeAnswerVo();
        vo.setTaskId(preparation.aggregate().detail().getTaskId());
        vo.setThreadId(threadId);
        vo.setNodeId(preparation.currentNode().getId());
        vo.setQuestion(preparation.question());
        vo.setAnswer(answer);
        vo.setModelId(preparation.resolvedChatModel().modelId());
        vo.setContextNodeIds(preparation.contextCompilation().usedCandidates().stream().map(ContextCandidate::nodeId).toList());
        vo.setCitations(buildCitations(preparation.contextCompilation().usedCandidates(), preparation.currentNode()));
        vo.setContextPlan(preparation.contextCompilation().contextPlan());
        vo.setBudgetReport(preparation.contextCompilation().budgetReport());
        vo.setUsedNodes(preparation.contextCompilation().usedCandidates().stream().map(this::toContextNodeVo).toList());
        vo.setCandidateNodes(preparation.contextCompilation().candidates().stream().map(this::toContextNodeVo).toList());
        vo.setKnowledgeFlowEdges(preparation.contextCompilation().knowledgeFlowEdges());
        return vo;
    }

    private AiDocumentNodeThreadVo toNodeThreadVo(SysAiDocumentNodeThread thread) {
        if (thread == null) {
            return null;
        }
        AiDocumentNodeThreadVo vo = new AiDocumentNodeThreadVo();
        vo.setThreadId(thread.getId());
        vo.setTaskId(thread.getTaskId());
        vo.setNodeId(thread.getNodeId());
        vo.setTitle(thread.getTitle());
        vo.setSummary(thread.getSummary());
        vo.setModelProvider(thread.getModelProvider());
        vo.setModelName(thread.getModelName());
        vo.setModelId(aiChatModelService.resolveModelId(thread.getModelProvider(), thread.getModelName()));
        vo.setModelDisplayName(aiChatModelService.resolveDisplayName(thread.getModelProvider(), thread.getModelName()));
        vo.setLastMessageAt(thread.getLastMessageAt());
        vo.setCreateTime(thread.getCreateTime());
        vo.setUpdateTime(thread.getUpdateTime());
        return vo;
    }

    private AiDocumentNodeMessageVo toNodeMessageVo(SysAiDocumentNodeMessage message) {
        AiDocumentNodeMessageVo vo = new AiDocumentNodeMessageVo();
        vo.setId(message.getId());
        vo.setThreadId(message.getThreadId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setTokensIn(message.getTokensIn());
        vo.setTokensOut(message.getTokensOut());
        vo.setQuotePayload(message.getQuotePayload());
        JsonNode quotePayload = parseNodeQuotePayload(message.getQuotePayload());
        vo.setModelId(textAt(quotePayload, "modelId"));
        vo.setSelectedNodeIds(parseNodeQuoteList(quotePayload, "selectedNodeIds", new TypeReference<>() {
        }));
        vo.setCitations(parseNodeQuoteList(quotePayload, "citations", new TypeReference<>() {
        }));
        vo.setContextPlan(parseNodeQuoteObject(quotePayload, "contextPlan", AiDocumentContextPlanVo.class));
        vo.setBudgetReport(parseNodeQuoteObject(quotePayload, "budgetReport", AiDocumentContextBudgetVo.class));
        vo.setUsedNodes(parseNodeQuoteList(quotePayload, "usedNodes", new TypeReference<>() {
        }));
        vo.setCandidateNodes(parseNodeQuoteList(quotePayload, "candidateNodes", new TypeReference<>() {
        }));
        vo.setKnowledgeFlowEdges(parseNodeQuoteList(quotePayload, "knowledgeFlowEdges", new TypeReference<>() {
        }));
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private JsonNode parseNodeQuotePayload(String quotePayload) {
        if (!StringUtils.hasText(quotePayload)) {
            return null;
        }
        try {
            return JsonUtil.readTree(quotePayload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> T parseNodeQuoteObject(JsonNode quotePayload, String fieldName, Class<T> targetType) {
        if (quotePayload == null || !StringUtils.hasText(fieldName) || !quotePayload.hasNonNull(fieldName)) {
            return null;
        }
        try {
            return JsonUtil.convertValue(quotePayload.get(fieldName), targetType);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> List<T> parseNodeQuoteList(JsonNode quotePayload, String fieldName, TypeReference<List<T>> valueTypeRef) {
        if (quotePayload == null || !StringUtils.hasText(fieldName) || !quotePayload.hasNonNull(fieldName)) {
            return List.of();
        }
        JsonNode node = quotePayload.get(fieldName);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        try {
            List<T> records = JsonUtil.convertValue(node, valueTypeRef);
            return records == null ? List.of() : records;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String extractAnswer(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        } else {
            chatResponse.getResult();
        }
        return safeText(chatResponse.getResult().getOutput().getText(), null);
    }

    private long resolveConsumedTokens(String question, String answer, int tokensIn, int tokensOut, int totalTokens) {
        if (totalTokens > 0) {
            return totalTokens;
        }
        int combined = tokensIn + tokensOut;
        if (combined > 0) {
            return combined;
        }
        int estimatedChars = safeText(question, "").length() + safeText(answer, "").length();
        return Math.max(estimatedChars / 2L, 400L);
    }

    private Integer zeroToNull(int value) {
        return value > 0 ? value : null;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
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
        List<ContextCandidate> visibleCandidates = usedCandidates.stream().limit(6).toList();
        for (int index = 0; index < visibleCandidates.size(); index++) {
            ContextCandidate candidate = visibleCandidates.get(index);
            AiDocumentTreeNodeVo node = candidate.node();
            AiDocumentNodeCitationVo citation = new AiDocumentNodeCitationVo();
            citation.setDisplayLabel(buildCitationDisplayLabel(index));
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

    private double scoreLexicalRetrieval(String queryText, AiDocumentTreeNodeVo candidateNode) {
        String candidateText = joinNonBlank("\n",
                candidateNode == null ? null : candidateNode.getTitle(),
                candidateNode == null ? null : candidateNode.getSummary(),
                candidateNode == null ? null : candidateNode.getMarkdown());
        if (!StringUtils.hasText(candidateText)) {
            return 0D;
        }
        String normalizedQuery = normalizeComparableText(queryText);
        String normalizedCandidate = normalizeComparableText(candidateText);
        if (!StringUtils.hasText(normalizedQuery) || !StringUtils.hasText(normalizedCandidate)) {
            return 0D;
        }
        double overlap = overlapRatio(normalizedQuery, normalizedCandidate);
        double containsBonus = normalizedCandidate.contains(normalizedQuery) ? 0.18D : 0D;
        String candidateTitle = normalizeComparableText(candidateNode == null ? null : candidateNode.getTitle());
        double titleBonus = StringUtils.hasText(candidateTitle) && normalizedQuery.contains(candidateTitle) ? 0.12D : 0D;
        return Math.min(1D, overlap + containsBonus + titleBonus);
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
                                                                        List<ContextCandidate> usedCandidates,
                                                                        String queryMode) {
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
            String anchorNodeId = StringUtils.hasText(candidate.anchorNodeId()) ? candidate.anchorNodeId() : currentNode.getId();
            switch (candidate.relation()) {
                case "descendant", "selected_descendant" ->
                        addKnowledgeEdge(edgeMap, anchorNodeId, candidate.nodeId(), "extends", candidate.weight(), candidate.reason());
                case "peer_context" ->
                        addKnowledgeEdge(edgeMap, candidate.nodeId(), anchorNodeId, "compares", candidate.weight(), candidate.reason());
                case "selected" -> addKnowledgeEdge(
                        edgeMap,
                        candidate.nodeId(),
                        currentNode.getId(),
                        Objects.equals(queryMode, "compare") ? "compares" : "supports",
                        candidate.weight(),
                        candidate.reason()
                );
                case "retrieved" -> addKnowledgeEdge(edgeMap, candidate.nodeId(), anchorNodeId, "retrieves", candidate.weight(), candidate.reason());
                case "ancestor" -> addKnowledgeEdge(edgeMap, candidate.nodeId(), anchorNodeId, "explains", candidate.weight(), candidate.reason());
                default -> {
                }
            }
        }
        List<AiDocumentKnowledgeFlowEdgeVo> edges = new ArrayList<>(edgeMap.values());
        for (int index = 0; index < edges.size(); index++) {
            edges.get(index).setDisplayLabel(buildKnowledgeEdgeDisplayLabel(index));
        }
        return edges;
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

    private String buildCitationDisplayLabel(int index) {
        return "R" + (index + 1);
    }

    private String buildKnowledgeEdgeDisplayLabel(int index) {
        return "A" + (index + 1);
    }

    private String describeCandidateRelation(ContextCandidate candidate, AiDocumentTreeNodeVo currentNode) {
        if (candidate == null) {
            return "上下文";
        }
        return switch (safeText(candidate.relation(), "")) {
            case "current" -> "当前节点";
            case "selected" -> "显式节点";
            case "retrieved" -> "RAG召回";
            case "ancestor" -> "祖先框架";
            case "descendant" -> Objects.equals(candidate.nodeId(), currentNode == null ? null : currentNode.getId()) ? "当前节点" : "当前下文";
            case "selected_descendant" -> "显式节点下文";
            case "peer_context" -> "同层补充";
            default -> "上下文";
        };
    }

    private String describeKnowledgeEdgeType(String edgeType) {
        return switch (safeText(edgeType, "").toLowerCase(Locale.ROOT)) {
            case "retrieves" -> "检索命中";
            case "supports" -> "补充支持";
            case "explains" -> "上文解释";
            case "extends" -> "下文展开";
            case "compares" -> "对照关系";
            default -> "关联";
        };
    }

    private boolean isRootNode(AiDocumentTreeNodeVo node) {
        return "document".equalsIgnoreCase(safeText(node == null ? null : node.getType(), ""))
                && !StringUtils.hasText(node == null ? null : node.getParentId());
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
                                    String reason,
                                    String anchorNodeId) {
    }

    private record RetrievalQuery(String text,
                                  String anchorNodeId,
                                  double baseWeight,
                                  String reasonPrefix) {
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

    private record NodeAskPreparation(DocumentTaskAggregate aggregate,
                                      AiDocumentTreeNodeVo root,
                                      AiDocumentTreeNodeVo currentNode,
                                      String question,
                                      long currentUserId,
                                      ContextCompilation contextCompilation,
                                      String contextPayload,
                                      AiResolvedChatModel resolvedChatModel) {
    }
}

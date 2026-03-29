package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.entity.*;
import top.fxmarkbrown.blog.mapper.*;
import top.fxmarkbrown.blog.service.AiChatModelService;
import top.fxmarkbrown.blog.service.AiDocumentVectorIndexService;
import top.fxmarkbrown.blog.service.SysAiDocumentTaskService;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.ai.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiDocumentTaskServiceImpl implements SysAiDocumentTaskService {

    private final SysAiDocumentTaskMapper documentTaskMapper;
    private final SysAiDocumentResultMapper documentResultMapper;
    private final SysAiDocumentNodeThreadMapper documentNodeThreadMapper;
    private final SysAiDocumentNodeMessageMapper documentNodeMessageMapper;
    private final SysUserMapper userMapper;
    private final AiChatModelService aiChatModelService;
    private final ObjectProvider<AiDocumentVectorIndexService> documentVectorIndexServiceProvider;

    @Override
    public IPage<AiDocumentTaskAdminVo> pageTasks(String status, String provider, String keyword, String userKeyword) {
        Page<SysAiDocumentTask> pageRequest = PageUtil.getPage();
        LambdaQueryWrapper<SysAiDocumentTask> wrapper = new LambdaQueryWrapper<SysAiDocumentTask>()
                .orderByDesc(SysAiDocumentTask::getUpdateTime)
                .orderByDesc(SysAiDocumentTask::getId);

        if (StringUtils.hasText(status)) {
            wrapper.eq(SysAiDocumentTask::getStatus, status.trim());
        }
        if (StringUtils.hasText(provider)) {
            wrapper.eq(SysAiDocumentTask::getProvider, provider.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(q -> q.like(SysAiDocumentTask::getTitle, normalizedKeyword)
                    .or()
                    .like(SysAiDocumentTask::getFileName, normalizedKeyword)
                    .or()
                    .like(SysAiDocumentTask::getRemoteTaskId, normalizedKeyword));
        }
        if (StringUtils.hasText(userKeyword)) {
            List<Long> matchedUserIds = findMatchedUserIds(userKeyword);
            if (matchedUserIds.isEmpty()) {
                return emptyTaskPage(pageRequest);
            }
            wrapper.in(SysAiDocumentTask::getUserId, matchedUserIds);
        }

        Page<SysAiDocumentTask> page = documentTaskMapper.selectPage(pageRequest, wrapper);
        if (page.getRecords().isEmpty()) {
            return emptyTaskPage(page);
        }

        List<Long> taskIds = page.getRecords().stream()
                .map(SysAiDocumentTask::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SysUser> userMap = loadUserMap(page.getRecords().stream().map(SysAiDocumentTask::getUserId).toList());
        Map<Long, SysAiDocumentResult> resultMap = loadResultMap(taskIds);
        Map<Long, List<SysAiDocumentNodeThread>> taskThreadMap = loadTaskThreadMap(taskIds);
        Map<Long, Long> taskMessageCountMap = loadTaskMessageCountMap(taskThreadMap);

        Page<AiDocumentTaskAdminVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(task -> toTaskAdminVo(task, userMap.get(task.getUserId()), resultMap.get(task.getId()),
                        taskThreadMap.getOrDefault(task.getId(), List.of()),
                        taskMessageCountMap.getOrDefault(task.getId(), 0L)))
                .toList());
        return result;
    }

    @Override
    public IPage<AiDocumentNodeThreadAdminVo> pageThreads(Long taskId, String keyword) {
        Page<SysAiDocumentNodeThread> pageRequest = PageUtil.getPage();
        SysAiDocumentTask task = documentTaskMapper.selectById(taskId);
        if (task == null) {
            return emptyThreadPage(pageRequest);
        }

        LambdaQueryWrapper<SysAiDocumentNodeThread> wrapper = new LambdaQueryWrapper<SysAiDocumentNodeThread>()
                .eq(SysAiDocumentNodeThread::getTaskId, taskId)
                .orderByDesc(SysAiDocumentNodeThread::getLastMessageAt)
                .orderByDesc(SysAiDocumentNodeThread::getId);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(q -> q.like(SysAiDocumentNodeThread::getTitle, normalizedKeyword)
                    .or()
                    .like(SysAiDocumentNodeThread::getSummary, normalizedKeyword)
                    .or()
                    .like(SysAiDocumentNodeThread::getNodeId, normalizedKeyword)
                    .or()
                    .like(SysAiDocumentNodeThread::getModelName, normalizedKeyword));
        }

        Page<SysAiDocumentNodeThread> page = documentNodeThreadMapper.selectPage(pageRequest, wrapper);
        if (page.getRecords().isEmpty()) {
            return emptyThreadPage(page);
        }

        List<Long> threadIds = page.getRecords().stream()
                .map(SysAiDocumentNodeThread::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Long> threadMessageCountMap = loadThreadMessageCountMap(threadIds);
        Map<String, String> nodeTitleMap = loadNodeTitleMap(taskId);

        Page<AiDocumentNodeThreadAdminVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(thread -> toThreadAdminVo(thread, nodeTitleMap, threadMessageCountMap.getOrDefault(thread.getId(), 0L)))
                .toList());
        return result;
    }

    @Override
    public IPage<AiDocumentNodeMessageVo> pageMessages(Long threadId) {
        Page<SysAiDocumentNodeMessage> pageRequest = PageUtil.getPage();
        if (documentNodeThreadMapper.selectById(threadId) == null) {
            return emptyMessagePage(pageRequest);
        }
        Page<SysAiDocumentNodeMessage> page = documentNodeMessageMapper.selectPage(
                pageRequest,
                new LambdaQueryWrapper<SysAiDocumentNodeMessage>()
                        .eq(SysAiDocumentNodeMessage::getThreadId, threadId)
                        .orderByAsc(SysAiDocumentNodeMessage::getCreateTime)
                        .orderByAsc(SysAiDocumentNodeMessage::getId)
        );
        Page<AiDocumentNodeMessageVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toNodeMessageVo).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasks(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return;
        }

        List<SysAiDocumentNodeThread> threads = documentNodeThreadMapper.selectList(new LambdaQueryWrapper<SysAiDocumentNodeThread>()
                .in(SysAiDocumentNodeThread::getTaskId, normalizedIds));
        List<Long> threadIds = threads.stream()
                .map(SysAiDocumentNodeThread::getId)
                .filter(Objects::nonNull)
                .toList();

        if (!threadIds.isEmpty()) {
            documentNodeMessageMapper.delete(new LambdaQueryWrapper<SysAiDocumentNodeMessage>()
                    .in(SysAiDocumentNodeMessage::getThreadId, threadIds));
        }
        documentNodeThreadMapper.delete(new LambdaQueryWrapper<SysAiDocumentNodeThread>()
                .in(SysAiDocumentNodeThread::getTaskId, normalizedIds));
        documentResultMapper.deleteByIds(normalizedIds);

        Optional.ofNullable(documentVectorIndexServiceProvider.getIfAvailable())
                .filter(AiDocumentVectorIndexService::isReady)
                .ifPresent(service -> normalizedIds.forEach(service::deleteTaskIndex));

        documentTaskMapper.deleteByIds(normalizedIds);
    }

    private List<Long> findMatchedUserIds(String userKeyword) {
        String normalizedUserKeyword = userKeyword == null ? "" : userKeyword.trim();
        if (!StringUtils.hasText(normalizedUserKeyword)) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .like(SysUser::getNickname, normalizedUserKeyword)
                        .or()
                        .like(SysUser::getUsername, normalizedUserKeyword))
                .stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, SysUser> loadUserMap(List<Long> userIds) {
        List<Long> normalizedIds = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectByIds(normalizedIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private Map<Long, SysAiDocumentResult> loadResultMap(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return documentResultMapper.selectByIds(taskIds).stream()
                .collect(Collectors.toMap(SysAiDocumentResult::getTaskId, Function.identity()));
    }

    private Map<Long, List<SysAiDocumentNodeThread>> loadTaskThreadMap(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return documentNodeThreadMapper.selectList(new LambdaQueryWrapper<SysAiDocumentNodeThread>()
                        .in(SysAiDocumentNodeThread::getTaskId, taskIds))
                .stream()
                .collect(Collectors.groupingBy(SysAiDocumentNodeThread::getTaskId));
    }

    private Map<Long, Long> loadTaskMessageCountMap(Map<Long, List<SysAiDocumentNodeThread>> taskThreadMap) {
        List<Long> threadIds = taskThreadMap.values().stream()
                .flatMap(List::stream)
                .map(SysAiDocumentNodeThread::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Long> threadMessageCountMap = loadThreadMessageCountMap(threadIds);
        Map<Long, Long> taskMessageCountMap = new LinkedHashMap<>();
        taskThreadMap.forEach((taskId, threads) -> {
            long totalCount = threads.stream()
                    .map(SysAiDocumentNodeThread::getId)
                    .filter(Objects::nonNull)
                    .mapToLong(threadId -> threadMessageCountMap.getOrDefault(threadId, 0L))
                    .sum();
            taskMessageCountMap.put(taskId, totalCount);
        });
        return taskMessageCountMap;
    }

    private Map<Long, Long> loadThreadMessageCountMap(List<Long> threadIds) {
        if (threadIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return documentNodeMessageMapper.selectList(new LambdaQueryWrapper<SysAiDocumentNodeMessage>()
                        .select(SysAiDocumentNodeMessage::getThreadId)
                        .in(SysAiDocumentNodeMessage::getThreadId, threadIds))
                .stream()
                .collect(Collectors.groupingBy(SysAiDocumentNodeMessage::getThreadId, Collectors.counting()));
    }

    private Map<String, String> loadNodeTitleMap(Long taskId) {
        SysAiDocumentResult result = documentResultMapper.selectById(taskId);
        if (result == null || !StringUtils.hasText(result.getRootJson())) {
            return Collections.emptyMap();
        }
        try {
            AiDocumentTreeNodeVo root = JsonUtil.convertValue(result.getRootJson(), AiDocumentTreeNodeVo.class);
            if (root == null) {
                return Collections.emptyMap();
            }
            Map<String, String> titleMap = new LinkedHashMap<>();
            collectNodeTitles(root, titleMap);
            return titleMap;
        } catch (Exception ex) {
            log.warn("解析文档结构树失败, taskId={}", taskId, ex);
            return Collections.emptyMap();
        }
    }

    private void collectNodeTitles(AiDocumentTreeNodeVo node, Map<String, String> titleMap) {
        if (node == null || !StringUtils.hasText(node.getId())) {
            return;
        }
        titleMap.put(node.getId(), node.getTitle());
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return;
        }
        for (AiDocumentTreeNodeVo child : node.getChildren()) {
            collectNodeTitles(child, titleMap);
        }
    }

    private AiDocumentTaskAdminVo toTaskAdminVo(SysAiDocumentTask task,
                                                SysUser user,
                                                SysAiDocumentResult result,
                                                List<SysAiDocumentNodeThread> threads,
                                                long messageCount) {
        AiDocumentTaskAdminVo vo = new AiDocumentTaskAdminVo();
        vo.setTaskId(task.getId());
        vo.setUserId(task.getUserId());
        vo.setUsername(user == null ? "-" : user.getUsername());
        vo.setUserNickname(user == null ? "-" : user.getNickname());
        vo.setUserAvatar(user == null ? null : user.getAvatar());
        vo.setTitle(task.getTitle());
        vo.setStatus(task.getStatus());
        vo.setProvider(task.getProvider());
        vo.setRemoteTaskId(task.getRemoteTaskId());
        vo.setFileName(task.getFileName());
        vo.setPageCount(task.getPageCount());
        vo.setParsed(result != null && StringUtils.hasText(result.getRootJson()));
        vo.setRootNodeId(task.getRootNodeId());
        vo.setThreadCount((long) threads.size());
        vo.setMessageCount(messageCount);
        vo.setLastMessageAt(threads.stream()
                .map(SysAiDocumentNodeThread::getLastMessageAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        vo.setExpireAt(task.getExpireAt());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private AiDocumentNodeThreadAdminVo toThreadAdminVo(SysAiDocumentNodeThread thread,
                                                        Map<String, String> nodeTitleMap,
                                                        long messageCount) {
        AiDocumentNodeThreadAdminVo vo = new AiDocumentNodeThreadAdminVo();
        vo.setThreadId(thread.getId());
        vo.setTaskId(thread.getTaskId());
        vo.setNodeId(thread.getNodeId());
        vo.setNodeTitle(nodeTitleMap.getOrDefault(thread.getNodeId(), thread.getNodeId()));
        vo.setTitle(thread.getTitle());
        vo.setSummary(thread.getSummary());
        vo.setModelProvider(thread.getModelProvider());
        vo.setModelName(thread.getModelName());
        vo.setModelId(aiChatModelService.resolveModelId(thread.getModelProvider(), thread.getModelName()));
        vo.setModelDisplayName(aiChatModelService.resolveDisplayName(thread.getModelProvider(), thread.getModelName()));
        vo.setMessageCount(messageCount);
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

    private String textAt(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName) || !node.hasNonNull(fieldName)) {
            return null;
        }
        String value = node.get(fieldName).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private Page<AiDocumentTaskAdminVo> emptyTaskPage(Page<?> sourcePage) {
        Page<AiDocumentTaskAdminVo> result = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), 0);
        result.setRecords(List.of());
        return result;
    }

    private Page<AiDocumentNodeThreadAdminVo> emptyThreadPage(Page<?> sourcePage) {
        Page<AiDocumentNodeThreadAdminVo> result = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), 0);
        result.setRecords(List.of());
        return result;
    }

    private Page<AiDocumentNodeMessageVo> emptyMessagePage(Page<?> sourcePage) {
        Page<AiDocumentNodeMessageVo> result = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), 0);
        result.setRecords(List.of());
        return result;
    }
}

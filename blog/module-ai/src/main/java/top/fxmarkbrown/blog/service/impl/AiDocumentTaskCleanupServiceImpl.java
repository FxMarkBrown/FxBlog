package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.entity.SysAiDocumentNodeMessage;
import top.fxmarkbrown.blog.entity.SysAiDocumentNodeThread;
import top.fxmarkbrown.blog.entity.SysAiDocumentTask;
import top.fxmarkbrown.blog.mapper.SysAiDocumentNodeMessageMapper;
import top.fxmarkbrown.blog.mapper.SysAiDocumentNodeThreadMapper;
import top.fxmarkbrown.blog.mapper.SysAiDocumentResultMapper;
import top.fxmarkbrown.blog.mapper.SysAiDocumentTaskMapper;
import top.fxmarkbrown.blog.service.AiDocumentTaskCleanupService;
import top.fxmarkbrown.blog.service.AiDocumentVectorIndexService;
import top.fxmarkbrown.blog.service.FileDetailService;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentTaskCleanupServiceImpl implements AiDocumentTaskCleanupService {

    private final SysAiDocumentTaskMapper documentTaskMapper;
    private final SysAiDocumentResultMapper documentResultMapper;
    private final SysAiDocumentNodeThreadMapper documentNodeThreadMapper;
    private final SysAiDocumentNodeMessageMapper documentNodeMessageMapper;
    private final FileDetailService fileDetailService;
    private final ObjectProvider<AiDocumentVectorIndexService> documentVectorIndexServiceProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTaskCascade(Long taskId) {
        performDeleteTasksCascade(taskId == null ? List.of() : List.of(taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasksCascade(Collection<Long> taskIds) {
        performDeleteTasksCascade(taskIds);
    }

    private void performDeleteTasksCascade(Collection<Long> taskIds) {
        List<Long> normalizedIds = normalizeTaskIds(taskIds);
        if (normalizedIds.isEmpty()) {
            return;
        }

        List<SysAiDocumentTask> tasks = documentTaskMapper.selectByIds(normalizedIds);
        if (tasks.isEmpty()) {
            return;
        }

        Set<String> deletableSourceFileIds = collectDeletableSourceFileIds(tasks, normalizedIds);
        Set<String> deletableSourceUrls = collectDeletableSourceUrls(tasks, normalizedIds, deletableSourceFileIds);

        deleteTaskThreads(normalizedIds);
        documentResultMapper.deleteByIds(normalizedIds);
        documentTaskMapper.deleteByIds(normalizedIds);

        deleteTaskVectorIndexes(normalizedIds);
        deleteSourceFiles(deletableSourceFileIds, deletableSourceUrls);
    }

    private List<Long> normalizeTaskIds(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return taskIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private void deleteTaskThreads(List<Long> taskIds) {
        List<SysAiDocumentNodeThread> threads = documentNodeThreadMapper.selectList(new LambdaQueryWrapper<SysAiDocumentNodeThread>()
                .in(SysAiDocumentNodeThread::getTaskId, taskIds));
        List<Long> threadIds = threads.stream()
                .map(SysAiDocumentNodeThread::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!threadIds.isEmpty()) {
            documentNodeMessageMapper.delete(new LambdaQueryWrapper<SysAiDocumentNodeMessage>()
                    .in(SysAiDocumentNodeMessage::getThreadId, threadIds));
        }
        documentNodeThreadMapper.delete(new LambdaQueryWrapper<SysAiDocumentNodeThread>()
                .in(SysAiDocumentNodeThread::getTaskId, taskIds));
    }

    private Set<String> collectDeletableSourceFileIds(List<SysAiDocumentTask> tasks, List<Long> deletingTaskIds) {
        Set<String> fileIds = new LinkedHashSet<>();
        for (SysAiDocumentTask task : tasks) {
            String sourceFileId = safeText(task.getSourceFileId());
            if (!StringUtils.hasText(sourceFileId)) {
                continue;
            }
            long remainingReferences = documentTaskMapper.selectCount(new LambdaQueryWrapper<SysAiDocumentTask>()
                    .eq(SysAiDocumentTask::getSourceFileId, sourceFileId)
                    .notIn(SysAiDocumentTask::getId, deletingTaskIds));
            if (remainingReferences == 0) {
                fileIds.add(sourceFileId);
            }
        }
        return fileIds;
    }

    private Set<String> collectDeletableSourceUrls(List<SysAiDocumentTask> tasks, List<Long> deletingTaskIds, Set<String> sourceFileIds) {
        Set<String> sourceUrls = new LinkedHashSet<>();
        for (SysAiDocumentTask task : tasks) {
            if (StringUtils.hasText(safeText(task.getSourceFileId())) && sourceFileIds.contains(safeText(task.getSourceFileId()))) {
                continue;
            }
            String sourceUrl = safeText(task.getSourceUrl());
            if (!StringUtils.hasText(sourceUrl)) {
                continue;
            }
            long remainingReferences = documentTaskMapper.selectCount(new LambdaQueryWrapper<SysAiDocumentTask>()
                    .eq(SysAiDocumentTask::getSourceUrl, sourceUrl)
                    .notIn(SysAiDocumentTask::getId, deletingTaskIds));
            if (remainingReferences == 0) {
                sourceUrls.add(sourceUrl);
            }
        }
        return sourceUrls;
    }

    private void deleteTaskVectorIndexes(List<Long> taskIds) {
        AiDocumentVectorIndexService service = documentVectorIndexServiceProvider.getIfAvailable();
        if (service == null || !service.isReady()) {
            return;
        }
        for (Long taskId : taskIds) {
            try {
                service.deleteTaskIndex(taskId);
            } catch (Exception ex) {
                log.warn("删除文档任务向量索引失败, taskId={}", taskId, ex);
            }
        }
    }

    private void deleteSourceFiles(Set<String> sourceFileIds, Set<String> sourceUrls) {
        for (String sourceFileId : sourceFileIds) {
            try {
                fileDetailService.deleteManagedFileById(sourceFileId);
            } catch (Exception ex) {
                log.warn("删除文档任务源文件失败, fileId={}", sourceFileId, ex);
            }
        }
        for (String sourceUrl : sourceUrls) {
            try {
                fileDetailService.deleteManagedFile(sourceUrl);
            } catch (Exception ex) {
                log.warn("删除文档任务源文件失败, sourceUrl={}", sourceUrl, ex);
            }
        }
    }

    private String safeText(String value) {
        return value == null ? null : value.trim();
    }
}

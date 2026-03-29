package top.fxmarkbrown.blog.service;

import java.util.Collection;

public interface AiDocumentTaskCleanupService {

    /**
     * 级联删除单个文档任务的结果、对话、向量索引和源文件。
     */
    void deleteTaskCascade(Long taskId);

    /**
     * 级联删除多个文档任务的结果、对话、向量索引和源文件。
     */
    void deleteTasksCascade(Collection<Long> taskIds);
}

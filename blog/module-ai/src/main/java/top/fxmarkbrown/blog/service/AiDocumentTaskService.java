package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.dto.ai.AiDocumentNodeAskDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskCreateDto;
import top.fxmarkbrown.blog.dto.ai.AiDocumentTaskRenameDto;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeAnswerVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentParseResultVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskDetailVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskListVo;

import java.util.List;

public interface AiDocumentTaskService {

    /**
     * 查询当前登录用户的文档任务列表。
     */
    List<AiDocumentTaskListVo> listTasks();

    /**
     * 查询指定文档任务的详情信息。
     */
    AiDocumentTaskDetailVo getTaskDetail(Long taskId);

    /**
     * 查询指定文档任务的解析结果。
     */
    AiDocumentParseResultVo getTaskResult(Long taskId);

    /**
     * 创建真实解析链路下的文档任务。
     */
    AiDocumentTaskDetailVo createTask(AiDocumentTaskCreateDto createDto);

    /**
     * 创建仅用于开发调试的本地 Mock 文档任务。
     */
    AiDocumentTaskDetailVo createLocalMockTask(AiDocumentTaskCreateDto createDto);

    /**
     * 重命名指定文档任务。
     */
    AiDocumentTaskDetailVo renameTask(Long taskId, AiDocumentTaskRenameDto renameDto);

    /**
     * 删除指定文档任务及其关联结果。
     */
    void deleteTask(Long taskId);

    /**
     * 清理所有已过期的文档任务及其关联结果。
     */
    int cleanupExpiredTasks();

    /**
     * 处理 MinerU 推送回来的任务完成回调。
     */
    void handleMineruCallback(String checksum, String content);

    /**
     * 在指定文档节点上下文内执行问答。
     */
    AiDocumentNodeAnswerVo askNode(Long taskId, String nodeId, AiDocumentNodeAskDto askDto);
}

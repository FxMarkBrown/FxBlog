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

    List<AiDocumentTaskListVo> listTasks();

    AiDocumentTaskDetailVo getTaskDetail(Long taskId);

    AiDocumentParseResultVo getTaskResult(Long taskId);

    AiDocumentTaskDetailVo createTask(AiDocumentTaskCreateDto createDto);

    AiDocumentTaskDetailVo renameTask(Long taskId, AiDocumentTaskRenameDto renameDto);

    void deleteTask(Long taskId);

    void syncPendingTasks();

    AiDocumentNodeAnswerVo askNode(Long taskId, String nodeId, AiDocumentNodeAskDto askDto);
}

package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeMessageVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentNodeThreadAdminVo;
import top.fxmarkbrown.blog.vo.ai.AiDocumentTaskAdminVo;

import java.util.List;

public interface SysAiDocumentTaskService {

    /**
     * 分页查询后台文档任务列表。
     */
    IPage<AiDocumentTaskAdminVo> pageTasks(String status, String provider, String keyword, String userKeyword);

    /**
     * 分页查询指定文档任务下的节点线程列表。
     */
    IPage<AiDocumentNodeThreadAdminVo> pageThreads(Long taskId, String keyword);

    /**
     * 分页查询指定节点线程下的消息记录。
     */
    IPage<AiDocumentNodeMessageVo> pageMessages(Long threadId);

    /**
     * 批量删除文档任务及其关联结果、线程、消息、向量索引和源文件。
     */
    void deleteTasks(List<Long> ids);
}

package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.vo.ai.AiConversationAdminVo;
import top.fxmarkbrown.blog.vo.ai.AiMessageVo;

import java.util.List;

public interface SysAiConversationService {

    /**
     * 分页查询后台 AI 会话列表。
     */
    IPage<AiConversationAdminVo> pageConversations(String type, String keyword, String userKeyword);

    /**
     * 分页查询后台指定会话的消息记录。
     */
    IPage<AiMessageVo> pageMessages(Long conversationId);

    /**
     * 批量删除后台选中的会话及其消息。
     */
    void deleteConversations(List<Long> ids);
}

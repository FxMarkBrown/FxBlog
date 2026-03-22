package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.entity.SysAiConversation;
import top.fxmarkbrown.blog.model.ai.AiToolBundle;
import top.fxmarkbrown.blog.vo.ai.AiRetrievedChunkVo;

import java.util.List;

public interface AiArticleToolService {

    /**
     * 为当前会话构建文章域工具、工具上下文与调用记录容器。
     */
    AiToolBundle buildToolBundle(SysAiConversation conversation, List<AiRetrievedChunkVo> citations);
}

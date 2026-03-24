package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.vo.ai.AiRagRebuildSubmitVo;

public interface AiArticleRagRebuildService {

    AiRagRebuildSubmitVo submitAsync(boolean publishedOnly, String trigger);

    boolean isRunning();
}

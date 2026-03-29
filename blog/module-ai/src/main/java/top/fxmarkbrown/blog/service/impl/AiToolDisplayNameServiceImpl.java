package top.fxmarkbrown.blog.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.service.AiToolDisplayNameService;

import java.util.Map;

@Service
public class AiToolDisplayNameServiceImpl implements AiToolDisplayNameService {

    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.of(
            "getCurrentArticleContext", "读取当前文章上下文",
            "searchRelevantArticles", "检索相关文章",
            "getMyLikedArticles", "读取我的点赞文章",
            "getMyFavoriteArticles", "读取我的收藏文章",
            "getMyCommentedArticles", "读取我的评论文章",
            "getMyAiUsageSnapshot", "读取我的 AI 使用情况"
    );

    @Override
    public String resolveToolDisplayName(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return "未命名工具";
        }
        return TOOL_DISPLAY_NAMES.getOrDefault(toolName, toolName.trim());
    }
}

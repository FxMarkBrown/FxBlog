package top.fxmarkbrown.blog.service;

import java.util.List;

/**
 * IndexNow 即时收录提交服务。
 */
public interface IndexNowService {

    /**
     * 提交文章 URL（新增 / 更新 / 删除均可调用，删除的 URL 会促使搜索引擎下架）
     * @param articleIds 文章 ID 列表
     */
    void submitArticles(List<Long> articleIds);
}

package top.fxmarkbrown.blog.event;

import java.util.List;

/**
 * 文章需要提交 IndexNow 的事件（新增 / 更新 / 删除后触发）。
 * 删除的文章同样提交：搜索引擎抓取到 404 会加速下线索引。
 */
public record IndexNowArticleEvent(List<Long> articleIds) {
}

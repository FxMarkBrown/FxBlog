package top.fxmarkbrown.blog.event.ai;

import java.util.List;

public record AiArticleIndexRemoveEvent(List<Long> articleIds) {
}

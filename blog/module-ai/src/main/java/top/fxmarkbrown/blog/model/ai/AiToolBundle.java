package top.fxmarkbrown.blog.model.ai;

import org.springframework.ai.tool.ToolCallback;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.util.List;
import java.util.Map;

public record AiToolBundle(
        List<ToolCallback> toolCallbacks,
        Map<String, Object> toolContext,
        List<AiToolCallVo> toolCalls
) {
}

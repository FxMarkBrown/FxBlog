package top.fxmarkbrown.blog.model.ai;

import org.springframework.ai.chat.client.ChatClient;
import top.fxmarkbrown.blog.vo.ai.AiToolCallVo;

import java.util.List;

public record AiChatInvocation(
        ChatClient.ChatClientRequestSpec requestSpec,
        List<AiToolCallVo> toolCalls
) {
}

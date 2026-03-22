package top.fxmarkbrown.blog.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 会话创建请求")
public class AiConversationCreateDto {

    @Schema(description = "模型标识")
    private String modelId;
}

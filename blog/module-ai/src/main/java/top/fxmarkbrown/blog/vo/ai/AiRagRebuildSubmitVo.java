package top.fxmarkbrown.blog.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "RAG 重建提交结果")
public class AiRagRebuildSubmitVo {

    @Schema(description = "是否已提交")
    private boolean submitted;

    @Schema(description = "当前是否有重建任务在运行")
    private boolean running;

    @Schema(description = "本次涉及文章数量")
    private int articleCount;

    @Schema(description = "是否仅重建已发布文章")
    private boolean publishedOnly;

    @Schema(description = "触发来源")
    private String trigger;
}

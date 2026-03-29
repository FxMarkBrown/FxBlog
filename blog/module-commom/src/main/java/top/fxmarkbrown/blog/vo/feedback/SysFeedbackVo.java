package top.fxmarkbrown.blog.vo.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.fxmarkbrown.blog.entity.SysFeedback;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "反馈对象vo")
public class SysFeedbackVo extends SysFeedback {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;
}

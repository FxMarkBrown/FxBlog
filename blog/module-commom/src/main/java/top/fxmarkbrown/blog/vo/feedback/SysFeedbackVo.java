package top.fxmarkbrown.blog.vo.feedback;

import top.fxmarkbrown.blog.entity.SysFeedback;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "反馈对象vo")
public class SysFeedbackVo extends SysFeedback {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;
}

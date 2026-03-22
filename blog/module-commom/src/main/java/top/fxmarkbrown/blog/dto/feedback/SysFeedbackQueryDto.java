package top.fxmarkbrown.blog.dto.feedback;

import top.fxmarkbrown.blog.entity.SysFeedback;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SysFeedbackQueryDto extends SysFeedback {

    private String source;
}

package top.fxmarkbrown.blog.dto.feedback;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.fxmarkbrown.blog.entity.SysFeedback;

@Data
@EqualsAndHashCode(callSuper = true)
public class SysFeedbackQueryDto extends SysFeedback {

    private String source;
}

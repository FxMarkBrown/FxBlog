package top.fxmarkbrown.blog.vo.moment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.fxmarkbrown.blog.entity.SysMoment;

@Data
@EqualsAndHashCode(callSuper = true)
public class MomentPageVo extends SysMoment {

    private String nickname;

    private String avatar;

}

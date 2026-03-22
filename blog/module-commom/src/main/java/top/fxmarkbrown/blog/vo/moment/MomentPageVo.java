package top.fxmarkbrown.blog.vo.moment;

import top.fxmarkbrown.blog.entity.SysMoment;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MomentPageVo extends SysMoment {

    private String nickname;

    private String avatar;

}

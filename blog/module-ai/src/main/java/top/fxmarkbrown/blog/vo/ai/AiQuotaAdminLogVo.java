package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiQuotaAdminLogVo extends AiQuotaLogVo {

    private Long userId;

    private String username;

    private String userNickname;

    private String userAvatar;
}

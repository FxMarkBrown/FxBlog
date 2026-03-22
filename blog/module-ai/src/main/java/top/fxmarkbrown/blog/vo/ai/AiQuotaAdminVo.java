package top.fxmarkbrown.blog.vo.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiQuotaAdminVo extends AiQuotaSnapshotVo {

    private String username;

    private String userNickname;

    private String userAvatar;
}

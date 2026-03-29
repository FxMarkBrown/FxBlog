package top.fxmarkbrown.blog.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.fxmarkbrown.blog.entity.SysUser;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "个人信息")
public class SysUserProfileVo {

    @Schema(description = "用户信息")
    private SysUser sysUser;

    @Schema(description = "角色")
    private List<String> roles;

    @Schema(description = "文章数")
    private Long articleCount;

    @Schema(description = "评论数")
    private Long commentCount;

    @Schema(description = "获赞数")
    private Long receivedLikeCount;
}

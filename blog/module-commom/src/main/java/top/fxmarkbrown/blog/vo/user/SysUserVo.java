package top.fxmarkbrown.blog.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.fxmarkbrown.blog.entity.SysUser;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页视图对象")
public class SysUserVo extends SysUser {

    @Schema(description = "角色id集合")
    private List<Integer> roleIds;
}

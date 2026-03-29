package top.fxmarkbrown.blog.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.fxmarkbrown.blog.entity.SysUser;

@Data
@EqualsAndHashCode(callSuper = true)
public class OnlineUserVo extends SysUser {

    @Schema(description = "token")
    private String tokenValue;

}

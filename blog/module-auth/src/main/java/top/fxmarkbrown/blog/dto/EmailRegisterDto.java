package top.fxmarkbrown.blog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmailRegisterDto {

    @Schema(description = "邮箱")
    @NotNull(message = "邮箱不能为空")
    private String email;

    @Schema(description = "验证码")
    @NotNull(message = "验证码不能为空")
    private String code;

    @Schema(description = "密码")
    @NotNull(message = "密码不能为空")
    private String password;

    @Schema(description = "昵称")
    @NotNull(message = "昵称不能为空")
    private String nickname;
}

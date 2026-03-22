package top.fxmarkbrown.blog.config.properties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    @Schema(description = "公众号appId")
    private String appId;

    @Schema(description = "公众号appSecret")
    private String secret;

    @Schema(description = "消息校验Token")
    private String token;

    @Schema(description = "消息加解密密钥")
    private String aesKey;

    @Schema(description = "微信小程序appId")
    private String appletAppId;

    @Schema(description = "微信小程序appSecret")
    private String appletSecret;
}

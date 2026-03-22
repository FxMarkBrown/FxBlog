package top.fxmarkbrown.blog.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "weibo")
public class WeiboConfigProperties {

    /**
     * 微博appId
     */
    private String appId;

    /**
     * 微博appSecret
     */
    private String appSecret;

    /**
     * 微博回调域名
     */
    private String redirectUrl;

}


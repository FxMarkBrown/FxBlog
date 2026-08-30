package top.fxmarkbrown.blog.config.indexnow;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IndexNow（Bing 等搜索引擎的即时收录协议）配置。
 * key 会与前端站点根路径下 {key}.txt 文件内容一致，供搜索引擎校验站点所有权。
 */
@Data
@Component
@ConfigurationProperties(prefix = "blog.indexnow")
public class IndexNowProperties {

    /** 是否启用文章变动自动提交 */
    private boolean enabled = false;

    /** IndexNow 验证 key（需与前端 public/{key}.txt 一致） */
    private String key = "";

    /** 前台站点地址（用于拼接文章 URL 与 keyLocation），如 https://blog.example.com */
    private String siteUrl = "";

    /** IndexNow 提交端点 */
    private String apiUrl = "https://api.indexnow.org/indexnow";
}

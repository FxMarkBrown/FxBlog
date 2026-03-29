package top.fxmarkbrown.blog.config;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.WxMpConfigStorage;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.fxmarkbrown.blog.config.properties.WechatProperties;

@Configuration
public class WechatConfig {

    private final WechatProperties wechatMpProperties;

    public WechatConfig(WechatProperties wechatMpProperties) {
        this.wechatMpProperties = wechatMpProperties;
    }

    @Bean
    public WxMpConfigStorage wxMpConfigStorage() {
        WxMpDefaultConfigImpl configStorage = new WxMpDefaultConfigImpl();
        configStorage.setAppId(wechatMpProperties.getAppId());
        configStorage.setSecret(wechatMpProperties.getSecret());
        configStorage.setToken(wechatMpProperties.getToken());
        configStorage.setAesKey(wechatMpProperties.getAesKey());
        return configStorage;
    }

    @Bean
    public WxMpService wxMpService(WxMpConfigStorage wxMpConfigStorage) {
        WxMpService wxMpService = new WxMpServiceImpl();
        wxMpService.setWxMpConfigStorage(wxMpConfigStorage);
        return wxMpService;
    }
}

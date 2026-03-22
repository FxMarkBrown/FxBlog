package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysNotice;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.vo.home.WeatherEffectVo;

import java.util.List;
import java.util.Map;

public interface HomeService {

    /**
     * 获取网站配置
     */
    Result<SysWebConfig> getWebConfig();

    /**
     * 获取天气氛围
     */
    WeatherEffectVo getWeatherEffect();

    /**
     * 添加访问量
     */
    void report();

    /**
     * 获取公告
     */
    Map<String, List<SysNotice>> getNotice();
}

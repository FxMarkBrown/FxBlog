package top.fxmarkbrown.blog.vo.home;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "天气氛围响应")
public class WeatherEffectVo {

    @Schema(description = "是否启用天气氛围")
    private Boolean enabled;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "标准化天气类型")
    private String weather;

    @Schema(description = "是否夜间")
    private Boolean isNight;

    @Schema(description = "温度")
    private Integer temperature;

    @Schema(description = "风力等级")
    private Integer windLevel;

    @Schema(description = "湿度")
    private Integer humidity;

    @Schema(description = "季节")
    private String season;

    @Schema(description = "空气质量")
    private String airQuality;

    @Schema(description = "特效强度")
    private String intensity;
}

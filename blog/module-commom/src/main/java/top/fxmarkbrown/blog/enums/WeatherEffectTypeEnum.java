package top.fxmarkbrown.blog.enums;

import lombok.Getter;

@Getter
public enum WeatherEffectTypeEnum {

    SUNNY("sunny", "晴天"),
    CLOUDY("cloudy", "多云"),
    OVERCAST("overcast", "阴天"),
    LIGHT_RAIN("light_rain", "小雨"),
    HEAVY_RAIN("heavy_rain", "大雨"),
    THUNDERSTORM("thunderstorm", "雷暴"),
    SNOW("snow", "下雪"),
    FOG("fog", "大雾"),
    WINDY("windy", "大风"),
    DUST("dust", "沙尘"),
    UNKNOWN("unknown", "未知");

    private final String type;
    private final String desc;

    WeatherEffectTypeEnum(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public static String safeType(String type) {
        if (type == null || type.isBlank()) {
            return UNKNOWN.type;
        }
        for (WeatherEffectTypeEnum item : values()) {
            if (item.type.equalsIgnoreCase(type)) {
                return item.type;
            }
        }
        return UNKNOWN.type;
    }
}

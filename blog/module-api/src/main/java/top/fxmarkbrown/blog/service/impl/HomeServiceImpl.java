package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.common.Result;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysNotice;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.enums.WeatherEffectTypeEnum;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysNoticeMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.HomeService;
import top.fxmarkbrown.blog.service.WeatherEffectCacheService;
import top.fxmarkbrown.blog.service.WebConfigCacheService;
import top.fxmarkbrown.blog.utils.HttpUtil;
import top.fxmarkbrown.blog.utils.IpUtil;
import top.fxmarkbrown.blog.utils.JsonUtil;
import top.fxmarkbrown.blog.utils.RedisUtil;
import top.fxmarkbrown.blog.vo.home.WeatherEffectVo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final WebConfigCacheService webConfigCacheService;
    private final WeatherEffectCacheService weatherEffectCacheService;
    private final RedisUtil redisUtil;
    private final SysNoticeMapper noticeMapper;
    private final SysUserMapper sysUserMapper;
    private final SysArticleMapper sysArticleMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_HOME_CONFIG, key = "'full'", sync = true)
    public Result<SysWebConfig> getWebConfig() {
        SysWebConfig sysWebConfig = getCurrentWebConfig();

        SysUser adminUser = sysUserMapper.selectPublicProfileByUsername(Constants.ADMIN);
        long articleCount = sysArticleMapper.selectCount(new LambdaQueryWrapper<SysArticle>()
                .eq(SysArticle::getStatus, Constants.YES));
        long likeCount = adminUser != null ? sysArticleMapper.selectReceivedLikeCount(adminUser.getId()) : 0L;

        long blogViewsCount = 0;
        long visitorCount = 0;
        if (redisUtil.hasKey(RedisConstants.BLOG_VIEWS_COUNT)) {
            blogViewsCount = Long.parseLong(redisUtil.get(RedisConstants.BLOG_VIEWS_COUNT).toString());
        }
        if (redisUtil.hasKey(RedisConstants.UNIQUE_VISITOR_COUNT)) {
            visitorCount = Long.parseLong(redisUtil.get(RedisConstants.UNIQUE_VISITOR_COUNT).toString());
        }

        return Result.success(sysWebConfig)
                .putExtra("blogViewsCount", blogViewsCount)
                .putExtra("visitorCount", visitorCount)
                .putExtra("articleCount", articleCount)
                .putExtra("likeCount", likeCount);
    }

    @Override
    public WeatherEffectVo getWeatherEffect() {
        SysWebConfig sysWebConfig = getCurrentWebConfig();
        if (!isWeatherEnabled(sysWebConfig)) {
            WeatherEffectVo disabledEffect = buildSeasonalEffect(sysWebConfig);
            disabledEffect.setEnabled(Boolean.FALSE);
            return disabledEffect;
        }
        if ("manual".equalsIgnoreCase(sysWebConfig.getWeatherMode())) {
            return buildManualEffect(sysWebConfig);
        }

        return weatherEffectCacheService.getOrLoad("current", resolveRefreshMinutes(sysWebConfig), () -> {
            WeatherLocation location = resolveLocation(sysWebConfig);
            return requestWeatherEffect(sysWebConfig, location);
        });
    }

    @Override
    public void report() {
        String ipAddress = IpUtil.getIp();
        UserAgent userAgent = IpUtil.getUserAgent(Objects.requireNonNull(IpUtil.getRequest()));
        Browser browser = userAgent.getBrowser();
        OperatingSystem operatingSystem = userAgent.getOperatingSystem();
        String uuid = ipAddress + browser.getName() + operatingSystem.getName();
        String md5 = DigestUtils.md5DigestAsHex(uuid.getBytes());
        if (!redisUtil.sIsMember(RedisConstants.UNIQUE_VISITOR, md5)) {
            redisUtil.increment(RedisConstants.UNIQUE_VISITOR_COUNT, 1);
            redisUtil.sAdd(RedisConstants.UNIQUE_VISITOR, md5);
        }
        redisUtil.increment(RedisConstants.BLOG_VIEWS_COUNT, 1);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_NOTICE, key = "'grouped'", sync = true)
    public Map<String, List<SysNotice>> getNotice() {
        List<SysNotice> sysNotices = noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getIsShow, Constants.YES));
        return sysNotices.stream().collect(Collectors.groupingBy(SysNotice::getPosition));
    }

    private SysWebConfig getCurrentWebConfig() {
        return normalizeWebConfig(webConfigCacheService.getCurrentWebConfig());
    }

    private boolean isWeatherEnabled(SysWebConfig sysWebConfig) {
        return sysWebConfig != null && Boolean.TRUE.equals(sysWebConfig.getWeatherEnabled());
    }

    private WeatherEffectVo buildManualEffect(SysWebConfig sysWebConfig) {
        WeatherEffectVo weatherEffect = buildBaseEffect(sysWebConfig);
        String manualType = WeatherEffectTypeEnum.safeType(sysWebConfig.getWeatherManualType());
        if (WeatherEffectTypeEnum.UNKNOWN.getType().equals(manualType)) {
            manualType = buildSeasonalWeather(weatherEffect.getSeason(), Boolean.TRUE.equals(weatherEffect.getIsNight()));
        }
        weatherEffect.setWeather(manualType);
        weatherEffect.setAirQuality(inferAirQuality(manualType));
        return weatherEffect;
    }

    private WeatherEffectVo requestWeatherEffect(SysWebConfig sysWebConfig, WeatherLocation location) {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + location.latitude()
                + "&longitude=" + location.longitude()
                + "&current=temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m"
                + "&wind_speed_unit=ms&timezone=Asia%2FShanghai&forecast_days=1";
        String response = HttpUtil.get(url);
        JsonNode responseNode = JsonUtil.readTree(response);
        JsonNode current = responseNode == null ? null : responseNode.get("current");
        if (current == null || current.isMissingNode() || current.isEmpty()) {
            throw new IllegalStateException("weather current data missing");
        }

        double windSpeed = getDoubleValue(current, "wind_speed_10m", 0D);
        double precipitation = getDoubleValue(current, "precipitation", 0D);
        Integer weatherCode = current.hasNonNull("weather_code") ? current.get("weather_code").intValue() : null;

        WeatherEffectVo weatherEffect = buildBaseEffect(sysWebConfig);
        weatherEffect.setCity(location.city());
        weatherEffect.setIsNight(getIntValue(current, "is_day", 1) == 0);
        weatherEffect.setTemperature(current.hasNonNull("temperature_2m") ? roundToInt(current.get("temperature_2m").doubleValue()) : null);
        weatherEffect.setHumidity(current.hasNonNull("relative_humidity_2m") ? current.get("relative_humidity_2m").intValue() : null);
        weatherEffect.setWindLevel(toWindLevel(windSpeed));
        String weatherType = normalizeWeatherType(weatherCode, precipitation, windSpeed);
        weatherEffect.setWeather(weatherType);
        weatherEffect.setAirQuality(inferAirQuality(weatherType));
        return weatherEffect;
    }

    private WeatherLocation resolveLocation(SysWebConfig sysWebConfig) {
        Double longitude = parseCoordinate(sysWebConfig.getWeatherLng());
        Double latitude = parseCoordinate(sysWebConfig.getWeatherLat());
        String city = resolveWeatherCity(sysWebConfig);
        if (longitude != null && latitude != null) {
            return new WeatherLocation(city, latitude, longitude);
        }

        String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&count=1&language=zh&format=json";
        String response = HttpUtil.get(url);
        JsonNode jsonObject = JsonUtil.readTree(response);
        JsonNode results = jsonObject == null ? null : jsonObject.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            throw new IllegalStateException("weather geocoding result missing");
        }
        JsonNode first = results.get(0);
        Double resolvedLatitude = first != null && first.hasNonNull("latitude") ? first.get("latitude").doubleValue() : null;
        Double resolvedLongitude = first != null && first.hasNonNull("longitude") ? first.get("longitude").doubleValue() : null;
        if (resolvedLatitude == null || resolvedLongitude == null) {
            throw new IllegalStateException("weather geocoding coordinates missing");
        }
        String resolvedCity = first != null && first.hasNonNull("name") ? first.get("name").asText() : city;
        return new WeatherLocation(resolvedCity, resolvedLatitude, resolvedLongitude);
    }

    private WeatherEffectVo buildSeasonalEffect(SysWebConfig sysWebConfig) {
        WeatherEffectVo weatherEffect = buildBaseEffect(sysWebConfig);
        weatherEffect.setWeather(buildSeasonalWeather(weatherEffect.getSeason(), Boolean.TRUE.equals(weatherEffect.getIsNight())));
        weatherEffect.setAirQuality("unknown");
        weatherEffect.setWindLevel(0);
        return weatherEffect;
    }

    private WeatherEffectVo buildBaseEffect(SysWebConfig sysWebConfig) {
        LocalDate today = LocalDate.now(Constants.WEATHER_ZONE);
        LocalTime now = LocalTime.now(Constants.WEATHER_ZONE);
        WeatherEffectVo weatherEffect = new WeatherEffectVo();
        weatherEffect.setEnabled(isWeatherEnabled(sysWebConfig));
        weatherEffect.setCity(resolveWeatherCity(sysWebConfig));
        weatherEffect.setWeather(WeatherEffectTypeEnum.UNKNOWN.getType());
        weatherEffect.setIsNight(now.isAfter(LocalTime.of(18, 0)) || now.isBefore(LocalTime.of(6, 0)));
        weatherEffect.setTemperature(null);
        weatherEffect.setWindLevel(0);
        weatherEffect.setHumidity(null);
        weatherEffect.setSeason(resolveSeason(today.getMonth()));
        weatherEffect.setAirQuality("unknown");
        weatherEffect.setIntensity(resolveIntensity(sysWebConfig));
        return weatherEffect;
    }

    private SysWebConfig buildDefaultWebConfig() {
        SysWebConfig sysWebConfig = new SysWebConfig();
        sysWebConfig.setWeatherEnabled(Boolean.TRUE);
        sysWebConfig.setWeatherCity(Constants.DEFAULT_WEATHER_CITY);
        sysWebConfig.setWeatherMode("auto");
        sysWebConfig.setWeatherIntensity("normal");
        sysWebConfig.setWeatherRefreshMinutes(Constants.DEFAULT_WEATHER_REFRESH_MINUTES);
        return sysWebConfig;
    }

    private SysWebConfig normalizeWebConfig(SysWebConfig sysWebConfig) {
        if (sysWebConfig == null) {
            return buildDefaultWebConfig();
        }
        if (sysWebConfig.getWeatherEnabled() == null) {
            sysWebConfig.setWeatherEnabled(Boolean.TRUE);
        }
        if (sysWebConfig.getWeatherCity() == null || sysWebConfig.getWeatherCity().isBlank()) {
            sysWebConfig.setWeatherCity(Constants.DEFAULT_WEATHER_CITY);
        }
        if (sysWebConfig.getWeatherMode() == null || sysWebConfig.getWeatherMode().isBlank()) {
            sysWebConfig.setWeatherMode("auto");
        }
        if (sysWebConfig.getWeatherIntensity() == null || sysWebConfig.getWeatherIntensity().isBlank()) {
            sysWebConfig.setWeatherIntensity("normal");
        }
        if (sysWebConfig.getWeatherRefreshMinutes() == null || sysWebConfig.getWeatherRefreshMinutes() < 10) {
            sysWebConfig.setWeatherRefreshMinutes(Constants.DEFAULT_WEATHER_REFRESH_MINUTES);
        }
        return sysWebConfig;
    }

    private String resolveWeatherCity(SysWebConfig sysWebConfig) {
        if (sysWebConfig == null || sysWebConfig.getWeatherCity() == null || sysWebConfig.getWeatherCity().isBlank()) {
            return Constants.DEFAULT_WEATHER_CITY;
        }
        return sysWebConfig.getWeatherCity().trim();
    }

    private String resolveIntensity(SysWebConfig sysWebConfig) {
        String intensity = sysWebConfig == null ? null : sysWebConfig.getWeatherIntensity();
        if ("light".equalsIgnoreCase(intensity) || "rich".equalsIgnoreCase(intensity)) {
            return intensity.toLowerCase();
        }
        return "normal";
    }

    private int resolveRefreshMinutes(SysWebConfig sysWebConfig) {
        Integer refreshMinutes = sysWebConfig == null ? null : sysWebConfig.getWeatherRefreshMinutes();
        if (refreshMinutes == null || refreshMinutes < 10) {
            return Constants.DEFAULT_WEATHER_REFRESH_MINUTES;
        }
        return refreshMinutes;
    }

    private String normalizeWeatherType(Integer weatherCode, double precipitation, double windSpeed) {
        if (weatherCode != null) {
            if (Constants.THUNDER_CODES.contains(weatherCode)) {
                return WeatherEffectTypeEnum.THUNDERSTORM.getType();
            }
            if (Constants.SNOW_CODES.contains(weatherCode)) {
                return WeatherEffectTypeEnum.SNOW.getType();
            }
            if (Constants.FOG_CODES.contains(weatherCode)) {
                return WeatherEffectTypeEnum.FOG.getType();
            }
            if (Constants.LIGHT_RAIN_CODES.contains(weatherCode)) {
                return WeatherEffectTypeEnum.LIGHT_RAIN.getType();
            }
            if (Constants.HEAVY_RAIN_CODES.contains(weatherCode)) {
                return WeatherEffectTypeEnum.HEAVY_RAIN.getType();
            }
            if (weatherCode == 0) {
                return WeatherEffectTypeEnum.SUNNY.getType();
            }
            if (weatherCode == 1 || weatherCode == 2) {
                return WeatherEffectTypeEnum.CLOUDY.getType();
            }
            if (weatherCode == 3) {
                return WeatherEffectTypeEnum.OVERCAST.getType();
            }
        }
        if (windSpeed >= 10.8D) {
            return WeatherEffectTypeEnum.WINDY.getType();
        }
        if (precipitation >= 8D) {
            return WeatherEffectTypeEnum.HEAVY_RAIN.getType();
        }
        if (precipitation > 0D) {
            return WeatherEffectTypeEnum.LIGHT_RAIN.getType();
        }
        return WeatherEffectTypeEnum.UNKNOWN.getType();
    }

    private String inferAirQuality(String weatherType) {
        return switch (WeatherEffectTypeEnum.safeType(weatherType)) {
            case "dust" -> "poor";
            case "light_rain", "heavy_rain", "snow" -> "good";
            case "fog" -> "normal";
            default -> "unknown";
        };
    }

    private String buildSeasonalWeather(String season, boolean isNight) {
        if ("summer".equals(season) && !isNight) {
            return WeatherEffectTypeEnum.SUNNY.getType();
        }
        if ("winter".equals(season)) {
            return WeatherEffectTypeEnum.OVERCAST.getType();
        }
        return WeatherEffectTypeEnum.CLOUDY.getType();
    }

    private String resolveSeason(Month month) {
        return switch (month) {
            case MARCH, APRIL, MAY -> "spring";
            case JUNE, JULY, AUGUST -> "summer";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "autumn";
            default -> "winter";
        };
    }

    private Integer toWindLevel(double windSpeed) {
        double[] thresholds = {0.3D, 1.6D, 3.4D, 5.5D, 8.0D, 10.8D, 13.9D, 17.2D, 20.8D, 24.5D, 28.5D, 32.7D};
        int level = 0;
        while (level < thresholds.length && windSpeed >= thresholds[level]) {
            level++;
        }
        return level;
    }

    private Integer roundToInt(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private double getDoubleValue(JsonNode jsonObject, String key, double defaultValue) {
        if (jsonObject == null || !jsonObject.hasNonNull(key)) {
            return defaultValue;
        }
        return jsonObject.get(key).doubleValue();
    }

    private int getIntValue(JsonNode jsonObject, String key, int defaultValue) {
        if (jsonObject == null || !jsonObject.hasNonNull(key)) {
            return defaultValue;
        }
        return jsonObject.get(key).intValue();
    }

    private Double parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record WeatherLocation(String city, double latitude, double longitude) {
    }
}

package top.fxmarkbrown.blog.common;

import java.util.List;

/**
 * 业务缓存名称
 */
public final class CacheNames {

    public static final String REDIS_PREFIX = "cache:";

    public static final String SYS_CONFIG = "sys_config";

    public static final String SYS_DICT = "sys_dict";

    public static final String WEB_CONFIG = "web_config";

    public static final String WEATHER_EFFECT = "weather_effect";

    public static final String PUBLIC_NOTICE = "public_notice";

    public static final String PUBLIC_TAG = "public_tag";

    public static final String PUBLIC_FRIEND = "public_friend";

    public static final String PUBLIC_MESSAGE = "public_message";

    public static final String PUBLIC_ARTICLE_ARCHIVE = "public_article_archive";

    public static final String PUBLIC_ARTICLE_CATEGORIES = "public_article_categories";

    public static final String PUBLIC_ARTICLE_CAROUSEL = "public_article_carousel";

    public static final String PUBLIC_ARTICLE_RECOMMEND = "public_article_recommend";

    public static final List<String> ALL = List.of(
            SYS_CONFIG,
            SYS_DICT,
            WEB_CONFIG,
            WEATHER_EFFECT,
            PUBLIC_NOTICE,
            PUBLIC_TAG,
            PUBLIC_FRIEND,
            PUBLIC_MESSAGE,
            PUBLIC_ARTICLE_ARCHIVE,
            PUBLIC_ARTICLE_CATEGORIES,
            PUBLIC_ARTICLE_CAROUSEL,
            PUBLIC_ARTICLE_RECOMMEND
    );

    private CacheNames() {
    }

    public static String redisPrefix(String cacheName) {
        return REDIS_PREFIX + cacheName + "::";
    }

    public static String redisPattern(String cacheName) {
        return redisPrefix(cacheName) + "*";
    }
}

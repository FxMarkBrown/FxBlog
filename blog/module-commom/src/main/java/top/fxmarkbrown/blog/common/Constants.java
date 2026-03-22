package top.fxmarkbrown.blog.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

public class Constants {
    public static final String UTF8 = "UTF-8";

    public static final String ADMIN = "admin";

    public static final String UNKNOWN = "未知";

    public static final int YES = 1;

    public static final int NO = 0;

    public static final String CURRENT_USER = "current_user";

    public static final String PARENT_VIEW = "ParentView";

    public static final Object USER = "user";

    public static final String DEFAULT_WEATHER_CITY = "北京";

    public static final int DEFAULT_WEATHER_REFRESH_MINUTES = 30;

    public static final ZoneId WEATHER_ZONE = ZoneId.of("Asia/Shanghai");

    public static final LocalDate USER_SIGN_START_DATE = LocalDate.of(2025, 2, 8);

    public static final Set<Integer> LIGHT_RAIN_CODES = Set.of(51, 53, 56, 61, 80);

    public static final Set<Integer> HEAVY_RAIN_CODES = Set.of(55, 57, 63, 65, 66, 67, 81, 82);

    public static final Set<Integer> SNOW_CODES = Set.of(71, 73, 75, 77, 85, 86);

    public static final Set<Integer> FOG_CODES = Set.of(45, 48);

    public static final Set<Integer> THUNDER_CODES = Set.of(95, 96, 99);

    public static final String AI_CONVERSATION_TYPE_GLOBAL = "global";

    public static final String AI_CONVERSATION_TYPE_ARTICLE = "article";

    public static final String AI_MESSAGE_ROLE_SYSTEM = "system";

    public static final String AI_MESSAGE_ROLE_USER = "user";

    public static final String AI_MESSAGE_ROLE_ASSISTANT = "assistant";

    public static final String AI_MESSAGE_ROLE_TOOL = "tool";
}

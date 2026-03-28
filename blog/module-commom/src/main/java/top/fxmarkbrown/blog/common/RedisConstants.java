package top.fxmarkbrown.blog.common;

/**
 * Redis常量
 */
public class RedisConstants {

    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN = "login:token:";

    /**
     * 用户签到
     */
    public static final String USER_SIGN = "user_sign:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_code:";

    /**
     * 滑块验证码 redis key
     */
    public static final String SLIDER_CAPTCHA_CODE_KEY = "slider_captcha_code:";

    /**
     * 访客
     */
    public static final String UNIQUE_VISITOR = "unique_visitor";

    /**
     * 访客量
     */
    public static final String UNIQUE_VISITOR_COUNT = "unique_visitor_count";

    /**
     * 博客浏览量
     */
    public static final String BLOG_VIEWS_COUNT = "blog_views_count";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 过期时间 1分钟
     */
    public static final long MINUTE_EXPIRE = 60;

    /**
     * 微信登录用户状态
     */
    public static final String WX_LOGIN_USER_CODE = "wx_login_user_statue:";

    /**
     * 微信登录用户信息
     */
    public static final String WX_LOGIN_USER = "wx_login_user:";

    /**
     * 文章阅读量
     */
    public static final String ARTICLE_QUANTITY = "article_quantity";

    /**
     * 文章阅读去重 IP 集合前缀
     */
    public static final String ARTICLE_VIEW_IP_SET = "article:viewed:";

    /**
     * AI 点赞每日统计
     */
    public static final String AI_LIKE_DAILY_COUNT = "ai:like:daily:";

    /**
     * AI 点赞单篇文章每日统计
     */
    public static final String AI_LIKE_ARTICLE_DAILY_COUNT = "ai:like:article:daily:";
}

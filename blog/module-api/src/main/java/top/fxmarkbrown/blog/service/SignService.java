package top.fxmarkbrown.blog.service;

public interface SignService {
    /**
     * 签到
     */
    Boolean sign();

    /**
     * 是否签到
     */
    Boolean isSignedToday();

    /**
     * 获取累计签到天数
     */
    Long getCumulativeSignDays();

    /**
     * 获取连续签到天数
     */
    int getConsecutiveSignDays();
}

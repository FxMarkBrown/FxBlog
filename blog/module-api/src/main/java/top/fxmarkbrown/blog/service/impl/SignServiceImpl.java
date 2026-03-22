package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.service.SignService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class SignServiceImpl implements SignService {

    private final RedisUtil redisUtil;
    private final AiQuotaCoreService aiQuotaCoreService;

    @Override
    public Boolean sign(){
        Long userId = StpUtil.getLoginIdAsLong();
        String signKey = RedisConstants.USER_SIGN + userId;
        long offset = getOffset();
        if (redisUtil.getBit(signKey, offset)) {
            return Boolean.TRUE;
        }
        // 记录签到
        redisUtil.setBit(signKey, offset, true);
        aiQuotaCoreService.recordSignReward(userId, getCumulativeSignDays());
        return Boolean.TRUE;
    }

    @Override
    public Boolean isSignedToday(){
        return redisUtil.getBit(RedisConstants.USER_SIGN + StpUtil.getLoginIdAsString(),getOffset());
    }

    @Override
    public Long getCumulativeSignDays(){
        return redisUtil.bitCount(RedisConstants.USER_SIGN + StpUtil.getLoginIdAsString(),0,getOffset());
    }

    @Override
    public int getConsecutiveSignDays(){
        int consecutiveDays = 0;
        int maxConsecutiveDays = 0;

        long endOffset = getOffset();
        for (long offset = endOffset; offset >= 0; offset--) {
            boolean isSigned = redisUtil.getBit(RedisConstants.USER_SIGN + StpUtil.getLoginIdAsString(), offset);
            if (isSigned) {
                consecutiveDays++;
                maxConsecutiveDays = Math.max(maxConsecutiveDays, consecutiveDays);
            } else {
                break;
            }
        }

        return maxConsecutiveDays;
    }

    public static long getOffset() {
        LocalDate startDate = Constants.USER_SIGN_START_DATE;
        LocalDate today = LocalDate.now();
        // 计算当前日期相对于起始日期的偏移量
        return ChronoUnit.DAYS.between(startDate, today);
    }
}

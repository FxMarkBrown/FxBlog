package top.fxmarkbrown.blog.aspect;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.annotation.OperationLogger;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.dto.user.LoginUserInfo;
import top.fxmarkbrown.blog.entity.SysOperateLog;
import top.fxmarkbrown.blog.event.operation.OperationLogSaveEvent;
import top.fxmarkbrown.blog.utils.AspectUtil;
import top.fxmarkbrown.blog.utils.IpUtil;
import top.fxmarkbrown.blog.utils.JsonUtil;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * 日志切面
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLoggerAspect {

    private static final Logger logger = LoggerFactory.getLogger(OperationLoggerAspect.class);

    private final ApplicationEventPublisher eventPublisher;

    @Pointcut(value = "@annotation(operationLogger)")
    public void pointcut(OperationLogger operationLogger) {

    }

    @Around(value = "pointcut(operationLogger)", argNames = "joinPoint,operationLogger")
    public Object doAround(ProceedingJoinPoint joinPoint, OperationLogger operationLogger) throws Throwable {
        HttpServletRequest request = IpUtil.getRequest();
        StpUtil.checkLogin();
        if  (!StpUtil.hasRole(Constants.ADMIN)) {
            throw new NotPermissionException("无权限");
        }
        long startTime = System.currentTimeMillis();

        //先执行业务
        Object result = joinPoint.proceed();
        try {
            // 日志收集
            handle(joinPoint, request, startTime);

        } catch (Exception e) {
            logger.error("日志记录出错!", e);
        }

        return result;
    }

    /**
     * 管理员日志收集
     *
     */
    private void handle(ProceedingJoinPoint point, HttpServletRequest request, long startTime) throws Exception {

        Method currentMethod = AspectUtil.INSTANCE.getMethod(point);

        //获取操作名称
        OperationLogger annotation = currentMethod.getAnnotation(OperationLogger.class);

        boolean save = annotation.save();

        String operationName = AspectUtil.INSTANCE.parseParams(point.getArgs(), annotation.value());
        if (!save) {
            return;
        }
        // 获取参数名称字符串
        String paramsJson = getParamsJson(point);

        // 当前操作用户
        LoginUserInfo user = (LoginUserInfo) StpUtil.getSession().get(Constants.CURRENT_USER);
        String type = request.getMethod();
        String ip = IpUtil.getIp();
        String url = request.getRequestURI();

        // 存储日志
        long spendTime = System.currentTimeMillis() - startTime;

        SysOperateLog operateLog = SysOperateLog.builder()
                .ip(ip)
                .source(IpUtil.getIp2region(ip))
                .type(type)
                .username(user.getUsername())
                .paramsJson(paramsJson)
                .requestUrl(url)
                .spendTime(spendTime)
                .methodName(point.getSignature().getName())
                .classPath(point.getTarget().getClass().getName())
                .operationName(operationName).build();

        eventPublisher.publishEvent(new OperationLogSaveEvent(operateLog));
    }

    private String getParamsJson(ProceedingJoinPoint joinPoint) throws ClassNotFoundException, NoSuchMethodException {
        // 参数值
        Object[] args = joinPoint.getArgs();
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        String[] parameterNames = methodSignature.getParameterNames();

        // 通过map封装参数和参数值
        HashMap<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {
            paramMap.put(parameterNames[i], args[i]);
        }

        boolean isContains = paramMap.containsKey("request");
        if (isContains) paramMap.remove("request");
        return JsonUtil.toJsonString(paramMap);
    }
}

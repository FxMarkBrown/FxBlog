package top.fxmarkbrown.blog.config.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import top.fxmarkbrown.blog.utils.SpringUtil;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

public class ApiAccessLogInterceptor implements HandlerInterceptor {

    private static final String ATTRIBUTE_STOP_WATCH = "ApiAccessLogInterceptor.StopWatch";

    private static final Logger logger = LoggerFactory.getLogger(ApiAccessLogInterceptor.class);

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // 记录 HandlerMethod，提供给 ApiAccessLogFilter 使用
        HandlerMethod handlerMethod = handler instanceof HandlerMethod ? (HandlerMethod) handler : null;

        if (!Objects.equals(SpringUtil.getActiveProfile(), "prod")) {
            String method = request.getMethod();
            Map<String, String> params = getParameters(request);
            if (params.isEmpty()) {
                logger.info("==> [入站请求 URL({}) 方法({}) 无参数]", request.getRequestURI(), method);
            }else {
                logger.info("==> [入站请求 URL({}) 方法({}) 参数({})]", request.getRequestURI(), method, params);
            }

            // 计时
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            request.setAttribute(ATTRIBUTE_STOP_WATCH, stopWatch);
            // 打印 Controller 路径
            printHandlerMethodPosition(handlerMethod);
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) throws Exception {
        if (!Objects.equals(SpringUtil.getActiveProfile(), "prod")) {
            StopWatch stopWatch = (StopWatch) request.getAttribute(ATTRIBUTE_STOP_WATCH);
            stopWatch.stop();
            logger.info("<== [请求完毕 URL({}) 耗时({} ms)]",
                    request.getRequestURI(), stopWatch.getTotalTimeMillis());
        }
    }

    private Map<String, String> getParameters(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameter(paramName);
            params.put(paramName, paramValue);
        }
        return params;
    }

    /**
     * 打印 Controller 方法路径
     */
    private void printHandlerMethodPosition(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return;
        }
        Method method = handlerMethod.getMethod();
        Class<?> clazz = method.getDeclaringClass();
        try {
            // 获取 method 的 lineNumber
            Path classFile = Path.of(Objects.requireNonNull(clazz.getResource(clazz.getSimpleName() + ".class")).toURI());
            Path sourceFile = Path.of(classFile.toString()
                    .replace("/build/classes/java/main/", "/src/main/java/")
                    .replace(".class", ".java"));
            List<String> clazzContents = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
            Optional<Integer> lineNumber = IntStream.range(0, clazzContents.size())
                    .filter(i -> clazzContents.get(i).contains(" " + method.getName() + "(")) // 简单匹配，不考虑方法重名
                    .mapToObj(i -> i + 1) // 行号从 1 开始
                    .findFirst();
            if (lineNumber.isEmpty()) {
                return;
            }
            // 打印结果
            System.out.printf("\tController 方法路径：%s(%s.java:%d)\n", clazz.getName(), clazz.getSimpleName(), lineNumber.get());
        } catch (Exception ignore) {
            // 忽略异常。原因：仅仅打印，非重要逻辑
        }
    }
}

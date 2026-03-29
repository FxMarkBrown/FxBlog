package top.fxmarkbrown.blog.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import top.fxmarkbrown.blog.entity.SysJob;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

public class JobInvokeUtils {

    /**
     * 允许调用的包名白名单
     */
    private static final String[] ALLOWED_PACKAGES = {"top.fxmarkbrown.blog"};

    /**
     * 禁止调用的类名黑名单
     */
    private static final String[] BLOCKED_CLASSES = {
            "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.System",
            "java.lang.Thread", "java.lang.reflect", "java.io", "java.net",
            "javax.naming", "jakarta.naming", "org.yaml.snakeyaml", "org.springframework.cglib",
            "org.springframework.expression", "java.lang.ClassLoader"
    };

    /**
     * 执行方法
     *
     * @param job 系统任务
     */
    public static void invokeMethod(SysJob job) throws Exception
    {
        String invokeTarget = job.getInvokeTarget();
        String beanName = getBeanName(invokeTarget);
        String methodName = getMethodName(invokeTarget);
        List<Object[]> methodParams = getMethodParams(invokeTarget);

        // 安全检查：验证调用目标是否在白名单内
        validateInvokeTarget(beanName);

        if (!isValidClassName(beanName))
        {
            Object bean = SpringUtil.getBean(beanName);
            invokeMethod(bean, methodName, methodParams);
        }
        else
        {
            // 禁止通过 Class.forName 实例化任意类，只允许从 Spring 容器获取 Bean
            throw new SecurityException("不允许通过全限定类名实例化对象，请使用 Spring Bean 名称调用");
        }
    }

    /**
     * 验证调用目标的安全性
     */
    private static void validateInvokeTarget(String beanName) {
        if (isValidClassName(beanName)) {
            // 检查黑名单
            for (String blocked : BLOCKED_CLASSES) {
                if (beanName.startsWith(blocked)) {
                    throw new SecurityException("禁止调用的类: " + beanName);
                }
            }
            // 检查白名单
            boolean allowed = false;
            for (String pkg : ALLOWED_PACKAGES) {
                if (beanName.startsWith(pkg)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new SecurityException("不允许调用该包下的类: " + beanName);
            }
        }
    }

    /**
     * 调用任务方法
     *
     * @param bean 目标对象
     * @param methodName 方法名称
     * @param methodParams 方法参数
     */
    private static void invokeMethod(Object bean, String methodName, List<Object[]> methodParams)
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException
    {
        if (methodParams != null && !methodParams.isEmpty())
        {
            Method method = bean.getClass().getDeclaredMethod(methodName, getMethodParamsType(methodParams));
            method.invoke(bean, getMethodParamsValue(methodParams));
        }
        else
        {
            Method method = bean.getClass().getDeclaredMethod(methodName);
            method.invoke(bean);
        }
    }

    /**
     * 校验是否为为class包名
     *
     * @return true是 false否
     */
    public static boolean isValidClassName(String invokeTarget)
    {
        return StringUtils.countMatches(invokeTarget, ".") > 1;
    }

    /**
     * 获取bean名称
     *
     * @param invokeTarget 目标字符串
     * @return bean名称
     */
    public static String getBeanName(String invokeTarget)
    {
        String beanName = StringUtils.substringBefore(invokeTarget, "(");
        return StringUtils.substringBeforeLast(beanName, ".");
    }

    /**
     * 获取bean方法
     *
     * @param invokeTarget 目标字符串
     * @return method方法
     */
    public static String getMethodName(String invokeTarget)
    {
        String methodName = StringUtils.substringBefore(invokeTarget, "(");
        return StringUtils.substringAfterLast(methodName, ".");
    }

    /**
     * 获取method方法参数相关列表
     *
     * @param invokeTarget 目标字符串
     * @return method方法相关参数列表
     */
    public static List<Object[]> getMethodParams(String invokeTarget)
    {
        String methodStr = StringUtils.substringBetween(invokeTarget, "(", ")");
        if (StringUtils.isEmpty(methodStr))
        {
            return null;
        }
        String[] methodParams = methodStr.split(",(?=(?:[^']*\"[^']*')*[^']*$)");
        List<Object[]> classs = new LinkedList<>();
        for (String methodParam : methodParams) {
            String str = StringUtils.trimToEmpty(methodParam);
            // String字符串类型，包含'
            if (Strings.CS.contains(str, "'")) {
                classs.add(new Object[]{Strings.CS.replace(str, "'", ""), String.class});
            }
            // boolean布尔类型，等于true或者false
            else if (Strings.CS.equals(str, "true") || Strings.CI.equals(str, "false")) {
                classs.add(new Object[]{Boolean.valueOf(str), Boolean.class});
            }
            // long长整形，包含L
            else if (Strings.CI.contains(str, "L")) {
                classs.add(new Object[]{Long.valueOf(Strings.CI.replace(str, "L", "")), Long.class});
            }
            // double浮点类型，包含D
            else if (Strings.CI.contains(str, "D")) {
                classs.add(new Object[]{Double.valueOf(Strings.CI.replace(str, "D", "")), Double.class});
            }
            // 其他类型归类为整形
            else {
                classs.add(new Object[]{Integer.valueOf(str), Integer.class});
            }
        }
        return classs;
    }

    /**
     * 获取参数类型
     *
     * @param methodParams 参数相关列表
     * @return 参数类型列表
     */
    public static Class<?>[] getMethodParamsType(List<Object[]> methodParams)
    {
        Class<?>[] classs = new Class<?>[methodParams.size()];
        int index = 0;
        for (Object[] os : methodParams)
        {
            classs[index] = (Class<?>) os[1];
            index++;
        }
        return classs;
    }

    /**
     * 获取参数值
     *
     * @param methodParams 参数相关列表
     * @return 参数值列表
     */
    public static Object[] getMethodParamsValue(List<Object[]> methodParams)
    {
        Object[] classs = new Object[methodParams.size()];
        int index = 0;
        for (Object[] os : methodParams)
        {
            classs[index] = os[0];
            index++;
        }
        return classs;
    }
}

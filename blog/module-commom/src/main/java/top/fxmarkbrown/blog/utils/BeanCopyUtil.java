package top.fxmarkbrown.blog.utils;

public class BeanCopyUtil {

    /**
     * 对象拷贝
     */
    public static <S, T> T copyObj(S source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        T target = null;
        try {
            target = targetClass.getConstructor().newInstance();
            org.springframework.beans.BeanUtils.copyProperties(source, target);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return target;
    }

}

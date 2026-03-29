package top.fxmarkbrown.blog.quartz;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.fxmarkbrown.blog.common.RedisConstants;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.utils.RedisUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component("task")
@RequiredArgsConstructor
public class TaskQuartz {

    private final RedisUtil redisUtil;

    private final SysArticleMapper articleMapper;

    public void neatMultipleParams(String s, Boolean b, Long l, Double d, Integer i) {
        System.out.println("执行多参方法： 字符串类型" + s + "，布尔类型" + b + "，长整型" + l + "，浮点型" + d + "，整形" + i);
    }

    public void neatParams(String params) {
        System.out.println("执行有参方法：" + params);
    }

    public void neatNoParams() {
        System.out.println("执行无参方法");
    }


    /**
     * 定时同步阅读量
     */
    public void syncQuantity() {
        // 获取带阅读量的前缀key集合
        List<SysArticle> articles = new ArrayList<>();
        Map<Object, Object> map = redisUtil.hGetAll(RedisConstants.ARTICLE_QUANTITY);
        // 取出所有数据更新到数据库
        for (Map.Entry<Object, Object> stringEntry : map.entrySet()) {
            Object id = stringEntry.getKey();
            //noinspection unchecked
            List<String> list = (List<String>) stringEntry.getValue();
            SysArticle article = SysArticle.builder()
                    .id(Long.parseLong(id.toString())).quantity(list.size())
                    .build();
            articles.add(article);
        }
        articleMapper.updateBatchQuantity(articles);
    }


}

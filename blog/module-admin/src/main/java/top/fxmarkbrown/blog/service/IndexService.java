package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.vo.dashboard.IndexVo;

import java.util.List;
import java.util.Map;


public interface IndexService {

    /**
     * 首页获取顶部数据
     */
    IndexVo index();

    /**
     * 获取分类
     */
    List<Map<String, Integer>> getCategories();


}

package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.vo.tag.TagListVo;

import java.util.List;

public interface TagService {

    /**
     * 获取标签列表
     */
    List<TagListVo> getTagsApi();

}

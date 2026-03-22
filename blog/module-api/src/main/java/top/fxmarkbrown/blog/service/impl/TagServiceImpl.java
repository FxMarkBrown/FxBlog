package top.fxmarkbrown.blog.service.impl;

import org.springframework.cache.annotation.Cacheable;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.mapper.SysTagMapper;
import top.fxmarkbrown.blog.service.TagService;
import top.fxmarkbrown.blog.vo.tag.TagListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final SysTagMapper sysTagMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_TAG, key = "'list'", sync = true)
    public List<TagListVo> getTagsApi() {
        return sysTagMapper.getTagsApi();
    }
}

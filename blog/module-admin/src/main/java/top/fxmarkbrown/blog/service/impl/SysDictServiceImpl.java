package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.entity.SysDict;
import top.fxmarkbrown.blog.mapper.SysDictDataMapper;
import top.fxmarkbrown.blog.mapper.SysDictMapper;
import top.fxmarkbrown.blog.service.SysDictService;
import top.fxmarkbrown.blog.utils.PageUtil;

@Service
@RequiredArgsConstructor
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements SysDictService {

    private final SysDictDataMapper dictDataMapper;

    @Override
    public IPage<SysDict> getDictPageList(String name,Integer status) {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<SysDict>()
                .like(StringUtils.hasText(name),SysDict::getName, name)
                .eq(status != null,SysDict::getStatus, status)
                .orderByAsc(SysDict::getSort);

        return baseMapper.selectPage(PageUtil.getPage(), wrapper);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_DICT, allEntries = true)
    public void addDict(SysDict dict) {
        // 检查字典类型是否已存在
        if (checkTypeExists(dict.getType(), null)) {
            throw new RuntimeException("字典类型已存在");
        }
        save(dict);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_DICT, allEntries = true)
    public void updateDict(SysDict dict) {
        // 检查字典是否存在
        if (getById(dict.getId()) == null) {
            throw new RuntimeException("字典不存在");
        }
        // 检查字典类型是否已存在
        if (checkTypeExists(dict.getType(), dict.getId())) {
            throw new RuntimeException("字典类型已存在");
        }
        updateById(dict);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_DICT, allEntries = true)
    public void deleteDict(Long id) {
        removeById(id);
    }

    /**
     * 检查字典类型是否已存在
     */
    private boolean checkTypeExists(String type, Long excludeId) {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDict::getType, type);
        if (excludeId != null) {
            wrapper.ne(SysDict::getId, excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }
}

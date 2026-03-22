package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.mapper.SysMomentMapper;
import top.fxmarkbrown.blog.service.MomentService;
import top.fxmarkbrown.blog.vo.moment.MomentPageVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MomentServiceImpl implements MomentService {

    private final SysMomentMapper baseMapper;

    @Override
    public PageResponse<MomentPageVo> getMomentList(PageQuery pageQuery) {
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        IPage<MomentPageVo> page = baseMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()));
        return PageResponse.from(page);
    }
}

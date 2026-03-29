package top.fxmarkbrown.blog.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fxmarkbrown.blog.entity.SysComment;
import top.fxmarkbrown.blog.mapper.SysCommentMapper;
import top.fxmarkbrown.blog.service.SysCommentService;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.comment.SysCommentVO;

@Service
@RequiredArgsConstructor
public class SysCommentServiceImpl extends ServiceImpl<SysCommentMapper,SysComment> implements SysCommentService {

    @Override
    public Page<SysCommentVO> selectList() {
        return baseMapper.selectPage(PageUtil.getPage());
    }
}

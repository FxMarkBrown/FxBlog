package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.entity.SysComment;
import top.fxmarkbrown.blog.vo.comment.SysCommentVO;

public interface SysCommentService extends IService<SysComment> {

    /**
     * 获取评论列表
     */
    Page<SysCommentVO> selectList();

}

package top.fxmarkbrown.blog.service;

import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.entity.SysComment;
import top.fxmarkbrown.blog.vo.comment.CommentListVo;

public interface CommentService {

    /**
     * 获取评论列表
     */

    PageResponse<CommentListVo> getComments(PageQuery pageQuery, Long articleId, String sortType);

    /**
     * 新增评论
     */
    void add(SysComment sysComment);
}

package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.vo.ai.AiQuotaLogVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.comment.CommentListVo;

import java.util.List;

public interface UserService {

    /**
     * 查询我的评论
     */
    IPage<CommentListVo> selectMyComment();

    /**
     * 删除我的评论
     */
    Void delMyComment(List<Long> ids);

    /**
     * 查询我的点赞
     */
    IPage<ArticleListVo> selectMyLike();

    /**
     * 查询我的收藏
     */
    IPage<ArticleListVo> selectMyFavorite();

    /**
     * 查询我的 AI 额度流水
     */
    IPage<AiQuotaLogVo> getMyAiQuotaLogs();

    /**
     * 查询我的回复
     */
    IPage<CommentListVo> getMyReply();

    /**
     * 修改我的资料
     */
    void updateProfile(SysUser user);

    /**
     * 查询我的文章
     */
    IPage<ArticleListVo> selectMyArticle(SysArticle article);

}

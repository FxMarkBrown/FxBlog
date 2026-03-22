package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysComment;
import top.fxmarkbrown.blog.entity.SysUser;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysCommentMapper;
import top.fxmarkbrown.blog.mapper.SysTagMapper;
import top.fxmarkbrown.blog.mapper.SysUserMapper;
import top.fxmarkbrown.blog.service.UserService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.ai.AiQuotaLogVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.comment.CommentListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;

    private final SysCommentMapper commentMapper;

    private final SysArticleMapper articleMapper;

    private final SysTagMapper tagMapper;

    private final AiQuotaCoreService aiQuotaCoreService;

    @Override
    public IPage<CommentListVo> selectMyComment() {
        return commentMapper.selectMyComment(PageUtil.getPage(), StpUtil.getLoginIdAsLong());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Void delMyComment(List<Long> ids) {
        commentMapper.deleteByIds(ids);
        commentMapper.delete(new LambdaQueryWrapper<SysComment>()
                .in(SysComment::getParentId, ids));
        return null;
    }

    @Override
    public IPage<ArticleListVo> selectMyLike() {
        return articleMapper.selectMyLike(PageUtil.getPage(),StpUtil.getLoginIdAsLong());
    }

    @Override
    public IPage<ArticleListVo> selectMyFavorite() {
        return articleMapper.selectMyFavorite(PageUtil.getPage(), StpUtil.getLoginIdAsLong());
    }

    @Override
    public IPage<AiQuotaLogVo> getMyAiQuotaLogs() {
        return aiQuotaCoreService.pageUserLogs(StpUtil.getLoginIdAsLong());
    }

    @Override
    public IPage<CommentListVo> getMyReply() {
        return commentMapper.getMyReply(PageUtil.getPage(),StpUtil.getLoginIdAsLong());
    }

    @Override
    public void updateProfile(SysUser user) {
        sysUserMapper.updateById(user);
    }

    @Override
    public IPage<ArticleListVo> selectMyArticle(SysArticle article) {
        article.setUserId(StpUtil.getLoginIdAsLong());
        return articleMapper.selectMyArticle(PageUtil.getPage(),article);
    }

}

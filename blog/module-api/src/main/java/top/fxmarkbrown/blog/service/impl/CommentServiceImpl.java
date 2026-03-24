package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.entity.SysComment;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.mapper.SysCommentMapper;
import top.fxmarkbrown.blog.service.CommentService;
import top.fxmarkbrown.blog.utils.IpUtil;
import top.fxmarkbrown.blog.utils.SensitiveUtil;
import top.fxmarkbrown.blog.vo.comment.CommentListVo;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final SysCommentMapper sysCommentMapper;

    private final SysAsyncServiceImpl sysAsyncServiceImpl;

    @Override
    public PageResponse<CommentListVo> getComments(PageQuery pageQuery, Long articleId, String sortType) {
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        IPage<CommentListVo> page = sysCommentMapper.getComments(
                new Page<>(query.getPageNum(), query.getPageSize()),
                articleId,
                sortType
        );
        attachChildren(page.getRecords());
        return PageResponse.from(page);
    }

    private void attachChildren(List<CommentListVo> parentComments) {
        if (parentComments == null || parentComments.isEmpty()) {
            return;
        }

        List<Long> parentIds = parentComments.stream()
                .map(CommentListVo::getId)
                .filter(id -> id != null && id > 0)
                .toList();
        if (parentIds.isEmpty()) {
            return;
        }

        List<CommentListVo> allChildren = sysCommentMapper.getChildrenCommentsByParentIds(parentIds);
        if (allChildren == null || allChildren.isEmpty()) {
            parentComments.forEach(comment -> comment.setChildren(Collections.emptyList()));
            return;
        }

        Map<Long, List<CommentListVo>> childrenByParentId = allChildren.stream()
                .filter(comment -> comment.getParentId() != null)
                .collect(Collectors.groupingBy(CommentListVo::getParentId, LinkedHashMap::new, Collectors.toList()));

        parentComments.forEach(comment ->
                comment.setChildren(childrenByParentId.getOrDefault(comment.getId(), Collections.emptyList()))
        );
    }

    @Override
    public void add(SysComment sysComment) {

        String ip = IpUtil.getIp();
        sysComment.setIp(ip);
        sysComment.setIpSource(IpUtil.getIp2region(ip));
        sysComment.setUserId(StpUtil.getLoginIdAsLong());
        // HTML净化：只允许安全标签，防止XSS
        String sanitized = Jsoup.clean(sysComment.getContent(), Safelist.relaxed().removeTags("script", "style")
                .removeAttributes(":all", "onclick", "onerror", "onload", "onmouseover", "onfocus", "onblur"));
        sysComment.setContent(SensitiveUtil.filter(sanitized));

        sysCommentMapper.insert(sysComment);

        sysAsyncServiceImpl.publishNotification(SysNotifications.builder()
                .title(sysComment.getReplyUserId() != null ? "评论回复通知" : "新评论通知")
                .message(sysComment.getContent())
                .articleId(sysComment.getArticleId())
                .isRead(Boolean.FALSE)
                .type("comment")
                .userId(sysComment.getReplyUserId())
                .fromUserId(sysComment.getUserId())
                .build());
    }
}

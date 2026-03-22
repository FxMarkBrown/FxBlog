package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.cache.annotation.Cacheable;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.common.PageQuery;
import top.fxmarkbrown.blog.common.PageResponse;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysCategory;
import top.fxmarkbrown.blog.entity.SysNotifications;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysCategoryMapper;
import top.fxmarkbrown.blog.service.ArticleService;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.utils.IpUtil;
import top.fxmarkbrown.blog.vo.article.ArchiveListVo;
import top.fxmarkbrown.blog.vo.article.ArticleDetailVo;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.CategoryListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final SysArticleMapper sysArticleMapper;

    private final SysCategoryMapper sysCategoryMapper;

    private final SysAsyncServiceImpl sysAsyncServiceImpl;

    private final AiQuotaCoreService aiQuotaCoreService;

    @Override
    public PageResponse<ArticleListVo> getArticleList(PageQuery pageQuery, Integer tagId, Integer categoryId, String keyword) {
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        IPage<ArticleListVo> page = sysArticleMapper.getArticleListApi(
                new Page<>(query.getPageNum(), query.getPageSize()),
                tagId,
                categoryId,
                keyword
        );
        return PageResponse.from(page);
    }

    @Override
    public ArticleDetailVo getArticleDetail(Long id) {
        ArticleDetailVo detailVo = sysArticleMapper.getArticleDetail(id);
        if (detailVo == null) {
            return null;
        }
        detailVo.setIsLike(Boolean.FALSE);
        detailVo.setIsFavorite(Boolean.FALSE);
        // 判断是否点赞
        if (StpUtil.isLogin()) {
            long currentUserId = StpUtil.getLoginIdAsLong();
            detailVo.setIsLike(sysArticleMapper.getUserIsLike(id, currentUserId));
            detailVo.setIsFavorite(sysArticleMapper.getUserIsFavorite(id, currentUserId));
        }

        //添加阅读量
        String ip = IpUtil.getIp();
        sysAsyncServiceImpl.recordArticleView(id, ip);
        return detailVo;
    }

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_ARTICLE_ARCHIVE, key = "'list'", sync = true)
    public List<ArchiveListVo> getArticleArchive() {

        List<ArchiveListVo> list = new ArrayList<>();

        List<Integer> years = sysArticleMapper.getArticleArchive();
        for (Integer year : years) {
            List<ArticleListVo> articleListVos = sysArticleMapper.getArticleByYear(year);
            list.add(new ArchiveListVo(year, articleListVos));
        }
        return list;
    }

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_ARTICLE_CATEGORIES, key = "'list'", sync = true)
    public List<CategoryListVo> getArticleCategories() {
        return sysCategoryMapper.getArticleCategories();
    }

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_ARTICLE_CAROUSEL, key = "'list'", sync = true)
    public List<ArticleListVo> getCarouselArticle() {
        return getArticlesByCondition(SysArticle::getIsCarousel);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.PUBLIC_ARTICLE_RECOMMEND, key = "'list'", sync = true)
    public List<ArticleListVo> getRecommendArticle() {
        return getArticlesByCondition(SysArticle::getIsRecommend);
    }

    @Override
    public Boolean like(Long articleId) {
        long userId = StpUtil.getLoginIdAsLong();
        SysArticle article = sysArticleMapper.selectById(articleId);
        if (article == null || !Integer.valueOf(1).equals(article.getStatus())) {
            throw new ServiceException("文章不存在或暂不可点赞");
        }
        if (article.getUserId() != null && article.getUserId() == userId) {
            throw new ServiceException("不能给自己的文章点赞");
        }

        aiQuotaCoreService.assertAndRecordLikeAction(userId, articleId);
        sysArticleMapper.like(articleId, userId);
        aiQuotaCoreService.recordLikeReward(userId, articleId, article.getTitle(), 1L, true);
        sysAsyncServiceImpl.publishNotification(SysNotifications.builder()
                .title("文章点赞通知")
                .articleId(articleId)
                .isRead(Boolean.FALSE)
                .type("like")
                .fromUserId(userId)
                .build());
        return true;
    }

    @Override
    public Boolean unlike(Long articleId) {
        long userId = StpUtil.getLoginIdAsLong();
        SysArticle article = sysArticleMapper.selectById(articleId);
        Long likeTimes = sysArticleMapper.countUserLikeTimes(articleId, userId);
        sysArticleMapper.unLike(articleId, userId);
        if (article != null
                && Integer.valueOf(1).equals(article.getStatus())
                && article.getUserId() != null
                && article.getUserId() != userId) {
            aiQuotaCoreService.recordLikeReward(userId, articleId, article.getTitle(), likeTimes == null ? 0L : likeTimes, false);
        }
        return true;
    }

    @Override
    public Boolean favorite(Long articleId) {
        long userId = StpUtil.getLoginIdAsLong();
        SysArticle article = sysArticleMapper.selectById(articleId);
        if (article == null || !Integer.valueOf(1).equals(article.getStatus())) {
            throw new ServiceException("文章不存在或暂不可收藏");
        }
        Boolean isFavorite = sysArticleMapper.getUserIsFavorite(articleId, userId);
        if (isFavorite) {
            sysArticleMapper.unFavorite(articleId, userId);
            if (article.getUserId() != null && article.getUserId() != userId) {
                aiQuotaCoreService.recordFavoriteReward(userId, articleId, article.getTitle(), false);
            }
        } else {
            sysArticleMapper.favorite(articleId, userId);
            if (article.getUserId() != null && article.getUserId() != userId) {
                aiQuotaCoreService.recordFavoriteReward(userId, articleId, article.getTitle(), true);
            }
            sysAsyncServiceImpl.publishNotification(SysNotifications.builder()
                    .title("文章收藏通知")
                    .articleId(articleId)
                    .isRead(Boolean.FALSE)
                    .type("favorite")
                    .fromUserId(userId)
                    .build());
        }
        return true;
    }

    @Override
    public List<SysCategory> getCategoryAll() {
        return sysCategoryMapper.selectList(new LambdaQueryWrapper<SysCategory>()
                .orderByAsc(SysCategory::getSort));
    }

    private List<ArticleListVo> getArticlesByCondition(SFunction<SysArticle, Object> conditionField) {
        LambdaQueryWrapper<SysArticle> wrapper = new LambdaQueryWrapper<SysArticle>()
                .select(SysArticle::getId, SysArticle::getTitle, SysArticle::getCover, SysArticle::getCreateTime)
                .orderByDesc(SysArticle::getCreateTime)
                .eq(conditionField, 1);

        List<SysArticle> sysArticles = sysArticleMapper.selectList(wrapper);

        if (sysArticles == null || sysArticles.isEmpty()) {
            return Collections.emptyList();
        }

        return sysArticles.stream().map(item -> ArticleListVo.builder()
                .id(item.getId())
                .cover(item.getCover())
                .title(item.getTitle())
                .createTime(item.getCreateTime())
                .build()).collect(Collectors.toList());
    }
}

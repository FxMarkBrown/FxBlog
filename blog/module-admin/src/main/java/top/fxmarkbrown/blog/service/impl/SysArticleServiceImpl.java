package top.fxmarkbrown.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import top.fxmarkbrown.blog.common.Constants;
import top.fxmarkbrown.blog.common.CacheNames;
import top.fxmarkbrown.blog.dto.article.ArticleQueryDto;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysCategory;
import top.fxmarkbrown.blog.entity.SysTag;
import top.fxmarkbrown.blog.exception.ServiceException;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysCategoryMapper;
import top.fxmarkbrown.blog.mapper.SysTagMapper;
import top.fxmarkbrown.blog.service.AiQuotaCoreService;
import top.fxmarkbrown.blog.service.SysArticleService;
import top.fxmarkbrown.blog.utils.PageUtil;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.SysArticleDetailVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.fxmarkbrown.blog.event.ai.AiArticleIndexRemoveEvent;
import top.fxmarkbrown.blog.event.ai.AiArticleIndexSyncEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SysArticleServiceImpl extends ServiceImpl<SysArticleMapper, SysArticle> implements SysArticleService {

    private final SysTagMapper sysTagMapper;

    private final SysCategoryMapper sysCategoryMapper;

    private final AiQuotaCoreService aiQuotaCoreService;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public IPage<ArticleListVo> selectPage(ArticleQueryDto articleQueryDto) {
        return baseMapper.selectPageList(PageUtil.getPage(), articleQueryDto);
    }

    @Override
    public SysArticleDetailVo detail(Integer id) {
        SysArticle sysArticle = baseMapper.selectById(id);

        SysArticleDetailVo sysArticleDetailVo = new SysArticleDetailVo();
        BeanUtils.copyProperties(sysArticle, sysArticleDetailVo);

        SysCategory sysCategory = sysCategoryMapper.selectById(sysArticle.getCategoryId());
        sysArticleDetailVo.setCategoryName(sysCategory.getName());

        //获取标签
        List<String> tags = sysTagMapper.getTagNameByArticleId(id);
        sysArticleDetailVo.setTags(tags);
        return sysArticleDetailVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_ARCHIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CAROUSEL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_RECOMMEND, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_TAG, allEntries = true)
    })
    public Boolean add(SysArticleDetailVo sysArticle) {

        SysArticle obj = new SysArticle();
        BeanUtils.copyProperties(sysArticle, obj);
        obj.setUserId(StpUtil.getLoginIdAsLong());

        //添加分类
        addCategory(sysArticle, obj);
        baseMapper.insert(obj);
        if (Integer.valueOf(Constants.YES).equals(obj.getStatus())) {
            aiQuotaCoreService.recordArticleReward(obj.getUserId(), obj.getId(), obj.getTitle(), true);
        }

        addTags(sysArticle, obj);
        eventPublisher.publishEvent(new AiArticleIndexSyncEvent(obj.getId()));

        return true;
    }




    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_ARCHIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CAROUSEL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_RECOMMEND, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_TAG, allEntries = true)
    })
    public Boolean update(SysArticleDetailVo sysArticle) {
        SysArticle previousArticle = baseMapper.selectById(sysArticle.getId());
        if (previousArticle == null) {
            throw new ServiceException("文章不存在");
        }

        SysArticle obj = new SysArticle();
        BeanUtils.copyProperties(sysArticle, obj);

        //没有管理员权限就只能修改自己的文章
        if (!StpUtil.hasRole(Constants.ADMIN)) {
            long currentUserId = StpUtil.getLoginIdAsLong();
            if (!Objects.equals(previousArticle.getUserId(), currentUserId)) {
                throw new ServiceException("只能修改自己的文章");
            }
        }

        addCategory(sysArticle, obj);
        baseMapper.updateById(obj);
        if (!Objects.equals(previousArticle.getStatus(), obj.getStatus())) {
            if (Integer.valueOf(Constants.YES).equals(obj.getStatus())) {
                aiQuotaCoreService.recordArticleReward(previousArticle.getUserId(), obj.getId(), obj.getTitle(), true);
            } else if (Integer.valueOf(Constants.YES).equals(previousArticle.getStatus())) {
                aiQuotaCoreService.recordArticleReward(previousArticle.getUserId(), previousArticle.getId(), previousArticle.getTitle(), false);
            }
        }

        //先删除标签在新增标签
        sysTagMapper.deleteArticleTagsByArticleIds(Collections.singletonList(obj.getId()));
        addTags(sysArticle, obj);
        eventPublisher.publishEvent(new AiArticleIndexSyncEvent(obj.getId()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_ARCHIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CAROUSEL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_RECOMMEND, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_TAG, allEntries = true)
    })
    public Boolean delete(List<Long> ids) {

        //没有管理员权限就只能删除自己的文章
        if (!StpUtil.hasRole(Constants.ADMIN)) {
            long currentUserId = StpUtil.getLoginIdAsLong();
            List<SysArticle> sysArticles = baseMapper.selectByIds(ids);
            for (SysArticle sysArticle : sysArticles) {
                if (!Objects.equals(sysArticle.getUserId(), currentUserId)) {
                    throw new RuntimeException("只能删除自己的文章");
                }
            }
        }

        List<SysArticle> removedArticles = baseMapper.selectByIds(ids);
        baseMapper.deleteByIds(ids);
        sysTagMapper.deleteArticleTagsByArticleIds(ids);
        List<Long> removedArticleIds = new ArrayList<>();
        for (SysArticle removedArticle : removedArticles) {
            if (removedArticle != null && Integer.valueOf(Constants.YES).equals(removedArticle.getStatus())) {
                aiQuotaCoreService.recordArticleReward(removedArticle.getUserId(), removedArticle.getId(), removedArticle.getTitle(), false);
            }
            if (removedArticle != null) {
                removedArticleIds.add(removedArticle.getId());
            }
        }
        if (!removedArticleIds.isEmpty()) {
            eventPublisher.publishEvent(new AiArticleIndexRemoveEvent(removedArticleIds));
        }
        return true;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_ARCHIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_CAROUSEL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_ARTICLE_RECOMMEND, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_TAG, allEntries = true)
    })
    public boolean updateById(SysArticle entity) {
        return super.updateById(entity);
    }

    private void addCategory(SysArticleDetailVo sysArticle, SysArticle obj) {
        SysCategory sysCategory = sysCategoryMapper.selectOne(new LambdaQueryWrapper<SysCategory>()
                .eq(SysCategory::getName, sysArticle.getCategoryName()));
        if (sysCategory == null) {
            sysCategory = SysCategory.builder().name(sysArticle.getCategoryName()).build();
            sysCategoryMapper.insert(sysCategory);
        }
        obj.setCategoryId(sysCategory.getId());
    }

    private void addTags(SysArticleDetailVo sysArticle, SysArticle obj) {
        //添加标签
        List<Integer> tagIds = new ArrayList<>();
        for (String tag : sysArticle.getTags()) {
            SysTag sysTag = sysTagMapper.selectOne(new LambdaQueryWrapper<SysTag>().eq(SysTag::getName, tag));
            if (sysTag == null) {
                sysTag = SysTag.builder().name(tag).build();
                sysTagMapper.insert(sysTag);
            }
            tagIds.add(sysTag.getId());
        }
        sysTagMapper.addArticleTagRelations(obj.getId(), tagIds);
    }

}

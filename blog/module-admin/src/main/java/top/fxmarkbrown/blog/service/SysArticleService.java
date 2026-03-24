package top.fxmarkbrown.blog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import top.fxmarkbrown.blog.dto.article.ArticleQueryDto;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.vo.article.ArticleListVo;
import top.fxmarkbrown.blog.vo.article.SysArticleDetailVo;

import java.util.List;

public interface SysArticleService extends IService<SysArticle> {

    /**
     * 分页查询
     */
    IPage<ArticleListVo> selectPage(ArticleQueryDto articleQueryDto);

    /**
     * 文章详情
     */
    SysArticleDetailVo detail(Long id);

    /**
     * 新增
     */
    Boolean add(SysArticleDetailVo sysArticle);

    /**
     * 修改
     */
    Boolean update(SysArticleDetailVo sysArticle);


    /**
     * 删除
     */
    Boolean delete(List<Long> ids);
}

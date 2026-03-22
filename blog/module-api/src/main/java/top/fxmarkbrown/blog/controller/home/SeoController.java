package top.fxmarkbrown.blog.controller.home;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import top.fxmarkbrown.blog.entity.SysArticle;
import top.fxmarkbrown.blog.entity.SysWebConfig;
import top.fxmarkbrown.blog.mapper.SysArticleMapper;
import top.fxmarkbrown.blog.mapper.SysWebConfigMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeoController {

    private static final DateTimeFormatter SITEMAP_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final SysArticleMapper sysArticleMapper;

    private final SysWebConfigMapper sysWebConfigMapper;

    @GetMapping(value = {"/sitemap.xml", "/api/sitemap.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String siteUrl = resolveSiteUrl(request);
        LocalDateTime siteLastModified = resolveSiteLastModified();
        List<String> urls = new ArrayList<>();
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/archive"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/categories"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/tags"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/moments"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/photos"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/messages"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/friends"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/about"));
        urls.add(buildUrlEntry(siteUrl, siteLastModified, "/ai"));

        List<SysArticle> articles = sysArticleMapper.selectList(new LambdaQueryWrapper<SysArticle>()
                .select(SysArticle::getId, SysArticle::getCreateTime, SysArticle::getUpdateTime)
                .eq(SysArticle::getStatus, 1)
                .orderByDesc(SysArticle::getUpdateTime)
                .orderByDesc(SysArticle::getCreateTime));
        for (SysArticle article : articles) {
            LocalDateTime articleLastModified = article.getUpdateTime() != null ? article.getUpdateTime() : article.getCreateTime();
            urls.add(buildUrlEntry(siteUrl, articleLastModified, "/post/" + article.getId()));
        }

        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        urls.forEach(xml::append);
        xml.append("</urlset>");
        return xml.toString();
    }

    @GetMapping(value = {"/robots.txt", "/api/robots.txt"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(HttpServletRequest request) {
        String siteUrl = resolveSiteUrl(request);
        return """
                User-agent: *
                Allow: /
                Disallow: /admin/
                Disallow: /login
                Disallow: /editor
                Disallow: /notifications
                Disallow: /user/

                Sitemap: %s/sitemap.xml
                """.formatted(siteUrl);
    }

    private String buildUrlEntry(String siteUrl, LocalDateTime lastModified, String path) {
        String normalizedPath = "/".equals(path) ? "" : path;
        String loc = xmlEscape(siteUrl + normalizedPath);
        StringBuilder entry = new StringBuilder(256)
                .append("<url><loc>")
                .append(loc)
                .append("</loc>");
        if (lastModified != null) {
            entry.append("<lastmod>")
                    .append(formatSitemapDateTime(lastModified))
                    .append("</lastmod>");
        }
        entry.append("</url>");
        return entry.toString();
    }

    private String resolveSiteUrl(HttpServletRequest request) {
        SysWebConfig config = sysWebConfigMapper.selectOne(new LambdaQueryWrapper<SysWebConfig>().last("limit 1"));
        if (config != null && config.getWebUrl() != null && !config.getWebUrl().isBlank()) {
            return trimTrailingSlash(config.getWebUrl().trim());
        }
        return trimTrailingSlash(ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString());
    }

    private LocalDateTime resolveSiteLastModified() {
        SysWebConfig config = sysWebConfigMapper.selectOne(new LambdaQueryWrapper<SysWebConfig>()
                .select(SysWebConfig::getUpdateTime, SysWebConfig::getCreateTime)
                .last("limit 1"));
        if (config == null) {
            return null;
        }
        return config.getUpdateTime() != null ? config.getUpdateTime() : config.getCreateTime();
    }

    private String formatSitemapDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).format(SITEMAP_DATE_TIME_FORMATTER);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String xmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

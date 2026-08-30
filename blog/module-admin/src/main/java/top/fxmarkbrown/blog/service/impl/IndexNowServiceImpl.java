package top.fxmarkbrown.blog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import top.fxmarkbrown.blog.config.indexnow.IndexNowProperties;
import top.fxmarkbrown.blog.service.IndexNowService;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexNowServiceImpl implements IndexNowService {

    private final IndexNowProperties indexNowProperties;

    private final RestClient restClient = RestClient.create();

    @Override
    public void submitArticles(List<Long> articleIds) {
        if (!indexNowProperties.isEnabled()
                || articleIds == null || articleIds.isEmpty()
                || !StringUtils.hasText(indexNowProperties.getKey())
                || !StringUtils.hasText(indexNowProperties.getSiteUrl())) {
            return;
        }

        String siteUrl = indexNowProperties.getSiteUrl().replaceAll("/+$", "");
        String host = URI.create(siteUrl).getHost();
        String key = indexNowProperties.getKey();

        List<String> urlList = articleIds.stream()
                .distinct()
                .map(id -> siteUrl + "/post/" + id)
                .toList();

        Map<String, Object> payload = Map.of(
                "host", host,
                "key", key,
                "keyLocation", siteUrl + "/" + key + ".txt",
                "urlList", urlList
        );

        try {
            restClient.post()
                    .uri(indexNowProperties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("IndexNow 提交成功, urls={}", urlList);
        } catch (Exception e) {
            // 提交失败不影响文章主流程，仅记录日志
            log.warn("IndexNow 提交失败, urls={}, error={}", urlList, e.getMessage());
        }
    }
}

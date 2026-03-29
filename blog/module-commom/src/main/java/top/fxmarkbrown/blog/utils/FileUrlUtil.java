package top.fxmarkbrown.blog.utils;

import org.springframework.util.StringUtils;
import top.fxmarkbrown.blog.entity.SysFileOss;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FileUrlUtil {

    private FileUrlUtil() {
    }

    public static String toRelativeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String value = url.trim();
        if (value.startsWith("data:") || value.startsWith("blob:")) {
            return value;
        }
        if (isHttpUrl(value)) {
            try {
                URI uri = URI.create(value);
                String path = ensureLeadingSlash(uri.getRawPath());
                StringBuilder builder = new StringBuilder(StringUtils.hasText(path) ? path : "/");
                if (StringUtils.hasText(uri.getRawQuery())) {
                    builder.append('?').append(uri.getRawQuery());
                }
                if (StringUtils.hasText(uri.getRawFragment())) {
                    builder.append('#').append(uri.getRawFragment());
                }
                return builder.toString();
            } catch (Exception ignored) {
            }
        }
        return ensureLeadingSlash(value);
    }

    public static List<String> buildUrlCandidates(String url, List<SysFileOss> ossList) {
        Set<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(url)) {
            candidates.add(url.trim());
        }

        String relativeUrl = toRelativeUrl(url);
        if (StringUtils.hasText(relativeUrl)) {
            candidates.add(relativeUrl);
        }

        if (ossList != null) {
            for (SysFileOss oss : ossList) {
                String absoluteUrl = toAbsoluteUrl(relativeUrl, oss == null ? null : oss.getDomain());
                if (StringUtils.hasText(absoluteUrl)) {
                    candidates.add(absoluteUrl);
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    public static String toAbsoluteUrl(String url, String domain) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String value = url.trim();
        if (isHttpUrl(value) || !StringUtils.hasText(domain)) {
            return value;
        }

        String normalizedDomain = domain.trim();
        if (!isHttpUrl(normalizedDomain)) {
            return ensureLeadingSlash(value);
        }

        try {
            URI uri = URI.create(normalizedDomain);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            String relativeUrl = ensureLeadingSlash(toRelativeUrl(value));
            return origin + relativeUrl;
        } catch (Exception ignored) {
            return value;
        }
    }

    private static boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String ensureLeadingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}

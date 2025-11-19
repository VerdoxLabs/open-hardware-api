package de.verdox.hwapi.io.api.selenium;

import com.google.common.net.InternetDomainName;

import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;

public final class DomainPathUtil {

    private static final int SHARD_LEN = 2; // auf 0 setzen, wenn du kein Sharding willst

    private DomainPathUtil() {}

    public static String normalizeUrl(String url) {
        try {
            URI u = URI.create(url);
            String scheme = Optional.ofNullable(u.getScheme()).orElse("http").toLowerCase(Locale.ROOT);
            String host = Optional.ofNullable(u.getHost()).orElse("").toLowerCase(Locale.ROOT);
            int port = u.getPort();
            String portPart = (port == -1 || port == 80 || port == 443) ? "" : (":" + port);
            String path = Optional.ofNullable(u.getPath()).orElse("/");
            if (path.isBlank()) path = "/";
            String query = (u.getQuery() == null) ? "" : ("?" + u.getQuery());
            return scheme + "://" + host + portPart + path + query; // Fragment wird verworfen
        } catch (Exception e) {
            return url;
        }
    }

    public static String topPrivateDomain(String host) {
        if (host == null) return null;
        try {
            var idn = InternetDomainName.from(host);
            if (idn.isUnderPublicSuffix() || idn.hasPublicSuffix()) {
                return idn.topPrivateDomain().toString();
            }
        } catch (Exception ignored) {}
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) h = h.substring(4);
        String[] p = h.split("\\.");
        if (p.length >= 2) return p[p.length - 2] + "." + p[p.length - 1];
        return h;
    }

    public static String urlHashHex(String normalizedUrl) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(normalizedUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(normalizedUrl.hashCode());
        }
    }

    public static Path domainFolder(Path cacheRoot, String urlOrHost) {
        String host = urlOrHost.contains("://") ? URI.create(urlOrHost).getHost() : urlOrHost;
        String domain = topPrivateDomain(host);
        return cacheRoot.resolve("pages").resolve(domain == null ? "unknown" : domain);
    }

    public static Path pageHtmlPath(Path cacheRoot, String url) {
        String norm = normalizeUrl(url);
        String hash = urlHashHex(norm);
        Path base = domainFolder(cacheRoot, url);
        if (SHARD_LEN > 0 && hash.length() >= SHARD_LEN) {
            base = base.resolve(hash.substring(0, SHARD_LEN));
        }
        return base.resolve(hash + ".html");
    }

    public static Path pageMetaPath(Path htmlPath) {
        String name = htmlPath.getFileName().toString().replace(".html", ".meta.json");
        return htmlPath.getParent().resolve(name);
    }
}

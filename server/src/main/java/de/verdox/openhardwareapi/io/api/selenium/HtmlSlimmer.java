package de.verdox.openhardwareapi.io.api.selenium;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * HTML Slimmer:
 * - Entfernt "schwere" Tags (script/style/noscript/template/svg/canvas/iframe,...)
 * - Entfernt Kommentare
 * - Behaelt Klassen/IDs und sinnvolle Attribute (href/src/alt/title, data-*, aria-*)
 * - Entfernt Inline-Event-Handler (on*) und style (optional)
 * - Macht href/src absolut & strippt Tracking-Parameter (utm_*, _sp, mkcid,...)
 * - Optional: GZIP/Zstd ausgeben
 */
public final class HtmlSlimmer {

    private HtmlSlimmer() {
    }

    public static final class Options {
        /**
         * Welche Tags komplett entfernen?
         */
        public Set<String> removeTags = new HashSet<>(Arrays.asList(
                "script", "style", "noscript", "template", "svg", "canvas", "iframe", "object", "embed"
        ));

        /**
         * Zusätzliche CSS-Selektoren, die entfernt werden (z.B. Prefetch/Preload-Links, Ads-Container)
         */
        public List<String> removeSelectors = new ArrayList<>(Arrays.asList(
                "link[rel~=(?i)preconnect|prefetch|preload|dns-prefetch]",
                "meta[http-equiv]", // meist unnötig fürs Parsen
                "[data-ad], .ad, .ads, [aria-label=advertisement]"
        ));

        /**
         * Sollen inline styles entfernt werden? (empfohlen: true)
         */
        public boolean dropStyleAttribute = true;

        /**
         * Sollen event-Handler (on*) Attribute entfernt werden?
         */
        public boolean dropEventHandlerAttributes = true;

        /**
         * Tracking-Parameter die aus URLs entfernt werden
         */
        public Set<String> trackingParams = new HashSet<>(Arrays.asList(
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "gclid", "gbraid", "wbraid", "mc_cid", "mc_eid", "_sp", "mkcid", "mkrid", "campid", "adgroupid"
        ));

        /**
         * Belassene Attribute zusätzlich zu den „generischen“ unten
         */
        public Map<String, Set<String>> keepAttributesByTag = new HashMap<>();

        /**
         * True = Whitespace in Texten zusammenfalten
         */
        public boolean normalizeWhitespace = true;

        /**
         * prettyPrint deaktivieren, um extra Whitespaces zu vermeiden
         */
        public boolean minifyOutput = true;

        public Options() {
            // Example: für <a> und <img> spezifische wichtige Attrs whitelisten
            keepAttributesByTag.put("a", new HashSet<>(Arrays.asList("href", "name", "title", "rel", "target")));
            keepAttributesByTag.put("img", new HashSet<>(Arrays.asList("src", "srcset", "sizes", "alt", "title", "width", "height", "loading")));
            keepAttributesByTag.put("time", new HashSet<>(Collections.singletonList("datetime")));
            keepAttributesByTag.put("meta", new HashSet<>(Arrays.asList("name", "property", "content", "charset")));
            keepAttributesByTag.put("link", new HashSet<>(Arrays.asList("rel", "href", "as", "type")));
            keepAttributesByTag.put("source", new HashSet<>(Arrays.asList("src", "srcset", "type", "sizes")));
            keepAttributesByTag.put("track", new HashSet<>(Arrays.asList("src", "kind", "srclang", "label", "default")));
            // alles andere bekommt die generische Regel unten (class/id/data-*/aria-* etc.)
        }
    }

    /**
     * Hauptmethode: gibt „geslimmtes“ HTML zurück.
     */
    public static String slimHtml(String html, String baseUrl, Options opts) {
        if (opts == null) opts = new Options();
        Document doc = Jsoup.parse(html, baseUrl, Parser.htmlParser());

        // 1) Kommentare entfernen
        removeComments(doc);

        // 2) Unerwünschte Tags entfernen
        for (String tag : opts.removeTags) {
            doc.select(tag).remove();
        }

        // 3) Selektoren entfernen
        for (String sel : opts.removeSelectors) {
            doc.select(sel).remove();
        }

        // 4) Attribute säubern (aber class/id/data-*/aria-*/href/src/etc. behalten)
        scrubAttributes(doc, opts);

        // 5) href/src absolut + Tracking-Parameter weg
        absolutizeAndDetrackUrls(doc, opts);

        // 6) Whitespace normalisieren
        if (opts.normalizeWhitespace) normalizeWhitespace(doc);

        // 7) Output-Settings
        if (opts.minifyOutput) {
            doc.outputSettings()
                    .prettyPrint(false)
                    .indentAmount(0)
                    .outline(false)
                    .escapeMode(Entities.EscapeMode.base)
                    .charset(StandardCharsets.UTF_8);
        } else {
            doc.outputSettings().prettyPrint(true);
        }

        // Nur <body> ausgeben? Meist ist <html> nützlich fürs Debuggen.
        return doc.outerHtml();
    }

    /**
     * Optional: GZIP-komprimieren.
     */
    public static byte[] gzip(String text, int level /*0..9*/) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Standard GZIPOutputStream hat festen Level; mit JDK Deflater separat setzen:
            Deflater deflater = new Deflater(level, true);
            try (GZIPOutputStream gos = new GZIPOutputStream(baos) {
                {
                    def = deflater;
                } // kleiner Trick, Level übernehmen
            }) {
                gos.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("GZIP failed", e);
        }
    }

    /**
     * Optional: Zstd (benötigt Dependency com.github.luben:zstd-jni).
     */
    public static byte[] zstd(String text, int level /* ~ 3..19 */) {
        try {
            Class<?> zstdClass = Class.forName("com.github.luben.zstd.Zstd");
            return (byte[]) zstdClass
                    .getMethod("compress", byte[].class, int.class)
                    .invoke(null, text.getBytes(StandardCharsets.UTF_8), level);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("zstd-jni not on classpath", e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Zstd failed", e);
        }
    }

    /* ----------------------- intern ----------------------- */

    private static void removeComments(Document doc) {
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof Comment) node.remove();
            }

            @Override
            public void tail(Node node, int depth) {
            }
        }, doc);
    }

    private static void scrubAttributes(Document doc, Options opts) {
        doc.select("*").forEach(el -> {
            // Kopie der Attrs, dann gezielt zurückschreiben
            Attributes original = el.attributes();
            Attributes kept = new Attributes();

            // 1) Generische „immer behalten“-Regeln
            copyIfPresent(original, kept, "id");
            copyIfPresent(original, kept, "class");
            copyIfPresent(original, kept, "name");
            copyIfPresent(original, kept, "type");
            copyIfPresent(original, kept, "value");
            copyIfPresent(original, kept, "title");
            copyIfPresent(original, kept, "alt");

            // href/src immer prüfen (werden später noch absolutiert und bereinigt)
            copyIfPresent(original, kept, "href");
            copyIfPresent(original, kept, "src");
            copyIfPresent(original, kept, "srcset");
            copyIfPresent(original, kept, "sizes");

            // data-* / aria-*
            original.asList().forEach(a -> {
                String key = a.getKey();
                if (key.startsWith("data-") || key.startsWith("aria-")) {
                    kept.put(a);
                }
            });

            // 2) Tag-spezifische Whitelist
            Set<String> whitelist = opts.keepAttributesByTag.get(el.tagName());
            if (whitelist != null) {
                for (String w : whitelist) copyIfPresent(original, kept, w);
            }

            // 3) Unerwünschte entfernen
            if (opts.dropStyleAttribute) kept.remove("style");

            List<Attribute> copy = original.asList();

            if (opts.dropEventHandlerAttributes) {
                copy.forEach(a -> {
                    if(a.getKey().startsWith("on")) {
                        kept.remove(a.getKey());
                    }
                });
            }

            // Zusätzliche Bereinigung: leere Attribute rausschmeißen

            copy.forEach(a -> {
                if(a.getValue().isBlank()) {
                    kept.remove(a.getKey());
                }
            });

            // Ersetzen
            el.clearAttributes();
            kept.asList().forEach(attr -> el.attributes().put(attr));
        });
    }

    private static void copyIfPresent(Attributes from, Attributes to, String key) {
        if (!from.hasKey(key)) return;

        String fromValue = from.get(key);
        if (fromValue.isBlank()) {
            to.put(new Attribute(key, ""));
        }
        else {
            to.put(new Attribute(key, fromValue));
        }
    }

    private static void absolutizeAndDetrackUrls(Document doc, Options opts) {
        // href
        doc.select("[href]").forEach(el -> {
            String abs = el.absUrl("href");
            if (abs == null || abs.isBlank()) abs = el.attr("href");
            String clean = stripTracking(abs, opts.trackingParams);
            el.attr("href", clean);
        });
        // src
        doc.select("[src]").forEach(el -> {
            String abs = el.absUrl("src");
            if (abs == null || abs.isBlank()) abs = el.attr("src");
            String clean = stripTracking(abs, opts.trackingParams);
            el.attr("src", clean);
        });
        // srcset (mehrere URLs)
        doc.select("[srcset]").forEach(el -> {
            String srcset = el.attr("srcset");
            StringBuilder sb = new StringBuilder();
            for (String part : srcset.split(",")) {
                String[] bits = part.trim().split("\\s+", 2);
                String url = bits.length > 0 ? bits[0] : "";
                String descriptor = bits.length > 1 ? bits[1] : "";
                String abs = el.absUrl(url).isBlank() ? url : el.absUrl(url);
                String clean = stripTracking(abs, opts.trackingParams);
                if (sb.length() > 0) sb.append(", ");
                sb.append(clean);
                if (!descriptor.isBlank()) sb.append(" ").append(descriptor);
            }
            el.attr("srcset", sb.toString());
        });
    }

    private static String stripTracking(String url, Set<String> blockedKeys) {
        if (url == null || url.isBlank()) return url;
        try {
            URI u = new URI(url);
            String query = u.getRawQuery();
            if (query == null || query.isBlank()) return u.toString();

            List<NameValue> pairs = parseQuery(query);
            List<NameValue> kept = new ArrayList<>(pairs.size());
            for (NameValue nv : pairs) {
                String keyLower = nv.name.toLowerCase(Locale.ROOT);
                if (keyLower.startsWith("utm_") || blockedKeys.contains(keyLower)) {
                    continue; // drop
                }
                kept.add(nv);
            }
            String newQuery = buildQuery(kept);
            URI rebuilt = new URI(
                    u.getScheme(), u.getAuthority(), u.getPath(),
                    newQuery.isEmpty() ? null : newQuery, u.getFragment()
            );
            return rebuilt.toString();
        } catch (URISyntaxException e) {
            return url; // im Zweifel nicht anfassen
        }
    }

    private static void normalizeWhitespace(Document doc) {
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof TextNode tn) {
                    String collapsed = tn.getWholeText().replaceAll("\\s+", " ");
                    tn.text(collapsed.trim());
                }
            }
        }, doc.body() != null ? doc.body() : doc);
    }

    /* Query utils ohne externe Libs */
        private record NameValue(String name, String value) {
    }

    private static List<NameValue> parseQuery(String raw) {
        List<NameValue> result = new ArrayList<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            String[] nv = pair.split("=", 2);
            String n = urlDecode(nv[0]);
            String v = nv.length > 1 ? urlDecode(nv[1]) : "";
            result.add(new NameValue(n, v));
        }
        return result;
    }

    private static String buildQuery(List<NameValue> pairs) {
        StringBuilder sb = new StringBuilder();
        for (NameValue nv : pairs) {
            if (sb.length() > 0) sb.append('&');
            sb.append(urlEncode(nv.name));
            if (nv.value != null && !nv.value.isEmpty()) {
                sb.append('=').append(urlEncode(nv.value));
            }
        }
        return sb.toString();
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /* --------- simple demo --------- */
    public static void main(String[] args) {
        String html = "<!doctype html><html><head><script>console.log('x')</script></head>"
                + "<body><a href=\"/p?utm_source=x&foo=1\" class=\"btn\" id=\"k\">Link</a>"
                + "<img src=\"/i.jpg\" onload=\"hack()\" style=\"width:100px\" alt=\"x\">"
                + "</body></html>";
        Options o = new Options();
        String out = slimHtml(html, "https://example.com", o);
        System.out.println(out);
    }
}

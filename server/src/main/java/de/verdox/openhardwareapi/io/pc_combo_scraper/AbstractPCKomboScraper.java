package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.io.api.ComponentWebScraper;
import de.verdox.openhardwareapi.io.pc_combo_scraper.cache.SpecStore;
import de.verdox.openhardwareapi.model.HardwareSpec;
import de.verdox.openhardwareapi.util.PoliteHttpGate;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

/**
 * Zwei-Phasen-Scraper für pc-kombo Detailseiten:
 * 1) Alle Detail-/Spec-Seiten werden (parallel & höflich) lokal gecached – Listenseiten bleiben live.
 * 2) Aus dem Cache (Artefakte) werden die eigentlichen Domain-Objekte erzeugt (rein offline).
 * <p>
 * Vorteile:
 * - Bei Netzproblemen kann Phase 1 übersprungen werden; Phase 2 liest direkt aus dem Cache ("offline/stale").
 * - Struktur klar getrennt: Download/Caching vs. Parsing/Mapping.
 * - Wir speichern weiterhin AUSSCHLIESSLICH Spec-/Detailseiten (Map/List + optional HTML) im SpecStore.
 */
public abstract class AbstractPCKomboScraper<HARDWARE extends HardwareSpec> implements ComponentWebScraper<HARDWARE> {

    private final String prefix;
    protected final HardwareSpecService hardwareSpecService;
    private final String baseUrl;
    private final Supplier<HARDWARE> constructor;

    private final SpecStore specStore = new SpecStore(java.nio.file.Path.of("data"), "pc-kombo-parser-v1");
    private final Duration specTtl = Duration.ofDays(60);
    private static final String PARSER_VERSION = "pc-kombo-parser-v1";

    protected String userAgent() {
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36";
    }

    protected int timeoutMs() {
        return (int) Duration.ofSeconds(20).toMillis();
    }

    protected boolean supportsUpdatingExisting() {
        return false;
    }

    protected boolean allowListFallbackFromCache() {
        return true;
    }

    protected Duration listFallbackMaxAge() {
        return null;
    }

    protected boolean skipDownloadIfListFromCache() {
        return true;
    }

    protected PoliteHttpGate politeGate() {
        return new PoliteHttpGate(new PoliteHttpGate.Policy(
                Runtime.getRuntime().availableProcessors(),
                10,
                Duration.ofMillis(0),
                3,
                Duration.ofMillis(600)
        ));
    }

    protected ExecutorService downloadExecutor() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
            Thread t = new Thread(r, "spec-fetcher");
            t.setDaemon(true);
            return t;
        });
    }

    // ------------------ Von Subklassen zu implementieren ------------------

    /**
     * Parses details from scraped data
     */
    protected abstract void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, HARDWARE target);

    /**
     * Updates existing entries from scraped data
     */
    protected void updateExisting(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, HARDWARE target) {
        // optional von Subklassen zu überschreiben
    }

    /**
     * Whether data should be saved
     */
    protected boolean shouldSave(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, HARDWARE target) {
        return true;
    }

    public AbstractPCKomboScraper(String prefix, HardwareSpecService hardwareSpecService, String baseUrl, Supplier<HARDWARE> constructor) {
        this.prefix = prefix;
        this.hardwareSpecService = hardwareSpecService;
        this.baseUrl = baseUrl;
        this.constructor = constructor;
    }

    @Override
    public Set<HARDWARE> scrape(ScrapeListener<HARDWARE> onScrape) throws Throwable {
        List<PcKomboListItem> items;
        boolean listFromCache = false;

        try {
            items = fetchListItems(baseUrl);
            if ((items == null || items.isEmpty()) && allowListFallbackFromCache()) {
                items = listItemsFromSpecCache();
                listFromCache = true;
                ScrapingService.LOGGER.log(Level.INFO, prefix + ": list empty → using cached list (" + items.size() + ")");
            }
        } catch (Exception ex) {
            if (allowListFallbackFromCache()) {
                items = listItemsFromSpecCache();
                listFromCache = true;
                ScrapingService.LOGGER.log(Level.INFO, prefix + ": list fetch failed → using cached list (" + items.size() + "): " + ex.getMessage(), ex);
            } else {
                throw ex;
            }
        }


        if (!(listFromCache && skipDownloadIfListFromCache())) {
            PoliteHttpGate httpGate = politeGate();
            ExecutorService pool = downloadExecutor();
            AtomicInteger done = new AtomicInteger(0);

            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>(items.size());

                for (PcKomboListItem base : items) {
                    List<PcKomboListItem> finalItems = items;
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            var cached = specStore.readByUrl(prefix, base.detailUrl());
                            boolean fresh = cached.isPresent() && PARSER_VERSION.equals(cached.get().meta().parserVersion()) && specStore.isFresh(cached.get(), specTtl);

                            if (fresh) {
                                return;
                            }

                            URI uri = toSafeUri(base.detailUrl());
                            Connection.Response resp = httpGate.run(uri, () -> Jsoup.connect(uri.toString()).userAgent(userAgent()).timeout(timeoutMs()).ignoreHttpErrors(true).followRedirects(true).referrer("https://www.google.com").execute());

                            int status = resp.statusCode();
                            if (status < 200 || status >= 400) {
                                throw new IllegalStateException("HTTP " + status + " for " + base.detailUrl());
                            }

                            String html = resp.body();
                            Document details = Jsoup.parse(html, base.detailUrl());

                            var list = extractSpecsList(details);
                            var specs = extractSpecsMap(list);

                            String producer = specs.getOrDefault("Producer", List.of("")).isEmpty() ? "" : specs.get("Producer").getFirst();
                            specStore.writeByUrl(prefix, base.detailUrl(), base.name(), producer, base.name(), String.valueOf(parseFirstInt("EAN", specs)), specs, list, html);
                        } catch (Exception ex) {
                            ScrapingService.LOGGER.log(Level.WARNING, "Cache download failed for " + base.detailUrl() + " (" + base.name() + "): " + ex.getMessage(), ex);
                        }
                    }, pool));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            } finally {
                downloadExecutorShutdownQuietly(pool);
            }
        } else {
            // Liste kam aus Cache → optional Phase 1 auslassen
            ScrapingService.LOGGER.log(Level.INFO, prefix + ": skip download phase (list came from cache)");
        }

        Set<HARDWARE> result = new HashSet<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            PcKomboListItem base = items.get(i);
            try {
                var specOpt = specStore.readByUrl(prefix, base.detailUrl());
                if (specOpt.isEmpty()) continue; // nichts im Cache

                var spec = specOpt.get();
                var specs = spec.map();
                var list = spec.list();

                String EAN = extractFirstString("EAN", specs);
                String MPN = extractFirstString("MPN", specs);
                String UPC = extractFirstString("UPC", specs);

                boolean knowsHardware = hardwareSpecService.findLightByEanMPNUPCSN(constructor.get().getClass(), EAN, MPN, UPC).isPresent();

                HARDWARE hw;
                if (supportsUpdatingExisting() && knowsHardware) {
                    hw = constructor.get();

                    ExampleMatcher matcher = ExampleMatcher.matchingAny()
                            .withMatcher("EAN", ExampleMatcher.GenericPropertyMatchers.exact().ignoreCase())
                            .withMatcher("EAN", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())

                            .withMatcher("MPN", ExampleMatcher.GenericPropertyMatchers.exact().ignoreCase())
                            .withMatcher("MPN", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())

                            .withMatcher("UPC", ExampleMatcher.GenericPropertyMatchers.exact().ignoreCase())
                            .withMatcher("UPC", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase());

                    hw.setEAN(EAN);
                    hw.setMPN(MPN);
                    hw.setUPC(UPC);

                    Example<HARDWARE> example = Example.of(hw, matcher);
                    hw = hardwareSpecService.findByExample((Class<HARDWARE>) hw.getClass(), example);
                    fillBasicsFromSpecs(base, hw, specs);
                } else if (!knowsHardware) {
                    hw = constructor.get();
                    fillBasicsFromSpecs(base, hw, specs);
                } else {
                    continue; // kennt Hardware und wir updaten nicht → überspringen
                }

                if (!shouldSave(base, specs, list, hw)) continue;

                if (knowsHardware && supportsUpdatingExisting()) {
                    updateExisting(base, specs, list, hw);
                } else {
                    parseDetails(base, specs, list, hw);
                }

                result.add(hw);
                onScrape.onScrape(hw);

            } catch (Exception ex) {
                ScrapingService.LOGGER.log(Level.WARNING, "Error while parsing cached spec for " + base.detailUrl() + " (" + base.name() + ")", ex);
            } finally {
            }
        }
        return result;
    }

    private void downloadExecutorShutdownQuietly(ExecutorService pool) {
        if (pool == null) return;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void fillBasicsFromSpecs(PcKomboListItem base, HARDWARE hw, Map<String, List<String>> specs) {
        hw.setManufacturer(specs.getOrDefault("Producer", List.of("")).isEmpty() ? "" : specs.get("Producer").getFirst());
        if (hw.getEAN() == null) {
            hw.setEAN(extractFirstString("EAN", specs));
        }
        if (hw.getMPN() == null) {
            hw.setMPN(extractFirstString("MPN", specs));
        }
        if (hw.getUPC() == null) {
            hw.setUPC(extractFirstString("UPC", specs));
        }
        if (hw.getModel() == null) {
            hw.setModel(base.name);
        }
        if (!specs.getOrDefault("Year", List.of()).isEmpty()) {
            String year = specs.get("Year").getFirst();
            if (!year.isEmpty()) {
                hw.setLaunchDate(LocalDate.of((int) parseFirstInt("Year", specs), 1, 1));
            }
        }
    }

    // ------------------ Hilfen: Cache-List-Fallback ------------------

    /**
     * Rekonstruiert die Listen-Items aus dem Spec-Cache (meta.json je URL).
     * Erfordert eine Methode im SpecStore: listMetaByPrefix(prefix).
     */
    private List<PcKomboListItem> listItemsFromSpecCache() {
        List<SpecStore.Meta> metas = specStore.listMetaByPrefix(prefix); // <— stelle sicher, dass SpecStore das anbietet
        if (metas == null || metas.isEmpty()) return List.of();

        Duration maxAge = listFallbackMaxAge();
        long now = System.currentTimeMillis();

        return metas.stream().filter(m -> maxAge == null || (now - m.savedAtEpochMs()) <= maxAge.toMillis()).map(m -> new PcKomboListItem((m.name() == null || m.name().isBlank()) ? (m.model() == null ? "unknown" : m.model()) : m.name(), m.url())).distinct().toList();
    }

    // ------------------ Listen-Parsing (nur Name + Detail-URL) ------------------
    protected List<PcKomboListItem> fetchListItems(String url) throws Exception {
        Thread.sleep(1000);
        Document doc = get(url);
        return parseList(doc);
    }


    protected List<PcKomboListItem> parseList(Document doc) {
        List<PcKomboListItem> items = new ArrayList<>();

        for (Element li : doc.select("ol#hardware li[data-jplist-item]")) {
            Element a = li.selectFirst(".column.col-10.col-lg-8.col-sm-12 a[href]");
            if (a == null) a = li.selectFirst("a[href]"); // Fallback
            String detailUrl = a.absUrl("href");
            Element nameEl = a.selectFirst("h5.name");

            String name = nameEl != null ? nameEl.text().trim() : "";

            if (!name.isEmpty() && !detailUrl.isEmpty()) {
                items.add(new PcKomboListItem(name, detailUrl));
            }
        }
        return items;
    }

    // ------------------ SPECS-EXTRAKTION ------------------

    /**
     * Ein Eintrag aus der Specs-Seite (#specs).
     *
     * @param section  z.B. "Model Info", "Dimensions", "Cooling", ...
     * @param label    dt-Text, z.B. "Producer", "MPN", "Supported CPU cooler height"
     * @param itemprop falls dd ein itemprop trägt (brand, mpn, gtin13, ...), sonst null
     * @param key      normalisierter Schlüssel (bevorzugt itemprop, sonst label)
     * @param value    extrahierter Wert als Klartext ("Corsair", "170 mm", "true"/"false" bei Icon)
     */
    public record SpecEntry(String section, String label, String itemprop, String key, String value) {
    }

    /**
     * Liest #specs und verwandelt jedes dt/dd in SpecEntry.
     */
    protected List<SpecEntry> extractSpecsList(Document doc) {
        Element specsRoot = doc.selectFirst("#specs");
        if (specsRoot == null) return Collections.emptyList();

        List<SpecEntry> out = new ArrayList<>();

        for (Element section : specsRoot.select("section.card")) {
            String sectionName = Optional.ofNullable(section.selectFirst(".card-header .card-title")).map(Element::text).map(String::trim).orElse("");

            for (Element dl : section.select(".card-body dl")) {
                for (Element dt : dl.select("dt")) {
                    Element dd = dt.nextElementSibling();
                    if (dd == null || !"dd".equalsIgnoreCase(dd.tagName())) continue;

                    String label = dt.text().trim();
                    String itemprop = dd.hasAttr("itemprop") ? dd.attr("itemprop").trim() : null;
                    String value = extractDdValue(dd);
                    String key = normalizeKey(itemprop != null && !itemprop.isEmpty() ? itemprop : label);

                    out.add(new SpecEntry(sectionName, label, itemprop, key, value));
                }
            }
        }
        return out;
    }

    /**
     * Baut aus der Liste eine Map<label, List<value>>.
     * (Wir nehmen bewusst das Label als Schlüssel, da pc-kombo hier konsistent ist.)
     */
    protected Map<String, List<String>> extractSpecsMap(List<SpecEntry> list) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (SpecEntry e : list) {
            map.computeIfAbsent(e.label(), k -> new ArrayList<>()).add(e.value());
        }
        return map;
    }

    /**
     * dd-Wert robust extrahieren:
     * - Icons (check/stop) → "true"/"false"
     * - Farbkästchen (div[title]) → title
     * - sonst gesamter Text.
     */
    protected String extractDdValue(Element dd) {
        Element icon = dd.selectFirst("i.icon");
        if (icon != null) {
            Set<String> classes = icon.classNames();
            if (classes.contains("icon-check")) return "true";
            if (classes.contains("icon-stop")) return "false";
        }
        Element colorBox = dd.selectFirst("div[title]");
        if (colorBox != null) {
            String title = colorBox.attr("title").trim();
            if (!title.isEmpty()) return title;
        }
        return dd.text().trim();
    }

    protected String normalizeKey(String in) {
        String s = in == null ? "" : in.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        s = s.replaceAll("_+", "_");
        return s;
    }

    // ------------------ HTTP/Hilfsmethoden ------------------
    protected Document get(String url, String waitForCss, Duration maxWait, Duration pollEvery) throws Exception {
        URI uri = toSafeUri(url);
        long deadline = System.nanoTime() + (maxWait == null ? 0 : maxWait.toNanos());
        long pollMs = (pollEvery == null || pollEvery.isZero()) ? 350 : Math.max(150, pollEvery.toMillis());

        Document last = null;
        int attempt = 0;
        do {
            Connection conn = Jsoup.connect(uri.toString())
                    .userAgent(userAgent())
                    .timeout(timeoutMs())          // Verbindungs-/Read-Timeout
                    .maxBodySize(0)                // volle Seite zulassen
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .referrer("https://www.google.com")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8");

            Document doc = conn.get();
            last = doc;

            if (waitForCss == null || waitForCss.isBlank() || doc.selectFirst(waitForCss) != null) {
                return doc; // Selektor gefunden oder kein Wait gewünscht
            }

            // kurzer, höflicher Polling-Schlaf mit leichtem Jitter
            attempt++;
            long sleep = Math.min(1500, pollMs + ThreadLocalRandom.current().nextLong(40, 120));
            Thread.sleep(sleep);
        } while (maxWait != null && System.nanoTime() < deadline);

        return last; // gibt Letztstand zurück, auch wenn Selektor nicht auftauchte
    }

    /**
     * Bequemer Overload: standardmäßig auf '#specs' warten, 6s Budget, 350ms Poll.
     */
    protected Document get(String url) throws Exception {
        return get(url, "#specs", Duration.ofSeconds(6), Duration.ofMillis(350));
    }

    public static URI toSafeUri(String raw) throws Exception {
        URL u = new URL(raw);
        String encodedPath = Arrays.stream(u.getPath().split("/", -1)).map(seg -> {
            try {
                String dec = URLDecoder.decode(seg, StandardCharsets.UTF_8);
                return URLEncoder.encode(dec, StandardCharsets.UTF_8).replace("+", "%20");
            } catch (Exception e) {
                return URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20");
            }
        }).collect(Collectors.joining("/"));

        if (!encodedPath.startsWith("/")) encodedPath = "/" + encodedPath;
        return new URI(u.getProtocol(), null, u.getHost(), u.getPort(), encodedPath, u.getQuery(), u.getRef());
    }

    @Deprecated
    protected static String appendPageParam(String base, int page) {
        return base;
    }

    @Override
    public int getAmountTasks() {
        return 2;
    }

    /**
     * @param name      z.B. "Sapphire Pulse Radeon RX 6600 Gaming"
     * @param detailUrl Link zur Detailseite (Specs)
     */
    public record PcKomboListItem(String name, String detailUrl) {
    }
}

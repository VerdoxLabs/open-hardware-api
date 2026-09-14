# open-hardware-api — AI Agent Encyclopedia

> Module doc. Start at the workspace root [`../AGENTS.md`](../AGENTS.md) for the big picture.
> Java 21 · Spring Boot 3.5.6 · single Maven application module · base package `de.verdox.hwapi`.

## What this service is

A Spring Boot **data hub** for the PC-flipping business. It **scrapes** PC-component specs,
market prices, and benchmarks from several external sources, **normalizes** them into a
PostgreSQL JPA model, **exposes a REST API** (`/api/v1/*`), and **syncs** catalog data out to a
list of downstream "client" nodes (configured in a JSON file). It is the single source of truth
for *what a part is and what it's worth*.

The `server` module contains the complete runnable application, including persistence,
domain models, repositories and internal API clients.

## Module map

| Module | Packaging | Purpose | Runnable? |
|---|---|---|---|
| `server/` | Spring Boot fat jar | **The complete application**: controllers, scraping, price services, scheduling, config, Flyway migrations, JPA entities, repositories, DTOs and internal HTTP clients. Main class `de.verdox.hwapi.OpenHardwareApiApplication`. | **Yes** |

Parent POM: `pom.xml` (groupId `de.verdox.open-hardware-api`, artifactId `service`). Shared
(inherited) deps: `spring-boot-starter-web/-webflux/-data-jpa/-validation/-actuator`,
`selenium-java 4.23.1`, `webdrivermanager 5.8.0`, `jsoup 1.17.2`, `jackson-databind 2.17.2`,
`flyway-core` + `flyway-database-postgresql`, `postgresql`, `h2` (dev/test), `commons-text`,
`lombok`. Server-only: `caffeine`, `commons-csv`, `okio`, `com.ebay.api:feed-sdk`.

---

## Domain model (in `server`)

### Hardware specs — JOINED inheritance
Base `model/HardwareSpec.java`: `@Entity @Inheritance(JOINED) @DiscriminatorColumn(spec_type)`.
Fields: `id`, `manufacturer`, `model`, `EANs` (element-collection), `MPNs` (element-collection),
`pictureUrls` (EAGER element-collection), `launchDate`. `@PrePersist` sanitizes numbers/normalizes
EAN/MPN. Rich `merge(...)` helpers dedupe scraped records.

Concrete subclasses (each `@DiscriminatorValue`, each a `@NamedEntityGraph("X.All")`):
`CPU`, `GPU` (→ `GPUChip` ManyToOne), `GPUChip`, `RAM`, `Storage`, `Motherboard`, `PSU`,
`PCCase`, `CPUCooler`, `Display`, `Fan`. Example: `CPU` has socket, cores/E-P cores, clock, L3, TDP,
and `isCompatibleWith(Motherboard)` via socket. `Fan` (table `fan`) is a case fan as its own
sellable component: `connectorType` (`FanConnectorType`), embedded `FanSpec` (diameter/rpm/count),
`airflowCfm`, `staticPressureMmH2o`, `noiseLevelDB`, `mountPositions` (element-collection), `hasRgb`.

Enums/value objects in `catalog/domain/values/` + `catalog/domain/`: `HardwareTypes` (big enums: `CpuSocket`,
`Chipset`, `RamType`, `PcieVersion`, `CoolerType`, PSU/case/display types, …), `Currency`,
`ItemCondition`, `DimensionsMm`, `FanSpec`, `M2Slot`, `PcieSlot`, `PowerConnector`, `USBPort`.

Repositories in `catalog/persistence/`: `HardwareSpecRepository` (base) + one per type
(`CPURepository`, `GPURepository`, `GPUChipRepository`, `MotherboardRepository`, `RAMRepository`,
`StorageRepository`, `PSURepository`, `PCCaseRepository`, `CPUCoolerRepository`,
`DisplayRepository`).

### Product identity / registry
`identity/ProductIdentity.java` (`product_identity`) → OneToMany `ProductIdentifier`
(`product_identifier`, unique `(type, normalized_value)`). `IdentifierType`: `ASIN, EAN, UPC, GTIN,
MPN, TITLE, TITLE_NORMALIZED`. Service/controller live in `identity/application` and `identity/web`.

### Prices (`pricing/model/`)
- `RemoteActiveListing` (`remote_active_listing`) — a currently-listed item. Unique
  `(market_place_domain, market_place_item_id)`. Fields: uuid (PK), `primaryRegion`
  (`ListingEnums.Country`), marketPlaceName/Domain/ItemID, ean, mpn, title, productManufacturer,
  itemUrl, merchantImageUrl, firstSeenAt, lastSeenAt, stillActive.
- `ListingPricePoint` (`remote_active_listing_price`) — **daily price snapshot** of a listing
  (ManyToOne → listing, unique `(listing_uuid, snapshot_date)`): price, shippingPrice, currency,
  availableQuantity, condition.
- `RemoteSoldItem` (`remote_sold_item`) — completed transactions. **Deterministic UUIDv5**
  (SHA-1) derived from the business key → idempotent upserts.
- `PriceLookupBlock` (`price_lookup_block`) — rate-limit/blocklist per (ean, currency).

Repositories in `pricing/repository/`: `RemoteActiveListingRepository`,
`ListingPricePointRepository`, `RemoteSoldItemRepository` (nested projections),
`PriceLookupBlockRepository`.

### Benchmarks (`benchmark/`)
`entity/BenchmarkResults.java` (`@Inheritance(JOINED)`, discriminator `benchmark_result_type`):
`modelName`, `source` (e.g. "passmark"). Subclasses `CPUBenchmarkResults` (cpuMark/threadMark),
`GPUBenchmarkResults` (g3D/g2D mark). Repositories: `BenchmarkResultRepository` + CPU/GPU.

### Relationships (textual)
```
hardware_spec (base) ─┬─ cpu/gpu/gpuchip/ram/storage/motherboard/psu/pccase/cpucooler/display (1:1 JOINED)
                      ├─ gpu ─(ManyToOne)→ gpuchip
                      └─ element-collections: *_eans, *_mpns, *_picture_urls, *_color
product_identity ─(1:N)→ product_identifier ─(1:N)→ product_identifier_sources
remote_active_listing ─(1:N)→ remote_active_listing_price
benchmark_results ─ cpu_benchmark_results / gpu_benchmark_results
```
Components are cross-linked **logically by EAN/MPN** (no FK between e.g. a sold item and a spec).

---

## REST API (controllers in `server`, base `/api/v1`)

- **`APIHardwareController`** (`/api/v1/specs`) — `HEAD /{type}` (pagination headers, 204),
  `GET /{type}/count`, `GET /{type}` (paged, ETag = `totalElements:lastLaunchDate`, 304,
  `Cache-Control max-age=30`), `GET /byEan/{ean}/{type}`, `GET /types`, `GET /page/{type}/{filter}`.
- **`BenchmarkController`** (`/api/v1/benchmark`) — `GET /cpu?cpuModelName=`, `GET /gpu?gpuCanonicalName=`
  (exact-then-fuzzy match).
- **`APIPricesController`** (`/api/v1/prices/sold`, max bulk 250) — `POST /points` (bulk upload
  → `BulkResult`), `POST /series/fetchActive/bulkByIds`, `POST /series/fetchCompleted/bulkByIds`.
  Inline DTO records `BulkSeriesRequestV2` / `SeriesEntryV2` / `BulkSeriesResponseV2`.
- **`HardwareAPIAdminController`** (`/api/v1/admin`) — `GET /status` (scrape flag/progress),
  `GET /stats`, `POST /scraping/restart` (202/409), `DELETE /hardware` (danger zone), `GET /awin/status`.
- **`ProductRegistryController`** (`/api/v1/product-registry`) — `GET /search/best?q=`,
  `GET /search/aggregated?q=`.
- **Actuator** — `health`, `info` in prod (with k8s probes).

> **Internal API client is ahead of the server.** `server/.../integration/client/` references endpoints the current `server` does not
> implement (the whole `/cluster/prices/*` family, `POST /specs/{type}`, `/specs/{type}/bulk|
> bulkFile|pages`, `GET /prices/sold/series/fetchActive|Completed`, `/prices/sold/avg-current/bulk`).
> These wrappers are used internally for downstream synchronization and are kept in the single server module.

---

## Data sources / integrations (in `server`)

- **eBay** (three mechanisms):
  - *Buy Feed (ITEM) download* — `pricing/application/ebay/EbayFeedPriceService.java`
    (uses `com.ebay.api:feed-sdk`). OAuth **Client Credentials** via `EbayOAuthService`
    (in-memory cached token, refresh 60s before expiry); creds from `EbayFeedProperties`
    (`ebay.feed.*` ← `EBAY_CLIENT_ID`/`EBAY_CLIENT_SECRET`). Downloads zipped feed per
    `EbayMarketplace`, unzips, filters by GTIN, parses TSV, upserts active listings + daily price.
  - *Completed/sold listings* — `EbayCompletedListingsService` (`@Scheduled` every 1 min).
  - *Selenium* — `pricing/sources/ebay/EbayScraper.java`.
  - Marketplaces/enums in `server/.../integration/client/ebay/` (`EbayMarketplace`, `EbayDeveloperAPIClient`, …).
- **Amazon (PA-API 5)** — currently not part of the application. The old commented-out service,
  SDK helper module and unused configuration were removed during the module consolidation.
- **Awin (affiliate CSV)** — `AwinFeedService` (download gzip feed → gunzip → parse CSV),
  `AwinProductFeedParser`, `AwinFeedOverviewService`, `AwinTrackActiveListingsService`
  (`@Scheduled(cron="0 0 * * * *")` hourly). Admin: `controllerapi/AwinAdminService`.
- **Passmark (benchmarks)** — `benchmark/PassmarkDataScraper` (Selenium),
  `BenchmarkService` (`@Scheduled(fixedRate=7, TimeUnit.DAYS)`), upserts by `(modelName, source)`.
- **Selenium site scrapers** — framework in `catalog/ingestion/`: `api/ComponentWebScraper`
  (core interface), `api/WebsiteScraper` (fluent DSL), `api/ScrapeParser` (declarative field
  parser, DE/EN number normalization), `api/WebsiteCatalogScraper`, `api/WebsiteScrapingStrategy`
  + per-site strategies, `api/selenium/SeleniumBasedWebScraper` (+ `SeleniumUtil` reads
  `SELENIUM_REMOTE_URL`, default `http://localhost:4444`). Site scrapers in
  `scraping/websites/`: `intel/`, `amd/AmdCpuCsvImporter` (imports bundled specs.csv),
  `pc_builder_io/`, `pc_kombo/`, `dbgpu/`. `pcpartpicker/` exists but is **not registered**.
  Orchestration: `scraping/ScrapingService` (builds scraper list, runs AMD CSV import + each web
  scraper, registers products, pushes specs to `HardwareSyncService`).
- **Selectable scraping backend** — `hwapi.scraping.backend=selenium|webscraper` keeps the existing
  Jsoup/site parsers but selects how HTML is rendered. `selenium` uses the legacy RemoteWebDriver;
  `webscraper` calls the Webscraper API `POST /v1/raw` endpoint with API-key authentication.

---

## Database

PostgreSQL. **Flyway migrations in `server/src/main/resources/db/migration/`** (V1…V15).
Dev = H2 file DB + `ddl-auto: update` + Flyway **disabled** (`locations: classpath:db/h2`);
prod = Postgres + Flyway **enabled** + `ddl-auto: validate`.

Timeline: **V1** full base schema (sequences + all component tables + element-collection tables +
FKs + `remote_sold_item`); **V2** benchmarks + `hardware_spec_picture_urls`; **V3**
`remote_active_listing` + `price_lookup_block` + sold-item condition; **V4** no-op; **V5**
sequence; **V6** cooler/fan cols + **split price into `remote_active_listing_price`** (daily
snapshots); **V7** type fixes; **V8** product-registry tables; **V9** currency/shipping/quantity
on price points; **V10** `spec_color` (transitional); **V11** per-type `*_color` tables; **V12**
`product_manufacturer`; **V13** `market_place_name`; **V14/V15** `primary_region` (default 'DE' +
backfill).

Data source config: prod via `SPRING_DATASOURCE_URL` (default `jdbc:postgresql://db:5432/pcparts`),
`SPRING_DATASOURCE_USERNAME/PASSWORD` (default `pcparts`), Hikari pool, `PostgreSQLDialect`.
Common `application.yml`: Hikari max 30, Hibernate batch tuning (`batch_size=50`,
`order_inserts/updates`, `default_batch_fetch_size=64`), `open-in-view: false`.

---

## Scheduled jobs

`@EnableScheduling` on the app + `configuration/SchedulingConfig` (scheduler pool 2, `jobExecutor`
core=CPUs/max=2×CPUs queue 200 `CallerRunsPolicy`). `@EnableAsync` on.

| Class · method | Trigger | Does |
|---|---|---|
| `ScrapingService.runDailyJob` | `cron 0 0 2 * * *` Europe/Berlin (+ on boot) | Full scrape cycle |
| `BenchmarkService.updateDatabase` | `fixedRate 7 days` | Re-scrape Passmark CPU/GPU |
| `HardwareSyncService.scheduledFlush` | `fixedDelay ${sync.flush-interval-ms}` (60s) | Flush spec buffer to downstream clients |
| `ItemPriceService.runBackgroundFetcher` | `fixedDelay ${sync.flush-interval-ms}` | Drain "fetch if no data" queue |
| `PricePointSyncService` (scheduled) | `fixedDelay ${sync.flush-interval-ms}` | Price-point sync/flush |
| `RemoteActiveListingWriterService` | `fixedDelay ${hwapi.specLinkFlush.delayMs:1500}` | Async upsert writer |
| `EbayCompletedListingsService` | `fixedDelay 1 min` | Poll eBay completed listings |
| `AwinTrackActiveListingsService` | `cron 0 0 * * * *` (hourly) | Track active Awin listings |

> `sync.flush-interval-ms` = **60000** in `application.yml`, overriding the `:5000` code defaults.

---

## Configuration & environment

- **`.env`** (module root): `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET` (**trailing `;` on both lines —
  parsing hazard**).
- **`application.yml`** (common): `server.port` `${SERVER_PORT:${PORT:8080}}`, bind `0.0.0.0`,
  json/ndjson compression, `sync.*` (flush-interval 60000, flush-threshold 100, bulk max-batch 200),
  Hibernate batch tuning, `ebay.feed.*`
  (client-id/secret from env, category-id default `58058` = PC components, download-dir
  default `/tmp/ebay-feeds`, enabled-marketplaces GERMANY/AUSTRIA/SWITZERLAND/USA/UK overridable
  via `EBAY_FEED_MARKETPLACE_1..5`), logging levels.
- **Other env vars:** `SELENIUM_REMOTE_URL` (default `http://localhost:4444`; compose sets
  `http://selenium:4444/wd/hub`), `HWAPI_SCRAPER_BACKEND` (`selenium` default or `webscraper`),
  `WEBSCRAPER_API_URL` (default `http://localhost:8090`), `WEBSCRAPER_API_KEY`,
  `WEBSCRAPER_ENGINE` (`auto` default), `WEBSCRAPER_TIMEOUT`, `SPRING_PROFILES_ACTIVE`.

---

## Build & run

- **Build:** `mvn -f open-hardware-api/pom.xml clean package -DskipTests` (or `mvn -pl server
  package`). The Dockerfile invokes `./mvnw clean package` with build context set to the repository
  root, where the Maven wrapper is checked in. Java 21.
- **Run:** fat jar from `server/target/*.jar`. Dev: `SPRING_PROFILES_ACTIVE=dev` (H2); scraping
  needs Selenium reachable. Prod: `SPRING_PROFILES_ACTIVE=prod` + Postgres + Selenium env.
- **Tests:** `mvn test` (h2 runtime; CI skips tests).
- **CI:** `.github/workflows/verdox-repo-publish.yml` — on **push to `master`**: setup Java 21 +
  Maven (settings from `secrets.REPO_USER`/`REPO_PASSWORD`), then `mvn -B -U clean deploy
  -DskipTests` → publishes SNAPSHOTs to `https://repo.verdox.de/snapshots`.
- **Docker:** `Dockerfile` (multi-stage temurin 21 → jre-alpine, non-root uid 1000, volume
  `/var/lib/open-hardware-api`, `SPRING_PROFILES_ACTIVE=prod`, JMX). `docker-compose.yml`:
  `db` (postgres:16, `pcparts`), `selenium` (legacy fallback), `webscraper-db`,
  `webscraper-camoufox`, `webscraper-api` (built from sibling `../web-scraper-api`, host
  `8090`→`8080`), and `app` (host `5050`→`8080`). The app defaults to the Webscraper API;
  set `HWAPI_SCRAPER_BACKEND=selenium` to select the legacy backend.

---

## Gotchas (read before editing)

1. **The application is intentionally single-module.** All production Java sources live under
   `server/src/main/java`; do not recreate `client`, `spring-db` or the old duplicate `price-api`
   modules without a concrete deployment requirement.
2. **Amazon price fetching is currently not included.** Runtime price sources are
   eBay/Awin/Kleinanzeigen only.
3. **Lombok everywhere**; mixed slf4j / `java.util.logging` (`ScrapingService.LOGGER` is a shared
   `java.util.logging.Logger` used across scraping classes).
6. **JOINED inheritance + two-repo save pattern:** `HardwareSpecService.saveHardware/...Batch`
   saves to base repo AND type-specific repo, dedupes/merges by EAN/MPN, updates
   `HardwareSpecCache` **after commit**; deletes child repos then base. Follow this when adding a
   component type.
7. **Adding a new hardware type** touches: `model/X.java`, a `XRepository`,
   `HardwareTypeUtil.getSupportedSpecTypes()`, `HardwareSpecService` constructor + `repoByType`,
   a scraper, and a Flyway migration.
8. **Deterministic IDs:** `RemoteSoldItem.uuid` is hand-rolled UUIDv5 from its business key →
   idempotent upserts. Don't switch to `@GeneratedValue` without breaking that.
9. **`DataStorage` path rules** (`server/.../infrastructure/storage/DataStorage`): Linux →
   `/var/lib/open-hardware-api/<sub>`, else (Windows) → `./open-hardware-api/<sub>`. This is why a
   nested `open-hardware-api/open-hardware-api/` runtime-data folder exists (scrape cache,
   `synchronization.json`, `ebay_api_config.json`, `awin-feeds/`). **Runtime state is file-based.**
10. **Config stores are JSON files, not DB:** `SynchronizationConfig` (`synchronization.json` =
    list of downstream client URLs) and `EbayAPIConfig` (`ebay_api_config.json`) — both
    `@Component` with `@PostConstruct` atomic-write + `ReentrantReadWriteLock`.
11. **EAN/MPN normalization is central** (`HardwareSpec.normalizeEan` strips non-digits,
    UPC-A→EAN-13, accepts 13/14; `normalizeMpn` NFKC/uppercase). Keep consistent in new lookups.
12. **Type strings are lowercase simple class names** (`cpu`, `gpu`, `ram`, …) via
    `HardwareTypeUtil` — used in REST `/specs/{type}`.
13. **Selenium is remote by default** (`SELENIUM_REMOTE_URL`); scraping jobs silently fail/log
    without a reachable Grid.
14. **Comments/logs largely in German**; identifiers English.
15. **`.env` trailing semicolons** on the EBAY lines.
16. **Repository package is `de.verdox.hwapi.catalog.persistence`**.

### Quick file index (module-relative)
- App: `server/src/main/java/de/verdox/hwapi/OpenHardwareApiApplication.java`
- Config: `server/.../configuration/{SchedulingConfig,SynchronizationConfig,CorsCfg,ApiExceptionHandler,ApiJacksonConfig}.java`
- Scraping: `server/.../catalog/ingestion/ScrapingService.java`, `ingestion/api/**`, `ingestion/websites/**`
- Specs: `server/.../catalog/application/{HardwareSpecService,HardwareSyncService,HardwareSpecCache}.java`
- Benchmarks: `server/.../benchmark/{BenchmarkController,BenchmarkService,PassmarkDataScraper}.java`
- Prices: `server/.../pricing/web/APIPricesController.java`, `.../application/**` (ebay/awin/kleinanzeigen + ItemPriceService + writers)
- Admin/registry: `server/.../admin/**`, `server/.../identity/**`
- Entities/repos: `server/.../catalog/domain/**`, `server/.../pricing/**`, `server/.../benchmark/**`, `server/.../identity/**`, `server/.../catalog/persistence/**`
- Internal API client: `server/.../integration/client/**`
- Migrations: `server/src/main/resources/db/migration/V1__.sql`…`V18__.sql`
- Infra: `pom.xml`, `*/pom.xml`, `Dockerfile`, `docker-compose.yml`, `.env`, `.github/workflows/verdox-repo-publish.yml`

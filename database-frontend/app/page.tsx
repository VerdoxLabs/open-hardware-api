"use client";
/* eslint-disable react-hooks/set-state-in-effect -- polling synchronizes the UI with the Spring API */
import {useCallback, useEffect, useState} from "react";

type Tab = "overview" | "database" | "scraper" | "awin";
type Stats = {
    totalHardware: number;
    hardwareWithPriceTracking: number;
    totalPricePoints: number;
    totalListingsTracked: number;
    typesCount: number;
    lastScrapeAt?: string
};
type BackendStatus = {
    currentlyDeleting: boolean;
    scrapingRunning: boolean;
    progress01?: number;
    message?: string;
    startedAt?: string;
    lastFinishedAt?: string;
    scrapers?: ScraperStatus[]
};
type ScraperStatus = {
    id: string;
    baseUrl: string;
    running: boolean;
    currentUrl?: string;
    processedPages: number;
    estimatedPages: number;
    message?: string;
    lastError?: string;
    startedAt?: string;
    finishedAt?: string
};
type AwinStatus = {
    running: boolean;
    nextRunAt?: string;
    startedAt?: string;
    lastFinishedAt?: string;
    message?: string;
    lastError?: string;
    currentFeedAdvertiser?: string;
    currentFeedRegion?: string;
    currentFeedIndex: number;
    totalFeeds: number;
    currentFeedProcessed: number;
    currentFeedTotal: number;
    overallProcessed: number;
    overallTotal: number
};
type Feed = { advertiser: string; feedUrlDownloadLink: string; primaryRegion: string; language: string };
type Hardware = Record<string, unknown> & {
    id?: number;
    manufacturer?: string;
    modelName?: string;
    name?: string;
    specType?: string;
    mpns?: string[];
    eans?: string[];
    pictureUrls?: string[];
    displayPictureUrl?: string
};
type SearchPage = { content: Hardware[]; totalElements: number; totalPages: number; number: number; size: number };
const tabs: { id: Tab; label: string; icon: string }[] = [{
    id: "overview",
    label: "Übersicht",
    icon: "◈"
}, {id: "database", label: "Datenbank", icon: "⌕"}, {id: "scraper", label: "Scraper", icon: "↻"}, {
    id: "awin",
    label: "Awin Feed",
    icon: "↗"
}];
const num = (n?: number) => new Intl.NumberFormat("de-DE").format(n ?? 0);
const date = (d?: string) => d ? new Intl.DateTimeFormat("de-DE", {
    dateStyle: "medium",
    timeStyle: "short"
}).format(new Date(d)) : "—";

async function api<T>(url: string, init?: RequestInit): Promise<T> {
    const r = await fetch(url, {
        ...init,
        headers: {Accept: "application/json", ...(init?.headers ?? {})},
        cache: "no-store"
    });
    if (!r.ok) throw Error(`API ${r.status}`);
    return r.json()
}

export default function Home() {
    const [tab, setTab] = useState<Tab>("overview"), [stats, setStats] = useState<Stats>(), [status, setStatus] = useState<BackendStatus>(), [awin, setAwin] = useState<AwinStatus>(), [error, setError] = useState("");
    const refresh = useCallback(async () => {
        try {
            const [s, st, a] = await Promise.all([api<Stats>("/api/v1/admin/stats"), api<BackendStatus>("/api/v1/admin/status"), api<AwinStatus>("/api/v1/admin/awin/status")]);
            setStats(s);
            setStatus(st);
            setAwin(a);
            setError("")
        } catch {
            setError("Die API ist momentan nicht erreichbar.")
        }
    }, []);
    const pollScraper = useCallback(async () => {
        try {
            setStatus(await api<BackendStatus>("/api/v1/admin/status"));
        } catch {
            // The regular refresh owns the global offline indicator.
        }
    }, []);
    const pollAwin = useCallback(async () => {
        try {
            setAwin(await api<AwinStatus>("/api/v1/admin/awin/status"));
        } catch {
            // The regular refresh owns the global offline indicator.
        }
    }, []);
    useEffect(() => {
        refresh();
        const t = window.setInterval(refresh, 15000);
        return () => window.clearInterval(t)
    }, [refresh]);
    useEffect(() => {
        pollScraper();
        const t = window.setInterval(pollScraper, 3000);
        return () => window.clearInterval(t)
    }, [pollScraper]);
    useEffect(() => {
        pollAwin();
        const t = window.setInterval(pollAwin, 3000);
        return () => window.clearInterval(t)
    }, [pollAwin]);
    return <div className="shell">
        <aside className="sidebar">
            <div className="brand">
                <div className="brand-mark">OH</div>
                <div><strong>Open Hardware</strong><span>Data Console</span></div>
            </div>
            <div className="nav-label">ARBEITSBEREICH</div>
            <nav>{tabs.map(x => <button key={x.id} className={tab === x.id ? "nav-item active" : "nav-item"}
                                        onClick={() => setTab(x.id)}><span
                className="nav-icon">{x.icon}</span>{x.label}{x.id === "scraper" && status?.scrapingRunning &&
                <i className="nav-pulse"/>}</button>)}</nav>
            <div className="sidebar-bottom">
                <div className="api-status"><span
                    className={error ? "status-dot danger" : "status-dot"}/>{error ? "API offline" : "API verbunden"}
                </div>
                <small>Spring Boot · /api/v1</small></div>
        </aside>
        <main className="main">
            <header className="topbar">
                <div><p className="eyebrow">OPEN HARDWARE API</p><h1>{tabs.find(x => x.id === tab)?.label}</h1></div>
                <div className="top-actions"><span className="live"><span className="live-dot"/>Live-Daten</span>
                    <button className="icon-button" onClick={refresh}>↻</button>
                </div>
            </header>
            {error && <div className="alert danger">{error}
                <button onClick={refresh}>Erneut versuchen</button>
            </div>}{tab === "overview" &&
            <Overview stats={stats} status={status} awin={awin} go={setTab}/>} {tab === "database" &&
            <Database/>}{tab === "scraper" && <Scraper status={status} refresh={refresh}/>} {tab === "awin" &&
            <Awin awin={awin}/>}</main>
    </div>
}

function Overview({stats, status, awin, go}: {
    stats?: Stats;
    status?: BackendStatus;
    awin?: AwinStatus;
    go: (t: Tab) => void
}) {
    return <section className="content">
        <div className="hero">
            <div><span className="kicker">SYSTEM MONITOR</span><h2>Dein Datenhub auf einen Blick.</h2><p>Hardware,
                Preise und externe Feeds — zentral überwacht und direkt aus der Spring API.</p></div>
            <div className="hero-orb">◌</div>
        </div>
        <div className="metrics"><Metric l="Hardware-Komponenten" v={stats?.totalHardware}
                                         n={`${stats?.typesCount ?? 0} Komponententypen`}/><Metric l="Preis-Punkte"
                                                                                                   v={stats?.totalPricePoints}
                                                                                                   n={`${num(stats?.hardwareWithPriceTracking)} mit Preisverlauf`}/><Metric
            l="Aktive Listings" v={stats?.totalListingsTracked} n="Awin & eBay"/><Metric l="Scraper-Status"
                                                                                         v={status?.scrapingRunning ? "Läuft" : "Bereit"}
                                                                                         n={status?.lastFinishedAt ? `Zuletzt ${date(status.lastFinishedAt)}` : "Noch kein Lauf"}
                                                                                         tone={status?.scrapingRunning ? "blue" : "green"}/>
        </div>
        <div className="grid-two"><Panel title="Datenbank" action="Öffnen" go={() => go("database")}>
            <div className="mini-chart">
                <div className="chart-bars">{[42, 58, 48, 72, 65, 84, 76, 92, 81, 100, 88, 96].map((h, i) => <span
                    key={i} style={{height: `${h}%`}}/>)}</div>
                <div><strong>{num(stats?.totalHardware)}</strong><small>Einträge verfügbar</small></div>
            </div>
        </Panel><Panel title="Aktive Prozesse" action="Details" go={() => go("scraper")}><Process l="Hardware Scraper"
                                                                                                  running={status?.scrapingRunning}
                                                                                                  d={status?.message || "Wartet auf nächsten Lauf"}/><Process
            l="Awin Feed Import" running={awin?.running} d={awin?.message || "Wartet auf nächsten Lauf"}/></Panel></div>
        <div className="section-heading"><span className="kicker">RECENT ACTIVITY</span><h3>System-Aktivität</h3></div>
        <div className="activity-list"><Activity t="Datenbank synchronisiert"
                                                 d={stats?.lastScrapeAt ? date(stats.lastScrapeAt) : "Keine Synchronisierung bekannt"}
                                                 i="✓"/><Activity t="Awin Feed Scheduler"
                                                                  d={awin?.nextRunAt ? `Nächster Lauf ${date(awin.nextRunAt)}` : "Stündlicher Lauf"}
                                                                  i="↗"/><Activity t="API Health Check"
                                                                                   d="Alle Kernendpunkte registriert"
                                                                                   i="⌁"/></div>
    </section>
}

function Metric({l, v, n, tone}: { l: string; v?: number | string; n: string; tone?: string }) {
    return <div className="metric"><span>{l}</span><strong
        className={tone ? `tone-${tone}` : ""}>{typeof v === "number" ? num(v) : v ?? "—"}</strong><small>{n}</small>
    </div>
};

function Panel({title, action, go, children}: {
    title: string;
    action: string;
    go: () => void;
    children: React.ReactNode
}) {
    return <div className="panel">
        <div className="panel-head"><h3>{title}</h3>
            <button onClick={go}>{action} <span>→</span></button>
        </div>
        {children}</div>
};

function Process({l, running, d}: { l: string; running?: boolean; d: string }) {
    return <div className="process"><span
        className={running ? "process-icon running" : "process-icon"}>{running ? "↻" : "✓"}</span>
        <div><strong>{l}</strong><small>{d}</small></div>
        <em className={running ? "badge running" : "badge"}>{running ? "Aktiv" : "Bereit"}</em></div>
};

function Activity({t, d, i}: { t: string; d: string; i: string }) {
    return <div className="activity"><span className="activity-icon">{i}</span>
        <div><strong>{t}</strong><small>{d}</small></div>
        <span className="activity-arrow">→</span></div>
}

function Database() {
    const [q, setQ] = useState(""), [type, setType] = useState(""), [manufacturer, setManufacturer] = useState(""), [identifier, setIdentifier] = useState(""), [sort, setSort] = useState("name"), [types, setTypes] = useState<string[]>([]), [rows, setRows] = useState<Hardware[]>([]), [page, setPage] = useState(0), [totalPages, setTotalPages] = useState(0), [totalElements, setTotalElements] = useState(0), [loading, setLoading] = useState(false), [searched, setSearched] = useState(false), [selected, setSelected] = useState<Hardware>(), [backupMessage, setBackupMessage] = useState(""), [importing, setImporting] = useState(false);
    useEffect(() => {
        api<string[]>("/api/v1/specs/types").then(setTypes).catch(() => undefined)
    }, []);
    const search = async (targetPage = 0) => {
        setLoading(true);
        try {
            const params = new URLSearchParams({q, manufacturer, identifier, page: String(targetPage), size: "25", sort});
            if (type) params.set("type", type);
            const result = await api<SearchPage>(`/api/v1/specs/search/page?${params}`);
            setRows(result.content);
            setPage(result.number);
            setTotalPages(result.totalPages);
            setTotalElements(result.totalElements);
            setSearched(true)
        } finally {
            setLoading(false)
        }
    };
    const display = (r: Hardware) => String(r.model || r.name || r.modelName || r.displayName || r.title || r.id || "Unbenannt");
    const importBackup = async (file?: File) => {
        if (!file) return;
        setImporting(true);
        setBackupMessage("");
        try {
            const body = new FormData();
            body.append("file", file);
            const response = await fetch("/api/v1/admin/backups/hardware/import", {method: "POST", body});
            const result = await response.json();
            if (!response.ok) throw Error(result.detail || "Import fehlgeschlagen.");
            setBackupMessage(`${result.importedEntities} Hardware-Entities importiert. Vorhandene Einträge wurden zusammengeführt.`);
            void search();
        } catch (err) {
            setBackupMessage(err instanceof Error ? err.message : "Import fehlgeschlagen.");
        } finally {
            setImporting(false);
        }
    };
    const picture = (r: Hardware) => {
        const urls = Array.isArray(r.pictureUrls) ? r.pictureUrls : [];
        const url = String(r.displayPictureUrl || urls.find(x => !String(x).includes("noimage")) || "");
        return url.startsWith("http") && !url.includes("noimage") ? url : undefined;
    };
    useEffect(() => {
        void search();
        // The initial catalog load intentionally runs once when the tab opens.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    return <section className="content">
        <div className="page-intro">
            <div><span className="kicker">CATALOG EXPLORER</span><h2>Hardware-Datenbank</h2><p>Durchsuche Komponenten,
                Identifier, Preise und Benchmarks.</p></div>
            <div className="backup-actions"><a className="backup-button" href="/api/v1/admin/backups/hardware">↓ Backup exportieren</a><label className="backup-button import">↑ {importing ? "Import läuft …" : "Backup importieren"}<input type="file" accept="application/zip,.zip" disabled={importing} onChange={e => { void importBackup(e.target.files?.[0]); e.currentTarget.value = ""; }}/></label><span className="count-pill">{searched ? `${num(totalElements)} Treffer` : "Bereit zur Suche"}</span></div></div>
        {backupMessage && <div className={backupMessage.includes("importiert") ? "alert inline-alert" : "alert danger inline-alert"}>{backupMessage}</div>}
        <div className="search-box"><span>⌕</span><input value={q} onChange={e => setQ(e.target.value)}
                                                         onKeyDown={e => e.key === "Enter" && search()}
                                                         placeholder="Suche nach Modell, MPN oder EAN …"/><input value={manufacturer} onChange={e => setManufacturer(e.target.value)} placeholder="Hersteller …"/><input value={identifier} onChange={e => setIdentifier(e.target.value)} placeholder="MPN / EAN …"/><select
            value={type} onChange={e => setType(e.target.value)}>
            <option value="">Alle Typen</option>
            {types.map(x => <option key={x}>{x}</option>)}</select>
            <select value={sort} onChange={e => setSort(e.target.value)}><option value="name">Name A–Z</option><option value="id">Neueste ID</option></select><button onClick={() => search()} disabled={loading}>{loading ? "Suche …" : "Suchen"}</button>
        </div>
        {!searched ? <div className="empty">
            <div className="empty-icon">⌕</div>
            <h3>Hardware-Datenbank wird geladen …</h3><p>Die erste Seite der verfügbaren Komponenten wird geladen.</p></div> : <div className="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>Komponente</th>
                    <th>Typ</th>
                    <th>Identifier</th>
                    <th>Details</th>
                </tr>
                </thead>
                <tbody>{rows.map((r, i) => <tr key={String(r.id ?? i)}>
                    <td>
                        <div className="hardware-cell">
                            <div className="hardware-thumbnail">
                                {picture(r) ? <img src={picture(r)} alt={display(r)}/> : <span aria-label="Kein Produktbild verfügbar">◈</span>}
                            </div>
                            <div className="hardware-name"><strong>{display(r)}</strong><small>{String(r.manufacturer || "Hersteller nicht angegeben")}</small></div>
                        </div>
                    </td>
                    <td><span className="type-tag">{String(r.specType || "—")}</span></td>
                    <td><small>{[...(r.mpns || []), ...(r.eans || [])].slice(0, 3).join(" · ") || "—"}</small></td>
                    <td>
                        <button className="row-action" onClick={() => setSelected(r)}>Ansehen →</button>
                    </td>
                </tr>)}</tbody>
            </table>
            {rows.length === 0 &&
                <div className="empty compact"><h3>Keine Treffer</h3><p>Versuche eine andere Suche oder entferne den
                    Filter.</p></div>}
            {totalPages > 1 && <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 18px", borderTop: "1px solid var(--line)"}}><button className="row-action" disabled={page === 0 || loading} onClick={() => search(page - 1)}>← Zurück</button><span className="muted">Seite {page + 1} von {totalPages}</span><button className="row-action" disabled={page + 1 >= totalPages || loading} onClick={() => search(page + 1)}>Weiter →</button></div>}</div>}
        {selected && <div role="dialog" aria-modal="true" onClick={() => setSelected(undefined)} style={{position: "fixed", inset: 0, zIndex: 20, background: "rgba(21,35,59,.45)", display: "grid", placeItems: "center", padding: 20}}>
            <div onClick={e => e.stopPropagation()} style={{background: "#fff", borderRadius: 14, width: "min(760px, 100%)", maxHeight: "90vh", overflow: "auto", padding: 26, boxShadow: "0 20px 70px rgba(21,35,59,.25)"}}>
                <div style={{display: "flex", justifyContent: "space-between", gap: 20, alignItems: "start"}}>
                    <div><span className="kicker">HARDWARE-DETAILS</span><h2 style={{margin: "9px 0 4px"}}>{display(selected)}</h2><p style={{color: "var(--muted)", fontSize: 12, margin: 0}}>{String(selected.specType || "Komponente")}</p></div>
                    <button className="icon-button" onClick={() => setSelected(undefined)} aria-label="Schließen">×</button>
                </div>
                {picture(selected) && <img src={picture(selected)} alt={display(selected)} style={{display: "block", width: "100%", height: 220, objectFit: "contain", margin: "22px 0", background: "#f7f9fc", borderRadius: 10}}/>}
                <div className="info-grid"><Info l="Hersteller" v={String(selected.manufacturer || "—")}/><Info l="MPN" v={(selected.mpns || []).join(", ") || "—"}/><Info l="EAN" v={(selected.eans || []).join(", ") || "—"}/></div>
                <div style={{marginTop: 22}}><span className="kicker">TECHNISCHE DATEN</span><div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10, marginTop: 12}}>{Object.entries(selected).filter(([key, value]) => !["id", "manufacturer", "model", "displayName", "pictureUrls", "displayPictureUrl", "mpns", "eans"].includes(key) && value !== null && value !== undefined && typeof value !== "object").map(([key, value]) => <div key={key} style={{padding: "10px 12px", background: "#f7f9fc", borderRadius: 8}}><small style={{display: "block", color: "var(--muted)", fontSize: 10}}>{key}</small><strong style={{fontSize: 12}}>{String(value)}</strong></div>)}</div></div>
            </div>
        </div>}
    </section>
}

function Scraper({status, refresh}: { status?: BackendStatus; refresh: () => void }) {
    const [msg, setMsg] = useState("");
    const restart = async () => {
        try {
            const r = await api<{ message: string }>("/api/v1/admin/scraping/restart", {method: "POST"});
            setMsg(r.message);
            refresh()
        } catch {
            setMsg("Scraper läuft bereits oder konnte nicht gestartet werden.")
        }
    };
    const progress = Math.round((status?.progress01 ?? 0) * 100);
    return <section className="content">
        <div className="page-intro">
            <div><span className="kicker">INGESTION PIPELINE</span><h2>Scraper-Zentrale</h2><p>Überwache laufende Jobs
                und starte einen vollständigen Datenlauf.</p></div>
            <button className="primary-button" onClick={restart}
                    disabled={status?.scrapingRunning}>↻ {status?.scrapingRunning ? "Läuft gerade" : "Scraper starten"}</button>
        </div>
        <div className="scraper-card">
            <div className="scraper-state"><span
                className={status?.scrapingRunning ? "big-status active" : "big-status"}>{status?.scrapingRunning ? "↻" : "✓"}</span>
                <div><span className="kicker">AKTUELLER STATUS</span>
                    <h3>{status?.scrapingRunning ? "Scraper läuft" : "Scraper ist bereit"}</h3>
                    <p>{status?.message || "Kein aktiver Lauf. Der nächste geplante Lauf wird automatisch ausgeführt."}</p>
                </div>
            </div>
            {status?.scrapingRunning && <>
                <div className="progress-meta"><span>Fortschritt</span><strong>{progress}%</strong></div>
                <div className="progress"><span style={{width: `${progress}%`}}/></div>
            </>}
            <div className="info-grid"><Info l="Gestartet" v={date(status?.startedAt)}/><Info l="Letzter Abschluss"
                                                                                              v={date(status?.lastFinishedAt)}/><Info
                l="Fehler" v="Über API-Status sichtbar"/></div>
        </div>
        {msg && <div className="alert">{msg}</div>}
        <div className="panel scraper-processes">
            <div className="panel-head"><h3>Parallele Scraper-Prozesse</h3><span className="muted">Live-Aktualisierung alle 3 Sekunden</span></div>
            <div className="scraper-process-list">
                {(status?.scrapers || []).map(scraper => <ScraperProcess key={scraper.id} scraper={scraper}/>)}</div>
            {!status?.scrapers?.length && <div className="empty compact"><h3>Noch keine Prozessdaten</h3><p>Die API liefert die einzelnen Scraper-Prozesse beim nächsten Lauf.</p></div>}
        </div>
        <div className="panel">
            <div className="panel-head"><h3>Fehlerbehandlung</h3><span className="muted">Live aus Backend-Status</span>
            </div>
            <div className="health-row"><span className="health-icon">!</span>
                <div><strong>Probleme werden im Laufstatus gemeldet</strong><small>Bei Fehlern bleibt die API erreichbar
                    und liefert die letzte Statusmeldung.</small></div>
            </div>
        </div>
    </section>
};

function Info({l, v}: { l: string; v: string }) {
    return <div><small>{l}</small><strong>{v}</strong></div>
}

function ScraperProcess({scraper}: { scraper: ScraperStatus }) {
    const progress = scraper.estimatedPages > 1
        ? Math.min(100, Math.round(scraper.processedPages / scraper.estimatedPages * 100))
        : scraper.running ? 35 : 100;
    return <div className={scraper.running ? "scraper-process active" : "scraper-process"}>
        <span className={scraper.running ? "process-icon running" : "process-icon"}>{scraper.running ? "↻" : "✓"}</span>
        <div className="scraper-process-main">
            <div className="scraper-process-title"><strong>{scraper.id}</strong><span className={scraper.running ? "badge running" : "badge"}>{scraper.running ? "Aktiv" : "Bereit"}</span></div>
            <small className="scraper-site">{scraper.baseUrl}</small>
            <div className="scraper-current-url" title={scraper.currentUrl || scraper.message}>{scraper.currentUrl || scraper.message || "Wartet auf nächsten Lauf"}</div>
            {scraper.running && <><div className="process-progress"><span style={{width: `${progress}%`}}/></div><small className="process-count">{num(scraper.processedPages)} Seiten verarbeitet{scraper.estimatedPages > 1 ? ` · Ziel ${num(scraper.estimatedPages)}` : ""}</small></>}
            {scraper.lastError && <small className="process-error">{scraper.lastError}</small>}
        </div>
    </div>
}

function Awin({awin}: { awin?: AwinStatus }) {
    const [feeds, setFeeds] = useState<Feed[]>([]), [loading, setLoading] = useState(true);
    useEffect(() => {
        api<Feed[]>("/api/v1/admin/awin/feeds").then(setFeeds).catch(() => undefined).finally(() => setLoading(false))
    }, []);
    const p = awin?.overallTotal ? Math.round(awin.overallProcessed / awin.overallTotal * 100) : 0;
    return <section className="content">
        <div className="page-intro">
            <div><span className="kicker">AFFILIATE CATALOG</span><h2>Awin Feed</h2><p>Aktive Advertiser-Feeds und
                Importfortschritt im Überblick.</p></div>
            <span
                className={awin?.running ? "badge running large" : "badge large"}>{awin?.running ? "Import aktiv" : "Bereit"}</span>
        </div>
        <div className="awin-summary">
            <div><span className="kicker">FEED-IMPORT</span>
                <h3>{awin?.running ? awin.currentFeedAdvertiser || "Import läuft" : "Nächster automatischer Lauf"}</h3>
                <p>{awin?.running ? awin.message || `${num(awin.overallProcessed)} von ${num(awin.overallTotal)} Listings` : awin?.nextRunAt ? date(awin.nextRunAt) : "Stündlich"}</p>
            </div>
            <div className="awin-progress">
                <strong>{awin?.running ? `${p}%` : num(feeds.length || awin?.totalFeeds)}</strong><small>{awin?.running ? "Gesamtfortschritt" : "aktive Feeds"}</small>
            </div>
        </div>
        <div className="panel">
            <div className="panel-head"><h3>Aktive Advertiser-Feeds</h3><span
                className="muted">{loading ? "Laden …" : `${feeds.length} Feeds`}</span></div>
            <div className="feed-list">{feeds.map((f, i) => <div className="feed-row" key={`${f.advertiser}-${i}`}><span
                className="feed-avatar">{f.advertiser.slice(0, 1).toUpperCase()}</span>
                <div>
                    <strong>{f.advertiser}</strong><small>{f.primaryRegion || "Region unbekannt"} · {f.language || "Sprache unbekannt"}</small>
                </div>
                <span className="feed-link">CSV Feed ↗</span></div>)}{!loading && !feeds.length &&
                <div className="empty compact"><h3>Keine Feed-Daten</h3><p>Die Awin-Übersicht konnte aktuell nicht
                    geladen werden.</p></div>}</div>
        </div>
        {awin?.lastError && <div className="alert danger">Letzter Awin-Fehler: {awin.lastError}</div>}</section>
}

"use client";

import Link from "next/link";
import {useEffect, useState} from "react";

type Hardware = { id?: number; manufacturer?: string; model?: string; displayName?: string; specType?: string; mpns?: string[]; eans?: string[]; pictureUrls?: string[] };
type Marketplace = { id: string; domain: string; country: string };
type RegionResult = { marketplace: string; domain: string; status: string; listings: number; verifiedListings?: number; highConfidenceListings?: number; rejectedListings?: number; error?: string };
type Job = { jobId: string; identifier: string; status: string; totalRegions: number; completedRegions: number; currentRegion?: string; results: RegionResult[]; error?: string };

async function api<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, { ...init, headers: {Accept: "application/json", ...(init?.headers ?? {})}, cache: "no-store" });
    if (!response.ok) throw Error(`API ${response.status}`);
    return response.json();
}

const nameOf = (h: Hardware) => h.displayName || h.model || "Unbenannte Hardware";

export default function EbayPage() {
    const [query, setQuery] = useState("");
    const [hardware, setHardware] = useState<Hardware[]>([]);
    const [selected, setSelected] = useState<Hardware>();
    const [marketplaces, setMarketplaces] = useState<Marketplace[]>([]);
    const [job, setJob] = useState<Job>();
    const [loading, setLoading] = useState(false);
    const [focused, setFocused] = useState(false);
    const [activeSuggestion, setActiveSuggestion] = useState(0);
    const [error, setError] = useState("");

    useEffect(() => { api<Marketplace[]>("/api/v1/prices/sold/ebay/marketplaces").then(setMarketplaces).catch(() => undefined); }, []);

    const search = async (value = query) => {
        if (!value.trim()) { setHardware([]); return; }
        setLoading(true); setError("");
        try { setHardware(await api<Hardware[]>(`/api/v1/specs/search?q=${encodeURIComponent(value.trim())}&limit=20`)); setActiveSuggestion(0); }
        catch { setError("Die Hardware-Suche konnte nicht geladen werden."); }
        finally { setLoading(false); }
    };

    // Same live-search behaviour as the PC-Flipping picker: query the catalog
    // shortly after typing, while ignoring stale responses from older queries.
    useEffect(() => {
        let alive = true;
        const timer = window.setTimeout(async () => {
            if (!query.trim()) { if (alive) setHardware([]); return; }
            try {
                const result = await api<Hardware[]>(`/api/v1/specs/search?q=${encodeURIComponent(query.trim())}&limit=20`);
                if (alive) { setHardware(result); setActiveSuggestion(0); }
            } catch { if (alive) setError("Die Hardware-Suche konnte nicht geladen werden."); }
        }, 120);
        return () => { alive = false; window.clearTimeout(timer); };
    }, [query]);

    const chooseHardware = (item: Hardware) => {
        setSelected(item);
        setQuery(nameOf(item));
        setHardware([]);
        setFocused(false);
    };

    const startLookup = async () => {
        const identifier = selected?.mpns?.[0] || selected?.eans?.[0];
        if (!identifier) return;
        setError(""); setJob(undefined);
        try {
            const created = await api<{jobId: string}>("/api/v1/prices/sold/ebay/lookups", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({identifier})});
            setJob(await api<Job>(`/api/v1/prices/sold/ebay/lookups/${created.jobId}`));
        } catch { setError("Der eBay-Lookup konnte nicht gestartet werden."); }
    };

    useEffect(() => {
        if (!job || job.status === "COMPLETED") return;
        const timer = window.setInterval(async () => { try { setJob(await api<Job>(`/api/v1/prices/sold/ebay/lookups/${job.jobId}`)); } catch { /* keep current progress */ } }, 3000);
        return () => window.clearInterval(timer);
    }, [job]);

    const progress = job ? Math.round(job.completedRegions / Math.max(job.totalRegions, 1) * 100) : 0;
    return <main className="content ebay-page">
        <Link href="/" className="back-link">← Zur Datenbank</Link>
        <div className="page-intro"><div><span className="kicker">EBAY PRICE INTELLIGENCE</span><h2>Verkaufte Angebote über alle Regionen</h2><p>Wähle ein Hardware-Produkt aus und starte einen regionalen eBay-Sold-Price-Lookup.</p></div></div>
        <section className="panel ebay-search-panel">
            <div className="ebay-search-wrap"><div className="search-box"><span>⌕</span><input value={query} onFocus={() => setFocused(true)} onChange={e => { setQuery(e.target.value); setSelected(undefined); }} onKeyDown={e => { if (e.key === "ArrowDown") { e.preventDefault(); setActiveSuggestion(i => Math.min(i + 1, hardware.length - 1)); } else if (e.key === "ArrowUp") { e.preventDefault(); setActiveSuggestion(i => Math.max(i - 1, 0)); } else if (e.key === "Enter" && hardware[activeSuggestion]) { e.preventDefault(); chooseHardware(hardware[activeSuggestion]); } else if (e.key === "Escape") setFocused(false); }} placeholder="Modell, Hersteller, MPN oder EAN suchen …" aria-label="Hardware suchen"/><button onClick={() => void search()} disabled={loading}>{loading ? "Suche …" : "Suchen"}</button></div>
                {!!hardware.length && focused && <div className="ebay-results" role="listbox">{hardware.map((item, index) => <button role="option" aria-selected={index === activeSuggestion} className={index === activeSuggestion ? "ebay-result selected" : "ebay-result"} key={String(item.id)} onMouseDown={e => e.preventDefault()} onClick={() => chooseHardware(item)}><span className="ebay-result-icon">{String(item.specType || "HW").slice(0, 2).toUpperCase()}</span><span><strong>{nameOf(item)}</strong><small>{item.manufacturer || "—"} · {(item.mpns || []).join(", ") || (item.eans || []).join(", ") || "Keine Kennung"}</small></span></button>)}</div>}
            </div>
            {!loading && query && !hardware.length && <p className="empty-note">Keine passende Hardware gefunden.</p>}
        </section>
        <section className="ebay-layout">
            <div className="panel"><div className="panel-head"><div><span className="kicker">AUSGEWÄHLTES PRODUKT</span><h3>{selected ? nameOf(selected) : "Noch kein Produkt ausgewählt"}</h3></div><button className="primary-button" disabled={!selected || !!job && job.status !== "COMPLETED"} onClick={() => void startLookup()}>{job && job.status !== "COMPLETED" ? "Lookup läuft …" : "In eBay nachschlagen"}</button></div>{selected && <p className="muted">{selected.manufacturer} · MPN: {(selected.mpns || []).join(", ") || "—"} · EAN: {(selected.eans || []).join(", ") || "—"}</p>}{!selected && <p className="empty-note">Suche oben nach einer CPU, GPU, SSD oder einem anderen Katalogeintrag.</p>}</div>
            <div className="panel"><div className="panel-head"><div><span className="kicker">REGIONEN</span><h3>{marketplaces.length} eBay-Marktplätze</h3></div><span className="muted">10 s Rate-Limit</span></div><div className="marketplace-grid">{marketplaces.map(m => <span key={m.id} className="marketplace-chip">{m.country} <small>{m.domain}</small></span>)}</div></div>
        </section>
        {job && <section className="panel ebay-job"><div className="panel-head"><div><span className="kicker">LOOKUP-QUEUE</span><h3>{job.status === "COMPLETED" ? "Lookup abgeschlossen" : job.status === "RUNNING" ? `Region wird verarbeitet: ${job.currentRegion || "—"}` : "Wartet in der Queue"}</h3></div><strong className="job-progress-value">{progress}%</strong></div><div className="progress"><span style={{width: `${progress}%`}}/></div><p className="muted">{job.completedRegions} von {job.totalRegions} Regionen verarbeitet · Suchbegriff: {job.identifier}</p><div className="ebay-region-table">{job.results.map(result => <div className="ebay-region-row" key={result.marketplace}><span>{result.domain}</span><span className={result.status === "COMPLETED" ? "badge" : "badge error-badge"}>{result.status === "COMPLETED" ? `${result.verifiedListings ?? 0} verifiziert · ${result.highConfidenceListings ?? 0} prüfen` : "Fehler"}</span></div>)}</div></section>}
        {error && <div className="alert danger">{error}</div>}
    </main>;
}

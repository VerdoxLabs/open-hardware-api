"use client";

import Link from "next/link";
import {useEffect, useState} from "react";

type IcecatStatus = { configured: boolean; language: string; baseUrl: string };
type LookupResult = { status: "MATCHED" | "NOT_FOUND"; matchType?: string; title?: string; description?: string; images: string[]; specificationsJson?: string; rawPreview?: string; message?: string };
type Hardware = { id?: number; manufacturer?: string; model?: string; displayName?: string; mpns?: string[]; eans?: string[]; specType?: string };

async function api<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, { ...init, headers: {Accept: "application/json", ...(init?.headers ?? {})}, cache: "no-store" });
    if (!response.ok) {
        const detail = await response.json().catch(() => null) as { detail?: string } | null;
        throw Error(detail?.detail || `API ${response.status}`);
    }
    return response.json();
}

const nameOf = (item: Hardware) => item.displayName || item.model || "Unbenannte Hardware";

export default function IcecatPage() {
    const [status, setStatus] = useState<IcecatStatus>();
    const [gtin, setGtin] = useState("");
    const [brand, setBrand] = useState("");
    const [mpn, setMpn] = useState("");
    const [query, setQuery] = useState("");
    const [suggestions, setSuggestions] = useState<Hardware[]>([]);
    const [result, setResult] = useState<LookupResult>();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [showRaw, setShowRaw] = useState(false);

    const refreshStatus = () => api<IcecatStatus>("/api/v1/admin/icecat/status").then(setStatus).catch(() => setError("Icecat-Status konnte nicht geladen werden."));
    useEffect(() => { refreshStatus(); }, []);
    useEffect(() => {
        let active = true;
        const timer = window.setTimeout(async () => {
            if (!query.trim()) { if (active) setSuggestions([]); return; }
            try {
                const matches = await api<Hardware[]>(`/api/v1/specs/search?q=${encodeURIComponent(query.trim())}&limit=8`);
                if (active) setSuggestions(matches);
            } catch { if (active) setSuggestions([]); }
        }, 180);
        return () => { active = false; window.clearTimeout(timer); };
    }, [query]);

    const pick = (item: Hardware) => {
        setQuery(nameOf(item));
        setBrand(item.manufacturer || "");
        setMpn(item.mpns?.[0] || "");
        setGtin(item.eans?.[0] || "");
        setSuggestions([]);
        setResult(undefined);
    };
    const lookup = async () => {
        setLoading(true); setError(""); setResult(undefined); setShowRaw(false);
        try {
            setResult(await api<LookupResult>("/api/v1/admin/icecat/lookup", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({gtin, brand, mpn})}));
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Icecat-Abfrage fehlgeschlagen.");
        } finally { setLoading(false); }
    };

    return <main className="content icecat-page">
        <Link href="/" className="back-link">← Zur Datenbank</Link>
        <div className="page-intro">
            <div><span className="kicker">ICECAT ENRICHMENT LAB</span><h2>Produktdaten testen</h2><p>Suche lokale Hardware oder gib EAN, Marke und MPN ein. Die Anfrage geht ausschließlich über die Server-API; Zugangsdaten bleiben unsichtbar.</p></div>
            <button className="backup-button" onClick={refreshStatus}>↻ Status aktualisieren</button>
        </div>
        <section className="icecat-status panel">
            <div className={status?.configured ? "big-status" : "big-status inactive"}>{status?.configured ? "✓" : "!"}</div>
            <div><span className="kicker">VERBINDUNG</span><h3>{status?.configured ? "Icecat ist bereit" : "Icecat ist noch nicht konfiguriert"}</h3><p>{status?.configured ? `Sprache: ${status.language}` : "Prüfe ICECAT_ENABLED, ICECAT_SHOP_NAME und ICECAT_API_TOKEN in der Server-Umgebung."}</p></div>
        </section>
        <section className="panel icecat-form">
            <div className="panel-head"><div><span className="kicker">SCHRITT 1</span><h3>Hardware aus dem lokalen Katalog übernehmen</h3></div><span className="muted">optional</span></div>
            <div className="icecat-search"><input value={query} onChange={event => setQuery(event.target.value)} placeholder="CPU, GPU, SSD, MPN oder EAN suchen …" aria-label="Lokale Hardware suchen"/>{suggestions.length > 0 && <div className="icecat-suggestions">{suggestions.map(item => <button key={String(item.id)} onClick={() => pick(item)}><strong>{nameOf(item)}</strong><small>{item.manufacturer || "—"} · {(item.mpns || []).join(", ") || (item.eans || []).join(", ") || "Keine Kennung"}</small></button>)}</div>}</div>
            <div className="icecat-fields">
                <label>EAN / GTIN<input value={gtin} onChange={event => setGtin(event.target.value)} placeholder="z. B. 4260052187924" inputMode="numeric"/></label>
                <label>Marke<input value={brand} onChange={event => setBrand(event.target.value)} placeholder="z. B. Samsung"/></label>
                <label>MPN<input value={mpn} onChange={event => setMpn(event.target.value)} placeholder="z. B. MZ-V8P1T0BW"/></label>
            </div>
            <div className="icecat-actions"><p className="muted">Match-Reihenfolge: GTIN, danach Marke + MPN.</p><button className="primary-button" disabled={loading || (!gtin.trim() && (!brand.trim() || !mpn.trim())) || !status?.configured} onClick={() => void lookup()}>{loading ? "Icecat wird abgefragt …" : "Bei Icecat nachschlagen"}</button></div>
        </section>
        {error && <div className="alert danger">{error}</div>}
        {result && <section className="icecat-result">
            <div className="panel icecat-result-head"><div><span className="kicker">ERGEBNIS</span><h3>{result.status === "MATCHED" ? result.title || "Icecat-Datenblatt gefunden" : "Kein Datenblatt gefunden"}</h3><p>{result.status === "MATCHED" ? `Gematcht über ${result.matchType === "GTIN" ? "GTIN/EAN" : "Marke + MPN"}.` : result.message}</p></div><span className={result.status === "MATCHED" ? "badge large" : "badge error-badge large"}>{result.status === "MATCHED" ? "Gefunden" : "Kein Treffer"}</span></div>
            {result.status === "MATCHED" && <div className="icecat-result-grid">
                <div className="panel"><div className="panel-head"><h3>Inhalte</h3><span className="muted">normalisiert</span></div>{result.description && <p className="icecat-description">{result.description}</p>}{result.images.length > 0 && <div className="icecat-images">{result.images.map(url => <img key={url} src={url} alt="Icecat Produktbild"/>)}</div>}{result.specificationsJson && <details className="icecat-specs"><summary>Spezifikationen anzeigen</summary><pre>{result.specificationsJson}</pre></details>}</div>
                <div className="panel"><div className="panel-head"><h3>Rohantwort</h3><button onClick={() => setShowRaw(value => !value)}>{showRaw ? "Ausblenden" : "Anzeigen"}</button></div><p className="empty-note">Hilft beim Prüfen, ob ein zusätzliches Icecat-Feld in den finalen Parser soll.</p>{showRaw && <pre className="icecat-raw">{result.rawPreview || "Keine Rohantwort verfügbar."}</pre>}</div>
            </div>}
        </section>}
    </main>;
}

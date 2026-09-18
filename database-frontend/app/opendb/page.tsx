"use client";

import Link from "next/link";
import {useEffect, useState} from "react";

type ImportResult = { enabled: boolean; imported: number; invalid: number; error?: string | null; importedByCategory?: Record<string, number> };
type OpenDbStatus = { enabled: boolean; repository: string; branch: string; checkoutDirectory: string; schemaDirectory: string; running: boolean; phase: string; processedFiles: number; totalFiles: number; downloadedBytes: number; downloadTotalBytes: number; phaseCurrent: number; phaseTotal: number; currentCategory?: string; currentFile?: string; startedAt?: string | number; finishedAt?: string | number; lastImport?: ImportResult };

async function api<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, { ...init, headers: {Accept: "application/json", ...(init?.headers ?? {})}, cache: "no-store" });
    if (!response.ok) throw Error(`API ${response.status}`);
    return response.json();
}

const date = (value?: string | number) => {
    if (value === undefined || value === null || value === "") return "Noch nicht";
    const raw = typeof value === "string" && /^\d+(\.\d+)?$/.test(value) ? Number(value) : value;
    const timestamp = typeof raw === "number" && raw < 1_000_000_000_000 ? raw * 1000 : raw;
    const parsed = new Date(timestamp);
    return Number.isNaN(parsed.getTime()) ? "Unbekannt" : new Intl.DateTimeFormat("de-DE", {dateStyle: "medium", timeStyle: "short"}).format(parsed);
};
const bytes = (value: number) => value < 1024 * 1024 ? `${Math.round(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`;
const phaseLabel = (phase?: string) => ({STARTING: "Wird vorbereitet", REPOSITORY_SYNC: "Repository wird synchronisiert", DOWNLOAD: "OpenDB wird heruntergeladen", EXTRACT: "Archiv wird entpackt", SCANNING: "Dateien werden gesucht", PARSING: "JSON-Dateien werden geparst", PERSISTING: "Datensätze werden gespeichert", COMPLETE: "Abgeschlossen", ERROR: "Fehler"}[phase || ""] || "Wird verarbeitet");

export default function OpenDbPage() {
    const [status, setStatus] = useState<OpenDbStatus>();
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");

    const refresh = () => api<OpenDbStatus>("/api/v1/admin/opendb/status").then(value => { setStatus(value); setError(""); }).catch(() => setError("OpenDB-Status konnte nicht geladen werden."));
    useEffect(() => { refresh(); }, []);
    useEffect(() => {
        const timer = window.setInterval(refresh, status?.running ? 2500 : 15000);
        return () => window.clearInterval(timer);
    }, [status?.running]);

    const startImport = async () => {
        setMessage(""); setError("");
        try {
            const result = await api<{message: string}>("/api/v1/admin/opendb/import", {method: "POST"});
            setMessage(result.message); await refresh();
        } catch { setError("Import konnte nicht gestartet werden. Läuft möglicherweise bereits."); await refresh(); }
    };
    const last = status?.lastImport;
    const categories = Object.entries(last?.importedByCategory || {});

    return <main className="content opendb-page">
        <Link href="/" className="back-link">← Zur Datenbank</Link>
        <div className="page-intro"><div><span className="kicker">BUILDCORES OPEN DATABASE</span><h2>OpenDB Import</h2><p>Synchronisiert das quelloffene BuildCores-Repository und übernimmt die JSON-Datensätze in den lokalen Hardwarekatalog.</p></div><button className="backup-button" onClick={refresh}>↻ Status aktualisieren</button></div>
        <section className="opendb-status panel"><div className={status?.running ? "big-status running" : status?.enabled ? "big-status" : "big-status inactive"}>{status?.running ? "…" : status?.enabled ? "✓" : "!"}</div><div><span className="kicker">IMPORT-STATUS</span><h3>{status?.running ? phaseLabel(status.phase) : status?.enabled ? "OpenDB ist aktiviert" : "OpenDB ist deaktiviert"}</h3><p>{status?.running ? `${status.processedFiles}/${status.totalFiles || "?"} Dateien verarbeitet${status.currentCategory ? ` · ${status.currentCategory}` : ""}` : last?.error || (last ? `Letzter Lauf: ${date(status?.finishedAt)}.` : "Noch kein Import ausgeführt.")}</p></div><button className="primary-button opendb-run" disabled={!status?.enabled || status.running} onClick={() => void startImport()}>{status?.running ? "Import läuft …" : "Jetzt synchronisieren"}</button></section>
        {status?.running && <section className="panel opendb-progress"><div className="panel-head"><div><span className="kicker">LIVE-FORTSCHRITT</span><h3>{phaseLabel(status.phase)}</h3></div><span className="muted">{status.phase === "DOWNLOAD" && status.downloadTotalBytes > 0 ? `${Math.round(status.downloadedBytes / status.downloadTotalBytes * 100)} %` : status.phase === "EXTRACT" && status.phaseTotal > 0 || status.phase === "REPOSITORY_SYNC" && status.phaseTotal > 0 ? `${status.phaseCurrent}/${status.phaseTotal}` : status.phase === "PARSING" ? `${status.processedFiles}/${status.totalFiles}` : "…"}</span></div><div className={`opendb-progress-bar ${status.phase === "DOWNLOAD" && status.downloadTotalBytes > 0 || status.phase === "EXTRACT" && status.phaseTotal > 0 || status.phase === "REPOSITORY_SYNC" && status.phaseTotal > 0 || status.phase === "PARSING" && status.totalFiles > 0 ? "determinate" : "indeterminate"}`}><span style={{width: status.phase === "DOWNLOAD" && status.downloadTotalBytes > 0 ? `${Math.min(100, status.downloadedBytes / status.downloadTotalBytes * 100)}%` : status.phase === "EXTRACT" && status.phaseTotal > 0 || status.phase === "REPOSITORY_SYNC" && status.phaseTotal > 0 ? `${Math.min(100, status.phaseCurrent / status.phaseTotal * 100)}%` : status.phase === "PARSING" && status.totalFiles > 0 ? `${Math.min(100, status.processedFiles / status.totalFiles * 100)}%` : "100%"}} /></div><div className="opendb-progress-meta"><span>{status.phase === "DOWNLOAD" ? `${bytes(status.downloadedBytes)} / ${status.downloadTotalBytes ? bytes(status.downloadTotalBytes) : "unbekannt"}` : status.phase === "EXTRACT" ? `${status.phaseCurrent} von ${status.phaseTotal || "?"} Archivdateien entpackt` : status.phase === "REPOSITORY_SYNC" ? `Repository-Schritt ${status.phaseCurrent} von ${status.phaseTotal || "?"}` : `${status.processedFiles} von ${status.totalFiles || "?"} Dateien geparst`}</span><span>{status.currentFile || "Bitte warten …"}</span></div></section>}
        {error && <div className="alert danger">{error}</div>}{message && <div className="alert success">{message}</div>}
        <section className="opendb-grid"><div className="panel"><div className="panel-head"><div><span className="kicker">ERGEBNIS</span><h3>Letzter Import</h3></div><span className="muted">{date(status?.finishedAt)}</span></div>{last?.error && <div className="alert danger opendb-result-error">{last.error}</div>}{last ? <><div className="opendb-metrics"><div><strong>{last.imported}</strong><span>importiert</span></div><div><strong>{last.invalid}</strong><span>übersprungen</span></div><div><strong>{categories.length}</strong><span>Kategorien</span></div></div>{categories.length > 0 ? <div className="opendb-categories">{categories.map(([category, count]) => <div key={category}><span>{category}</span><strong>{count}</strong></div>)}</div> : <p className="empty-note">Keine gültigen Datensätze wurden importiert.</p>}</> : <p className="empty-note">Noch kein abgeschlossener Import vorhanden.</p>}</div><div className="panel"><div className="panel-head"><div><span className="kicker">KONFIGURATION</span><h3>Quelle &amp; Parser</h3></div></div><dl className="opendb-config"><dt>Repository</dt><dd>{status?.repository || "—"}</dd><dt>Branch</dt><dd>{status?.branch || "—"}</dd><dt>Checkout</dt><dd>{status?.checkoutDirectory || "—"}</dd><dt>Schemas</dt><dd>{status?.schemaDirectory || "—"}</dd><dt>Gestartet</dt><dd>{date(status?.startedAt)}</dd></dl></div></section>
        <p className="opendb-note">Die Parser validieren die lokalen JSON-Dateien gegen die Schemas aus dem Repository. OpenDB selbst liefert keine Bild-URLs; passende Awin-Feed-Bilder werden anhand von EAN oder MPN lokal gespiegelt und anschließend dem Produkt zugeordnet.</p>
    </main>;
}

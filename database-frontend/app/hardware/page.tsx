"use client";

import Link from "next/link";
import {useEffect, useMemo, useState} from "react";

type Hardware = Record<string, unknown> & { manufacturer?: string; model?: string; displayName?: string; specType?: string; mpns?: string[]; eans?: string[]; pictureUrls?: string[]; displayPictureUrl?: string };
type PricePoint = { date: string; price: number; currency?: string; marketName?: string; condition?: string };
type SeriesDto = { condition?: string; areCompletedListings?: boolean; prices?: Record<string, Array<{ date?: string; price?: number; currency?: string; marketName?: string }>> };
type SeriesResponse = { series?: SeriesDto[]; results?: Array<{ series?: SeriesResponse }> };
type Benchmark = { modelName?: string; cpuMarkScore?: number; threadMarkScore?: number; g3dMarkScore?: number; g2dMarkScore?: number };

const label = (key: string) => key.replace(/([A-Z])/g, " $1").replace(/^./, x => x.toUpperCase()).replace(/_/g, " ");
const isDefault = (value: unknown) => value === 0 || value === "UNKNOWN" || value === "unknown" || value === "" || value === null || value === undefined || (Array.isArray(value) && value.length === 0);
const pretty = (value: unknown) => Array.isArray(value) ? value.join(" · ") : typeof value === "boolean" ? (value ? "Ja" : "Nein") : String(value);

async function api<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, { ...init, headers: {Accept: "application/json", ...(init?.headers ?? {})}, cache: "no-store" });
    if (!response.ok) throw Error(`API ${response.status}`);
    return response.json();
}

function flattenSeries(response: SeriesResponse): PricePoint[] {
    const result: PricePoint[] = [];
    const groups = [...(response.series ?? []), ...(response.results ?? []).flatMap(entry => entry.series?.series ?? [])];
    for (const group of groups) for (const [currency, points] of Object.entries(group.prices ?? {})) {
        for (const point of points ?? []) if (point.date && typeof point.price === "number") result.push({date: point.date, price: point.price, currency, marketName: point.marketName, condition: group.condition});
    }
    return result.sort((a, b) => a.date.localeCompare(b.date));
}

export default function HardwareDetailPage() {
    const [spec, setSpec] = useState<Hardware>();
    const [active, setActive] = useState<PricePoint[]>([]);
    const [completed, setCompleted] = useState<PricePoint[]>([]);
    const [benchmark, setBenchmark] = useState<Benchmark>();
    const [error, setError] = useState("");
    const [showDefaults, setShowDefaults] = useState(false);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const type = params.get("type") || "";
        const mpn = params.get("mpn") || "";
        void (async () => {
            try {
                if (!mpn) throw Error("Kein MPN angegeben.");
                const hardware = await api<Hardware>(`/api/v1/specs/byMpn?type=${encodeURIComponent(type)}&mpn=${encodeURIComponent(mpn)}`);
                setSpec(hardware);
                const identifiers = {mpns: hardware.mpns ?? [], eans: hardware.eans ?? [], conditions: ["NEW", "USED"], monthSince: 12, fetchIfNoData: false};
                const [activeResponse, completedResponse] = await Promise.all([
                    api<SeriesResponse>("/api/v1/prices/sold/series/fetchActive/bulkByIds", {method: "POST", body: JSON.stringify(identifiers), headers: {"Content-Type": "application/json"}}),
                    api<SeriesResponse>("/api/v1/prices/sold/series/fetchCompleted/bulkByIds", {method: "POST", body: JSON.stringify(identifiers), headers: {"Content-Type": "application/json"}}),
                ]);
                setActive(flattenSeries(activeResponse)); setCompleted(flattenSeries(completedResponse));
                const name = String(hardware.specType === "gpu" ? hardware.gpuCanonicalName || hardware.model : hardware.displayName || hardware.model);
                try {
                    if (hardware.specType === "cpu") setBenchmark(await api<Benchmark>(`/api/v1/benchmark/cpu?cpuModelName=${encodeURIComponent(name)}`));
                    if (hardware.specType === "gpu") setBenchmark(await api<Benchmark>(`/api/v1/benchmark/gpu?gpuCanonicalName=${encodeURIComponent(name)}`));
                } catch { /* benchmark data is optional */ }
            } catch { setError("Die Hardware-Details konnten nicht geladen werden."); }
        })();
    }, []);

    const fields = useMemo(() => Object.entries(spec ?? {}).filter(([key, value]) => !["id", "manufacturer", "model", "displayName", "pictureUrls", "displayPictureUrl", "mpns", "eans", "specType"].includes(key) && !Array.isArray(value) && typeof value !== "object" && !isDefault(value)), [spec]);
    const defaults = useMemo(() => Object.entries(spec ?? {}).filter(([key, value]) => !["id", "manufacturer", "model", "displayName", "pictureUrls", "displayPictureUrl", "mpns", "eans", "specType"].includes(key) && !Array.isArray(value) && typeof value !== "object" && isDefault(value)), [spec]);
    const allPrices = [...active, ...completed];
    const currency = allPrices[0]?.currency || "EUR";
    const money = (value?: number) => value == null ? "—" : new Intl.NumberFormat("de-DE", {style: "currency", currency}).format(value);
    const currentPrices = active.filter(p => p.currency === currency).map(p => p.price);
    const lowest = currentPrices.length ? Math.min(...currentPrices) : undefined;
    const image = spec?.displayPictureUrl || spec?.pictureUrls?.find(x => !x.includes("noimage"));

    if (error) return <main className="detail-page"><Link href="/" className="back-link">← Zur Datenbank</Link><div className="detail-error">{error}</div></main>;
    if (!spec) return <main className="detail-page"><div className="detail-loading">Hardware-Details werden geladen …</div></main>;

    return <main className="detail-page">
        <Link href="/" className="back-link">← Zur Datenbank</Link>
        <div className="detail-heading"><div><span className="kicker">HARDWARE-DETAILS</span><h1>{String(spec.displayName || spec.model || "Hardware")}</h1><p>{String(spec.manufacturer || "Hersteller unbekannt")} · {String(spec.specType || "Komponente")}</p></div><div className="detail-identifiers"><span>MPN {(spec.mpns ?? []).join(", ") || "—"}</span><span>EAN {(spec.eans ?? []).join(", ") || "—"}</span></div></div>
        <div className="detail-grid">
            <section className="detail-main">
                {image && <div className="detail-image"><img src={image} alt={String(spec.displayName || spec.model || "Hardware")}/></div>}
                <div className="detail-card"><div className="section-title"><span className="kicker">TECHNISCHE DATEN</span><h2>Spezifikationen</h2></div><div className="spec-grid">{fields.map(([key, value]) => <div className="spec-field" key={key}><small>{label(key)}</small><strong>{pretty(value)}</strong></div>)}</div>{defaults.length > 0 && <div className="default-fields"><button onClick={() => setShowDefaults(x => !x)}>{showDefaults ? "Standardwerte ausblenden" : `${defaults.length} Standardwerte anzeigen`}</button>{showDefaults && <div className="spec-grid muted-fields">{defaults.map(([key, value]) => <div className="spec-field" key={key}><small>{label(key)}</small><strong>{pretty(value)}</strong></div>)}</div>}</div>}</div>
            </section>
            <aside className="detail-side">
                <div className="detail-card price-summary"><span className="kicker">PREISÜBERSICHT</span><h2>{money(lowest)}</h2><p>Niedrigster aktueller Preis · {active.length ? `${active.length} Preis-Punkte` : "keine aktuellen Daten"}</p></div>
                <PriceHistory active={active} completed={completed} money={money}/>
                {benchmark && <BenchmarkCard benchmark={benchmark} type={String(spec.specType)} />}
            </aside>
        </div>
    </main>;
}

function PriceHistory({active, completed, money}: {active: PricePoint[]; completed: PricePoint[]; money: (value?: number) => string}) {
    const points = [...active, ...completed].filter(p => p.price > 0).slice(-24);
    const max = Math.max(...points.map(p => p.price), 1); const min = Math.min(...points.map(p => p.price), 0);
    return <div className="detail-card price-history"><div className="section-title"><span className="kicker">MARKTDATEN</span><h2>Preisverlauf</h2></div>{points.length ? <><div className="history-chart">{points.map((point, i) => <div className="history-point" key={`${point.date}-${i}`} title={`${point.date}: ${money(point.price)}`}><span style={{height: `${Math.max(8, ((point.price - min) / Math.max(max - min, 1)) * 100)}%`}}/></div>)}</div><div className="history-meta"><span>{points[0].date}</span><strong>{money(Math.min(...points.map(p => p.price)))} – {money(Math.max(...points.map(p => p.price)))}</strong><span>{points[points.length - 1].date}</span></div></> : <p className="empty-note">Noch kein Preisverlauf vorhanden.</p>}</div>;
}

function BenchmarkCard({benchmark, type}: {benchmark: Benchmark; type: string}) {
    const score = type === "gpu" ? benchmark.g3dMarkScore : benchmark.cpuMarkScore;
    const secondary = type === "gpu" ? benchmark.g2dMarkScore : benchmark.threadMarkScore;
    return <div className="detail-card benchmark-card"><div className="section-title"><span className="kicker">PERFORMANCE</span><h2>Benchmark-Details</h2></div><div className="benchmark-score"><strong>{score ? new Intl.NumberFormat("de-DE").format(score) : "—"}</strong><span>{type === "gpu" ? "3DMark Score" : "CPU Mark"}</span></div>{secondary != null && <div className="benchmark-secondary"><span>{type === "gpu" ? "2DMark" : "Thread Mark"}</span><strong>{new Intl.NumberFormat("de-DE").format(secondary)}</strong></div>}<p className="empty-note">Quelle: PassMark · Modellabgleich aus der Benchmark-Datenbank.</p></div>;
}

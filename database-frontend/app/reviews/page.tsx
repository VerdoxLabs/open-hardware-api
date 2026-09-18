"use client";

import Link from "next/link";
import {useCallback, useEffect, useState} from "react";

type Review = { id: number; marketPlaceItemId: string; rawTitle: string; bestScore: number; tier: number; matchedEan?: string; matchedMpn?: string; adUrl?: string; status: "PENDING" | "CONFIRMED" | "REJECTED"; confirmedBy?: string; createdAt?: string };

async function api<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, {...init, headers: {Accept: "application/json", "Content-Type": "application/json", ...(init?.headers ?? {})}, cache: "no-store"});
    if (!response.ok) throw Error(`API ${response.status}`);
    return response.json();
}

export default function ReviewsPage() {
    const [status, setStatus] = useState<Review["status"]>("PENDING");
    const [reviews, setReviews] = useState<Review[]>([]);
    const [error, setError] = useState("");
    const load = useCallback(async () => { try { setReviews(await api<Review[]>(`/api/v1/pricing/c2c-reviews?status=${status}`)); setError(""); } catch { setError("Die Review-Queue konnte nicht geladen werden."); } }, [status]);
    useEffect(() => { void load(); }, [load]);
    const action = async (id: number, type: "confirm" | "reject", review?: Review) => {
        const ean = type === "confirm" ? window.prompt("EAN korrigieren (optional):", review?.matchedEan || "") : null;
        if (type === "confirm" && ean === null) return;
        const mpn = type === "confirm" ? window.prompt("MPN korrigieren (optional):", review?.matchedMpn || "") : undefined;
        if (type === "confirm" && mpn === null) return;
        try { await api(`/api/v1/pricing/c2c-reviews/${id}/${type}`, {method: "POST", body: JSON.stringify({actor: "frontend", ean: ean || undefined, mpn: mpn || undefined})}); await load(); } catch { setError("Die Änderung konnte nicht gespeichert werden."); }
    };
    return <main className="content review-page">
        <Link href="/" className="back-link">← Zur Datenbank</Link>
        <div className="page-intro"><div><span className="kicker">C2C MATCH MANAGEMENT</span><h2>Kleinanzeigen-Review-Queue</h2><p>Unsichere automatische Zuordnungen prüfen und die Lernschleife der Product Registry korrigieren.</p></div></div>
        <div className="review-tabs">{(["PENDING", "CONFIRMED", "REJECTED"] as const).map(x => <button key={x} className={status === x ? "active" : ""} onClick={() => setStatus(x)}>{x === "PENDING" ? "Offen" : x === "CONFIRMED" ? "Bestätigt" : "Abgelehnt"}</button>)}</div>
        {error && <div className="alert danger">{error}</div>}
        {!reviews.length ? <div className="empty compact"><div className="empty-icon">✓</div><h3>Keine Einträge</h3><p>In diesem Status gibt es aktuell keine Reviews.</p></div> : <div className="review-list">{reviews.map(review => <article className="panel review-card" key={review.id}><div className="review-main"><div><span className="kicker">{review.status} · TIER {review.tier}</span><h3>{review.rawTitle}</h3><p className="muted">Inserat {review.marketPlaceItemId} · Score {(review.bestScore * 100).toFixed(1)}%</p></div>{review.adUrl && <a className="review-link" href={review.adUrl} target="_blank" rel="noreferrer">Inserat öffnen ↗</a>}</div><div className="review-meta"><span><small>Vorgeschlagene EAN</small><strong>{review.matchedEan || "—"}</strong></span><span><small>Vorgeschlagene MPN</small><strong>{review.matchedMpn || "—"}</strong></span><span><small>Bearbeitet von</small><strong>{review.confirmedBy || "—"}</strong></span></div>{review.status === "PENDING" && <div className="review-actions"><button className="primary-button" onClick={() => void action(review.id, "confirm", review)}>Bestätigen / korrigieren</button><button className="secondary-button" onClick={() => void action(review.id, "reject", review)}>Ablehnen</button></div>}{review.status === "CONFIRMED" && <div className="review-actions"><button className="secondary-button" onClick={() => void action(review.id, "confirm", review)}>Zuordnung ändern</button></div>}</article>)}</div>}
    </main>;
}

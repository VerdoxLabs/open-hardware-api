-- V17: C2C-Lernschleife (Kleinanzeigen)
-- Review-Queue für fehlerbehaftete C2C-Titel + Matching-Konfidenz auf aktiven Listings.
--
-- Alle Anweisungen idempotent (IF NOT EXISTS) wie V11/V14/V16, damit die Migration
-- sowohl auf einer frischen als auch einer bestehenden Produktion läuft.
-- Dev (H2 + ddl-auto:update) wird von Hibernate unabhängig versorgt.

-- ============================================================
-- 1) Matching-Konfidenz auf aktiven Listings
--    (wie wurde das Listing der Datenbank zugeordnet? "AUTO" = Tier 1 ungeprüft,
--     "C2C" = aus Lernschleife bestätigt, sonst null)
-- ============================================================
ALTER TABLE remote_active_listing ADD COLUMN IF NOT EXISTS match_confidence DOUBLE PRECISION;
ALTER TABLE remote_active_listing ADD COLUMN IF NOT EXISTS match_source VARCHAR(50);

-- ============================================================
-- 2) Review-Queue für C2C-Titel
--    Ein Eintrag = ein Inserat-Titel, dessen Zuordnung noch (menschliche)
--    Bestätigung braucht (Tier 2/3). Nach Bestätigung wird der Titel in die
--    Product-Identity übernommen (Lernschleife) und derselbe Titel wird beim
--    nächsten Lauf ein exakter 1.0-Treffer.
-- ============================================================
CREATE SEQUENCE IF NOT EXISTS c2c_match_review_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS c2c_match_review (
    id                    BIGINT           NOT NULL,
    market_place_item_id  VARCHAR(255)     NOT NULL,
    raw_title             VARCHAR(512)     NOT NULL,
    title_normalized      VARCHAR(512)     NOT NULL,
    best_score            DOUBLE PRECISION NOT NULL DEFAULT 0,
    tier                  INTEGER          NOT NULL DEFAULT 3,
    matched_ean           VARCHAR(255),
    matched_mpn           VARCHAR(255),
    ad_url                VARCHAR(1000),
    status                VARCHAR(255)     NOT NULL DEFAULT 'PENDING',
    confirmed_by          VARCHAR(255),
    created_at            TIMESTAMP        NOT NULL,
    updated_at            TIMESTAMP        NOT NULL,
    CONSTRAINT pk_c2c_match_review PRIMARY KEY (id),
    -- dasselbe Inserat + derselbe (gecleante) Titel nur einmal
    CONSTRAINT uc_c2c_match_review UNIQUE (market_place_item_id, title_normalized)
);

CREATE INDEX IF NOT EXISTS idx_c2c_match_review_status ON c2c_match_review (status);
CREATE INDEX IF NOT EXISTS idx_c2c_match_review_tier ON c2c_match_review (tier);

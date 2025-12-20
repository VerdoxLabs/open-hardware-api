ALTER TABLE cpu
    ADD COLUMN code_name VARCHAR(255);

ALTER TABLE cpucooler
    ADD COLUMN colors JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE cpucooler
    ADD COLUMN fan_rpm DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE cpucooler
    ADD COLUMN noise_level_db INTEGER NOT NULL DEFAULT 0;

ALTER TABLE gpu
    ADD COLUMN colors JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE motherboard
    ADD COLUMN colors JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE pccase
    ADD COLUMN colors JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE psu
    ADD COLUMN colors JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE ram
    ADD COLUMN colors JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE remote_active_listing
    ADD COLUMN merchant_image_url VARCHAR(1000);

alter table remote_active_listing
    drop column if exists price;
alter table remote_active_listing
    drop column if exists currency;
alter table remote_active_listing
    drop column if exists condition;
alter table remote_active_listing
    drop column if exists shipping_price;
alter table remote_active_listing
    drop column if exists available_quantity;

create table remote_active_listing_price
(
    uuid          uuid                     not null,
    listing_uuid  uuid                     not null,
    snapshot_date date                     not null,
    captured_at   timestamp with time zone not null,
    condition     VARCHAR(255),
    price         numeric(18, 2),
    primary key (uuid),
    constraint fk_ralp_listing foreign key (listing_uuid)
        references remote_active_listing (uuid)
);

create unique index ux_ralp_listing_date
    on remote_active_listing_price (listing_uuid, snapshot_date);

create index idx_ralp_date
    on remote_active_listing_price (snapshot_date);

create index idx_ralp_captured_at
    on remote_active_listing_price (captured_at);
-- H2's enum representation (used by the legacy dev schema) can surface DE as 13.
-- Production databases normally already contain the string value and are unaffected.
UPDATE remote_active_listing SET primary_region = 'DE' WHERE primary_region = '13';

-- Stock groups moved to DynamoDB; snapshots and group history keep the group key as a plain column.
-- The CHECK constraints stay. The stock_group table itself is removed together with Postgres.
ALTER TABLE company_snapshot DROP CONSTRAINT IF EXISTS company_snapshot_group_key_fkey;
ALTER TABLE stock_group_history DROP CONSTRAINT IF EXISTS stock_group_history_previous_group_key_fkey;
ALTER TABLE stock_group_history DROP CONSTRAINT IF EXISTS stock_group_history_new_group_key_fkey;

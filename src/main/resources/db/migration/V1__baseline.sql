-- V1 Baseline: take over schema ownership from Django.
-- Renames Django-style table names to clean MSA conventions.
-- Safe for both fresh cluster deployments and migration from an existing Django-managed DB.

-- ─── account (was accounts_accountmodel) ──────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'accounts_accountmodel') THEN
        ALTER TABLE accounts_accountmodel RENAME TO account;
    ELSIF NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'account') THEN
        CREATE TABLE account (
            id                        BIGSERIAL PRIMARY KEY,
            ory_identity_id           VARCHAR(128) UNIQUE,
            country_code              VARCHAR(2),
            currency                  VARCHAR(3),
            annual_income             NUMERIC(15, 2),
            monthly_investable_amount NUMERIC(15, 2),
            created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
    END IF;
END $$;

-- Drop the Django auth_user FK orphan; column is gone in the new MSA design.
ALTER TABLE account DROP COLUMN IF EXISTS user_id;

-- ─── outbox_event (was events_outboxevent) ────────────────────────────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'events_outboxevent') THEN
        ALTER TABLE events_outboxevent RENAME TO outbox_event;
    ELSIF NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'outbox_event') THEN
        CREATE TABLE outbox_event (
            id             BIGSERIAL PRIMARY KEY,
            event_id       VARCHAR(36)  UNIQUE NOT NULL,
            aggregate_type VARCHAR(50)  NOT NULL,
            aggregate_id   VARCHAR(50)  NOT NULL,
            event_type     VARCHAR(100) NOT NULL,
            payload        JSONB        NOT NULL,
            created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
            published      BOOLEAN      NOT NULL DEFAULT FALSE
        );
    END IF;
END $$;

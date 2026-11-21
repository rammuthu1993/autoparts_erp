# Production database roles and Flyway rollout

## Required identities

Use three distinct PostgreSQL logins. Never run the ERP application as `postgres`.

| Login | Purpose | Required access |
|---|---|---|
| `autoparts_erp_migrator` | Flyway only | Owns the application schema objects and can run the migrations |
| `autoparts_erp_app` | Spring Boot ERP runtime | DML required by the application; no superuser, CREATEDB, CREATEROLE, or schema DDL |
| `autoparts_ai_reader` | AI lookup datasource | SELECT only on approved business tables; no access to `ai_conversations` / `ai_messages` |

The production profile patch makes `FLYWAY_DB_USERNAME` and `FLYWAY_DB_PASSWORD` mandatory through Spring Boot's `spring.flyway.user/password`. `DB_USERNAME/DB_PASSWORD` remain the ERP runtime login. The AI datasource remains independently configured through `AI_TOOL_DB_*`.

## Role provisioning (run as a DBA)

Create the logins with your approved credential-management process; do not put passwords in this file or source control:

```sql
CREATE ROLE autoparts_erp_migrator LOGIN;
CREATE ROLE autoparts_erp_app LOGIN;
-- autoparts_ai_reader may already exist; retain its existing restricted grants.

GRANT CONNECT ON DATABASE autoparts_erp TO autoparts_erp_migrator;
GRANT CONNECT ON DATABASE autoparts_erp TO autoparts_erp_app;
GRANT CONNECT ON DATABASE autoparts_erp TO autoparts_ai_reader;

GRANT USAGE ON SCHEMA public TO autoparts_erp_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO autoparts_erp_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO autoparts_erp_app;

-- Ensure future objects created by the migration owner are usable by the app role.
ALTER DEFAULT PRIVILEGES FOR ROLE autoparts_erp_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO autoparts_erp_app;
ALTER DEFAULT PRIVILEGES FOR ROLE autoparts_erp_migrator IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO autoparts_erp_app;

-- Reassert the AI reader's sensitive-table deny after any broad grants.
REVOKE ALL PRIVILEGES ON TABLE public.ai_conversations, public.ai_messages
  FROM autoparts_ai_reader;
```

Important: PostgreSQL `GRANT ALL` does not make a role the owner of existing objects, and `ALTER TABLE` requires ownership. Before using `autoparts_erp_migrator` against an existing database, inventory object owners and plan a reviewed ownership transfer for the application schema. Do not blindly run `REASSIGN OWNED` or change ownership of shared objects. Verify every migration operation under the migrator identity on a restored database first.

The AI reader needs explicit SELECT grants only for the business tables queried by the AI tools. Do not grant SELECT on all tables. Revoke table access to chat/conversation tables and verify denial using `SET ROLE` or a fresh connection as `autoparts_ai_reader`.

## Environment variables

Production:

```text
DB_URL=jdbc:postgresql://<host>:5432/autoparts_erp
DB_USERNAME=autoparts_erp_app
DB_PASSWORD=<managed-outside-source-control>
FLYWAY_DB_USERNAME=autoparts_erp_migrator
FLYWAY_DB_PASSWORD=<managed-outside-source-control>
AI_TOOL_DB_URL=jdbc:postgresql://<host>:5432/autoparts_erp
AI_TOOL_DB_USERNAME=autoparts_ai_reader
AI_TOOL_DB_PASSWORD=<managed-outside-source-control>
```

The app runtime login should not own the schema and should not have CREATE, ALTER, DROP, TRUNCATE, REFERENCES, TRIGGER, SUPERUSER, CREATEDB, or CREATEROLE privileges. Validate the effective grants rather than relying only on role names.

## Flyway V30 checksum / already-deployed databases

V30 is a versioned migration. Do not silently rewrite Flyway history at application startup and do not automatically run `repair` in production. First inventory every environment and record its applied V30 checksum. Test the intended upgrade on a restored copy of each schema state.

- Fresh databases must run the corrected migration chain from V1.
- Databases with V29 applied but V30 not yet applied should run the corrected V30 normally.
- Databases that already applied the earlier V30 content have a checksum mismatch after the file changes. Handle each such environment through a separately approved, documented maintenance procedure, with a backup and before/after `flyway info` output. Do not blindly update `flyway_schema_history` checksums; prefer a forward-only corrective migration for future changes and retain the exact old/new migration artifacts for audit.

The V30 patch scopes constraint existence checks to `public.ai_agent_runs`, avoiding a false match if another table has a same-named constraint.

## Destructive migration tests

`FlywayFreshMigrationVerificationTest` drops and recreates the `public` schema. The patch therefore disables both destructive tests by default. To run them intentionally, use only the dedicated `autoparts_erp_flyway_test` database and explicitly set:

```text
-D run.destructive.flyway.tests=true
-DTEST_DB_URL=jdbc:postgresql://localhost:5432/autoparts_erp_flyway_test
-DTEST_DB_USERNAME=<dedicated-test-db-owner>
-DTEST_DB_PASSWORD=<test-only-password>
```

Pass JVM properties without the space after `-D` in Maven, for example `-Drun.destructive.flyway.tests=true`. The test validates the connected database name and exact URL before either schema drop. Never point these settings at a development, staging, or production database. Prefer a disposable database restored/created specifically for this run.

Production-profile integration tests use `DB_USERNAME` for the app role and assert the runtime identity is not `postgres`. The Flyway-enabled test separately requires `FLYWAY_DB_USERNAME/PASSWORD`.

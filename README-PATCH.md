# Phase 5 test-safety patch

This ZIP contains three replacement test classes at the ZIP root. Copy the `src` tree into
the existing `autoparts-erp` project and allow the files to overwrite the matching test classes.
No production source, migration, POM, or secret file is included.

## What changed

1. `FlywayFreshMigrationVerificationTest`
   - Destructive tests remain opt-in through `run.destructive.flyway.tests=true`.
   - Requires explicit `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`.
   - Allows only localhost / 127.0.0.1 and the exact database `autoparts_erp_flyway_test`.
   - Rejects the default `postgres` role and any superuser role before dropping `public`.
   - Validates the connected database name before each schema reset.
   - Validates the database role identifier before using it in a GRANT statement.

2. `ProductionProfileStartupIntegrationTest`
   - Opt-in through `run.production.profile.tests=true`.
   - No static credential lookup / class-initialization failure.
   - Requires an explicitly configured, non-`postgres` ERP runtime role.
   - Verifies the ERP runtime and AI reader roles are not PostgreSQL superusers.
   - Continues checking AI reader read-only access and denied chat-table access.

3. `ProductionProfileFlywayStartupIntegrationTest`
   - Opt-in through `run.production.profile.tests=true`.
   - Requires distinct `DB_USERNAME` and `FLYWAY_DB_USERNAME`.
   - Requires separate Flyway credentials; no fallback to the ERP runtime account.
   - Rejects `postgres` and verifies both ERP runtime and Flyway roles are non-superusers.

## Normal regression suite

Run the normal suite without any opt-in properties:

```powershell
.\mvnw.cmd -o clean test
```

The two production-profile test classes and two destructive Flyway test methods should be skipped by default. Other existing tests continue to run.

## Production-profile verification (non-destructive)

Set the test credentials in the current PowerShell session. The password variables may be loaded
from your existing local secret configuration; this patch does not modify secret management.

```powershell
$env:DB_USERNAME = "autoparts_app"
$env:DB_PASSWORD = "<ERP runtime test password>"
$env:AI_TOOL_DB_PASSWORD = "<AI reader test password>"
$env:FLYWAY_DB_USERNAME = "autoparts_migrator"
$env:FLYWAY_DB_PASSWORD = "<Flyway migration test password>"
```

Run the two production-profile tests:

```powershell
.\mvnw.cmd --% -o -Drun.production.profile.tests=true -Dtest=ProductionProfileStartupIntegrationTest,ProductionProfileFlywayStartupIntegrationTest test
```

The production startup test uses `autoparts_erp_test`. The Flyway-enabled startup test uses
`autoparts_erp_flyway_test`. Both URLs are fixed to localhost in the test classes.

## Destructive Flyway verification

Only run this against a disposable local PostgreSQL database named exactly
`autoparts_erp_flyway_test`. The configured test role must be a non-superuser with the necessary
database/schema ownership and DDL privileges to drop and recreate the `public` schema.

```powershell
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/autoparts_erp_flyway_test"
$env:TEST_DB_USERNAME = "autoparts_migrator"
$env:TEST_DB_PASSWORD = "<dedicated migration test password>"
.\mvnw.cmd --% -o -Drun.destructive.flyway.tests=true -Dtest=FlywayFreshMigrationVerificationTest test
```

This test intentionally drops and recreates the `public` schema in that dedicated database.
Do not point it at a development, shared, staging, or production database.

## Expected default-suite result

The normal suite should finish with zero failures and zero errors. The skip count will increase
because the two production-profile integration tests are now opt-in in addition to the two
destructive Flyway test methods. Run the opt-in commands above to verify those tests separately.

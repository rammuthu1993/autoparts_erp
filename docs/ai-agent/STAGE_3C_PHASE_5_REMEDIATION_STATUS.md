# Stage 3C Phase 5 remediation status

## Changes applied in this package

1. Live provider evaluation is opt-in via `AI_LIVE_EVALUATION_ENABLED`; `application.properties` now defaults it to `false`.
2. The grounded generation prompt now has one system instruction message. Retrieved chunks and the current question are sent together as a user message, so retrieved document text is not elevated to a second system message.
3. Citation validation recognizes only exact, canonical abstention sentences. A factual sentence appended after an abstention is no longer accepted as an abstention.
4. Retrieved text sanitization escapes ampersands before angle brackets to avoid malformed/double-escaped entity sequences.
5. Empty SARVAM output with `finishReason=LENGTH` now produces a specific token-budget exhaustion exception message.
6. A test was added for abstention-prefix abuse and for ampersand escaping. The prompt-injection test now asserts that reference data is in a user message, not a system message.
7. The distributable source ZIP excludes the local `config/application-secrets.properties` file and includes `config/application-secrets.example.properties`. `.gitignore` excludes the local secrets file and environment files. The distributable ZIP also excludes `.git`, build output, and runtime logs.

## Important security follow-up

The original uploaded archive contained a local `config/application-secrets.properties` file. Its database password and JWT signing secret must be rotated. The SARVAM key was not present in that local file, but rotate it too if it was ever committed or otherwise exposed. The included Git history shows earlier commits containing `config/application-secrets.properties`; `.gitignore` does not erase those historical blobs. If this repository has been pushed to a remote, rotate credentials first, then rewrite the remote Git history and coordinate the force-push with all collaborators. The distributable ZIP intentionally omits `.git` so historical secret blobs are not repackaged.

## Remaining work — not claimed as implemented

The current `CitationValidator` verifies citation provenance against retrieved chunks. It does not prove semantic entailment between a claim and a cited passage. The current live evaluator also uses citation validation and lexical expected-fact anchors; it must not be represented as a semantic fact-checker.

Before Phase 5 can pass, implement and test a claim-to-evidence evaluation stage that:

- segments answers into atomic factual claims;
- associates each claim with exact retrieved evidence identifiers;
- evaluates whether the evidence entails each claim;
- detects incompatible values and contradictory claims;
- reports citation provenance separately from semantic support;
- measures expected-fact coverage against human-authored annotations;
- fails closed on missing or uncertain evidence.

A semantic evaluator (human annotation or a separately validated entailment model) is required for the semantic-support metric. Do not use a few keyword/number regexes as proof of entailment.

The SARVAM `finishReason=LENGTH` case also remains a live-provider verification task. Confirm the provider's actual output-token and reasoning-token semantics, then run the failing case with a shorter prompt. Do not increase `AI_MAX_TOKENS` without confirming the supported limit.

## Verification limitation

The source-level configuration and packaging corrections in this revision have not been validated by a Java 25 Maven run in this environment. Run `./mvnw.cmd clean test` and `./mvnw.cmd verify` on the project Java 25 Windows environment before merging. The previous uploaded test log reported 502 tests with zero failures, errors, or skips; that result predates the configuration/packaging-only edits in this revision. It does not validate live SARVAM behavior.

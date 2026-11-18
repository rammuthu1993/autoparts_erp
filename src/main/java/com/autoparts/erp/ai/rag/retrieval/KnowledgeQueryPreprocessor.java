package com.autoparts.erp.ai.rag.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Stage 3C Phase 4, Task 1: conservative preprocessing for knowledge queries.
 *
 * <p>Important semantic rule: natural-language negation is metadata, NOT PostgreSQL
 * {@code websearch_to_tsquery} exclusion syntax. A query such as
 * "alternator not charging" must not become {@code alternator -charging}, because
 * that would exclude documents that contain the word "charging" (including the
 * documents that explain an alternator-not-charging condition).</p>
 *
 * <p>For constrained queries this class deliberately emits no executable phrase,
 * Boolean-AND, or OR query. A later retrieval layer must explicitly decide how to
 * retrieve constrained questions while retaining their negation metadata.</p>
 */
@Component
public class KnowledgeQueryPreprocessor {

    public record PreprocessedQuery(
            String rawQuery,
            String normalizedText,
            String exactPhraseText,
            String exactPhraseWebsearch,
            String booleanAndQuery,
            String orQuery,
            List<String> keywords,
            boolean hasNegation,
            List<String> negationTerms,
            boolean hasExclusion,
            List<String> exclusionTerms,
            List<String> positiveTerms,
            List<String> excludedScopeTerms,
            boolean constraintScopeKnown
    ) {
        public boolean isEmpty() {
            return keywords == null || keywords.isEmpty() || !StringUtils.hasText(normalizedText);
        }

        public boolean hasPhrase() {
            return StringUtils.hasText(exactPhraseText);
        }

        public boolean hasOrQuery() {
            return StringUtils.hasText(orQuery);
        }

        public boolean isConstrained() {
            return hasNegation || hasExclusion;
        }

        /** True only when ordinary positive-query tiers can safely execute as emitted. */
        public boolean isSafeForStandardRetrieval() {
            return !isEmpty() && !isConstrained() && constraintScopeKnown
                    && StringUtils.hasText(booleanAndQuery);
        }

        /** A constrained query without any unnegated anchor cannot use ordinary retrieval tiers. */
        public boolean isPureExclusion() {
            return isConstrained() && positiveTerms.isEmpty() && !excludedScopeTerms.isEmpty();
        }
    }

    private static final List<Pattern> CONVERSATIONAL_PREFIX_PATTERNS = List.of(
            Pattern.compile("(?i)^\\s*(could|can)\\s+you\\s+(please\\s+)?(tell|explain|show|provide)\\s+(to\\s+)?(me\\s+)?(about\\s+)?"),
            Pattern.compile("(?i)^\\s*(please\\s+)?(tell|explain|show|provide)\\s+(to\\s+)?(me\\s+)?(about\\s+)?"),
            Pattern.compile("(?i)^\\s*i\\s+(would\\s+like|want|need)\\s+to\\s+(know|understand|find\\s+out)\\s+(about\\s+)?"),
            Pattern.compile("(?i)^\\s*(how\\s+can|how\\s+do|how\\s+does|how\\s+should)\\s+(a\\s+customer|a\\s+cashier|a\\s+user|the\\s+customer|they|we|i)\\s+"),
            Pattern.compile("(?i)^\\s*(what\\s+is|what\\s+are|what\\s+does|what\\s+do|what\\s+should)\\s+"),
            Pattern.compile("(?i)^\\s*(how\\s+to)\\s+")
    );

    private static final Pattern POLITE_TRAILING_PATTERN =
            Pattern.compile("(?i)[,\\s]+(thank\\s+you|thanks|please|kindly)[!?.\\s]*$");

    private static final Set<String> NEGATION_TOKENS = Set.of(
            "not", "never", "without", "no", "neither", "nor", "non"
    );
    private static final Set<String> EXCLUSION_OPERATOR_TOKENS = Set.of("except", "excluding", "omitting");
    private static final Set<String> ARTICLES = Set.of("a", "an", "the");
    private static final Set<String> POSITIVE_REENTRY_TOKENS = Set.of(
            "with", "having", "including", "plus", "requiring"
    );

    public PreprocessedQuery preprocess(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return empty(rawQuery);
        }

        String text = rawQuery.trim().replaceAll("\\s+", " ");
        boolean changed;
        do {
            changed = false;
            for (Pattern pattern : CONVERSATIONAL_PREFIX_PATTERNS) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    text = matcher.replaceFirst("").trim();
                    changed = true;
                    break;
                }
            }
        } while (changed);

        text = POLITE_TRAILING_PATTERN.matcher(text).replaceFirst("").trim();
        text = text.replaceAll("(?<=\\d)\\.(?=\\d)", "___DECIMAL___");
        text = text.replaceAll("(?<=[a-zA-Z0-9])-(?=[a-zA-Z0-9])", "___HYPHEN___");
        text = text.replaceAll("[,;:?/\\\\|\\\"'\\[\\]{}()!@#$%^&*~+=<>]", " ");
        text = text.replaceAll("-", " ");
        text = text.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        text = text.replace("___DECIMAL___", ".").replace("___HYPHEN___", "-");
        text = text.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return empty(rawQuery);
        }

        List<String> tokens = new ArrayList<>();
        for (String token : text.split("\\s+")) {
            String normalized = token.toLowerCase(Locale.ROOT)
                    .replaceAll("^[^a-z0-9_-]+|[^a-z0-9_-]+$", "");
            if (!normalized.isEmpty() && !ARTICLES.contains(normalized)) {
                tokens.add(normalized);
            }
        }
        if (tokens.isEmpty()) {
            return empty(rawQuery);
        }

        List<String> keywords = immutableDistinct(tokens);
        List<String> positiveTerms = new ArrayList<>();
        List<String> negatedTerms = new ArrayList<>();
        List<String> negationOperators = new ArrayList<>();
        List<String> exclusionOperators = new ArrayList<>();
        boolean hasNegation = false;
        boolean hasExclusion = false;
        boolean scopeKnown = true;

        // A one-token scope for "not" and "no"; a bounded phrase scope for
        // "without", "never", and explicit exclusion operators.
        int scope = 0; // 0=positive, 1=next token, 2=until positive re-entry/end
        boolean awaitingNeitherLeft = false;
        boolean awaitingNeitherRight = false;
        boolean neitherLeftFound = false;
        boolean sawNeither = false;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (EXCLUSION_OPERATOR_TOKENS.contains(token)) {
                hasExclusion = true;
                exclusionOperators.add(token);
                if (scope != 0) scopeKnown = false;
                scope = 2;
                continue;
            }

            if ("neither".equals(token)) {
                hasNegation = true;
                negationOperators.add(token);
                if (sawNeither || awaitingNeitherLeft || awaitingNeitherRight) scopeKnown = false;
                sawNeither = true;
                awaitingNeitherLeft = true;
                scope = 0;
                continue;
            }

            if ("nor".equals(token)) {
                hasNegation = true;
                negationOperators.add(token);
                if (!sawNeither || !neitherLeftFound || awaitingNeitherLeft) {
                    scopeKnown = false;
                }
                awaitingNeitherLeft = false;
                awaitingNeitherRight = true;
                scope = 0;
                continue;
            }

            if ("not".equals(token) || "no".equals(token)) {
                hasNegation = true;
                negationOperators.add(token);
                if (scope != 0 || awaitingNeitherLeft || awaitingNeitherRight) scopeKnown = false;
                scope = 1;
                continue;
            }

            if ("never".equals(token) || "without".equals(token)) {
                hasNegation = true;
                negationOperators.add(token);
                if (scope != 0 || awaitingNeitherLeft || awaitingNeitherRight) scopeKnown = false;
                scope = 2;
                continue;
            }

            if ("non".equals(token)) {
                hasNegation = true;
                negationOperators.add(token);
                // "non" as a standalone token has no reliable scope without a
                // hyphenated compound; do not guess which following words it negates.
                scopeKnown = false;
                scope = 1;
                continue;
            }

            if (awaitingNeitherLeft) {
                negatedTerms.add(token);
                awaitingNeitherLeft = false;
                neitherLeftFound = true;
                continue;
            }
            if (awaitingNeitherRight) {
                negatedTerms.add(token);
                awaitingNeitherRight = false;
                continue;
            }

            // "and with receipt" / "but with receipt" closes the previous
            // without/exclusion scope before the new positive condition.
            if (scope == 2 && ("and".equals(token) || "but".equals(token))) {
                if (i + 1 < tokens.size() && POSITIVE_REENTRY_TOKENS.contains(tokens.get(i + 1))) {
                    continue;
                }
            }
            if (scope == 2 && POSITIVE_REENTRY_TOKENS.contains(token)) {
                scope = 0;
                continue;
            }

            if (scope == 1) {
                negatedTerms.add(token);
                scope = 0;
            } else if (scope == 2) {
                // Conjunctions inside a bounded negative phrase are syntax, not
                // useful search terms. Keep other terms in the negative scope.
                if (!"and".equals(token) && !"or".equals(token) && !"but".equals(token)) {
                    negatedTerms.add(token);
                }
            } else if (!"and".equals(token) && !"or".equals(token) && !"but".equals(token)) {
                positiveTerms.add(token);
            }
        }

        if (awaitingNeitherLeft || awaitingNeitherRight) {
            scopeKnown = false;
        }
        if (sawNeither && !negationOperators.contains("nor")) {
            scopeKnown = false;
        }

        String normalizedText = String.join(" ", keywords);
        boolean constrained = hasNegation || hasExclusion;
        String exactPhraseText = constrained ? "" : normalizedText;
        String exactPhraseWebsearch = constrained || exactPhraseText.isBlank()
                ? "" : "\"" + exactPhraseText + "\"";

        // Constrained natural-language queries are intentionally non-executable
        // at this layer. Never convert negated words into PostgreSQL -term syntax.
        String booleanAndQuery = constrained || positiveTerms.isEmpty()
                ? "" : String.join(" ", positiveTerms);
        String orQuery = constrained || positiveTerms.isEmpty()
                ? "" : String.join(" OR ", positiveTerms);

        return new PreprocessedQuery(
                rawQuery,
                normalizedText,
                exactPhraseText,
                exactPhraseWebsearch,
                booleanAndQuery,
                orQuery,
                Collections.unmodifiableList(keywords),
                hasNegation,
                Collections.unmodifiableList(negationOperators),
                hasExclusion,
                Collections.unmodifiableList(exclusionOperators),
                immutableDistinct(positiveTerms),
                immutableDistinct(negatedTerms),
                scopeKnown
        );
    }

    private static List<String> immutableDistinct(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(values)));
    }

    private static PreprocessedQuery empty(String rawQuery) {
        return new PreprocessedQuery(
                rawQuery == null ? "" : rawQuery, "", "", "", "", "",
                List.of(), false, List.of(), false, List.of(), List.of(), List.of(), true
        );
    }
}

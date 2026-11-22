package com.autoparts.erp.common.util;

import com.autoparts.erp.common.exception.ClientValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaginationUtils Specification Tests")
class PaginationUtilsTest {

    @Nested
    @DisplayName("Page Size Normalization")
    class PageSizeNormalizationTests {

        @ParameterizedTest(name = "normalizeSize({0}) should default to 20")
        @ValueSource(ints = {0, -1, -10, -100})
        void nonPositiveSizeDefaultsToTwenty(int rawSize) {
            assertThat(PaginationUtils.normalizeSize(rawSize))
                    .isEqualTo(PaginationUtils.DEFAULT_PAGE_SIZE);
        }

        @ParameterizedTest(name = "normalizeSize({0}) should clamp to 100")
        @ValueSource(ints = {101, 150, 500, 10000, Integer.MAX_VALUE})
        void oversizedSizeClampsToOneHundred(int rawSize) {
            assertThat(PaginationUtils.normalizeSize(rawSize))
                    .isEqualTo(PaginationUtils.MAX_PAGE_SIZE);
        }

        @ParameterizedTest(name = "normalizeSize({0}) should preserve valid range")
        @ValueSource(ints = {1, 5, 20, 50, 99, 100})
        void validSizesPreserved(int rawSize) {
            assertThat(PaginationUtils.normalizeSize(rawSize))
                    .isEqualTo(rawSize);
        }
    }

    @Nested
    @DisplayName("Normal and Edge Boundary Offset Calculation")
    class NormalAndBoundaryCalculationTests {

        @ParameterizedTest(name = "calculateOffset(page={0}, size={1}) -> offset={2}")
        @CsvSource({
                "0, 1, 0",
                "0, 20, 0",
                "0, 100, 0",
                "1, 20, 20",
                "2, 20, 40",
                "5, 10, 50",
                "10, 50, 500",
                "99, 100, 9900"
        })
        void standardPageAndSizeCalculations(int page, int size, int expectedOffset) {
            int offset = PaginationUtils.calculateOffset(page, size);
            assertThat(offset).isEqualTo(expectedOffset);
            assertThat(offset).isNotNegative();
        }

        @ParameterizedTest(name = "calculateOffset(page={0}, size={1}) should clamp negative page to 0")
        @CsvSource({
                "-1, 20, 0",
                "-5, 50, 0",
                "-1000, 100, 0",
                "-2147483648, 20, 0"
        })
        void negativePagesTreatedAsPageZero(int negativePage, int size, int expectedOffset) {
            int offset = PaginationUtils.calculateOffset(negativePage, size);
            assertThat(offset).isEqualTo(expectedOffset);
            assertThat(offset).isNotNegative();
        }

        @Test
        @DisplayName("Maximum safe page for size=1 reaches Integer.MAX_VALUE without overflow")
        void maxSafePageForSizeOne() {
            int offset = PaginationUtils.calculateOffset(Integer.MAX_VALUE, 1);
            assertThat(offset).isEqualTo(Integer.MAX_VALUE);
            assertThat(offset).isPositive();
        }

        @Test
        @DisplayName("Maximum safe page for size=20 reaches 2,147,483,640 without overflow")
        void maxSafePageForSizeTwenty() {
            int maxSafePage = Integer.MAX_VALUE / 20; // 107374182
            int offset = PaginationUtils.calculateOffset(maxSafePage, 20);
            assertThat(offset).isEqualTo(107374182 * 20);
            assertThat(offset).isEqualTo(2147483640);
            assertThat(offset).isPositive();
        }

        @Test
        @DisplayName("Maximum safe page for size=100 reaches 2,147,483,600 without overflow")
        void maxSafePageForSizeOneHundred() {
            int maxSafePage = Integer.MAX_VALUE / 100; // 21474836
            int offset = PaginationUtils.calculateOffset(maxSafePage, 100);
            assertThat(offset).isEqualTo(21474836 * 100);
            assertThat(offset).isEqualTo(2147483600);
            assertThat(offset).isPositive();
        }
    }

    @Nested
    @DisplayName("Integer Overflow Protection and Error Handling")
    class OverflowProtectionTests {

        @Test
        @DisplayName("Offset multiplication exceeding Integer.MAX_VALUE with size=20 throws ClientValidationException")
        void offsetOverflowWithSizeTwentyThrowsClientValidationException() {
            int overflowingPage = (Integer.MAX_VALUE / 20) + 1; // 107374183
            assertThatThrownBy(() -> PaginationUtils.calculateOffset(overflowingPage, 20))
                    .isInstanceOf(ClientValidationException.class)
                    .hasMessageContaining("Pagination offset exceeds maximum supported limit");
        }

        @Test
        @DisplayName("Offset multiplication exceeding Integer.MAX_VALUE with size=100 throws ClientValidationException")
        void offsetOverflowWithSizeOneHundredThrowsClientValidationException() {
            int overflowingPage = (Integer.MAX_VALUE / 100) + 1; // 21474837
            assertThatThrownBy(() -> PaginationUtils.calculateOffset(overflowingPage, 100))
                    .isInstanceOf(ClientValidationException.class)
                    .hasMessageContaining("Pagination offset exceeds maximum supported limit");
        }

        @Test
        @DisplayName("page=Integer.MAX_VALUE with size=20 throws ClientValidationException")
        void maxIntPageWithSizeTwentyThrowsClientValidationException() {
            assertThatThrownBy(() -> PaginationUtils.calculateOffset(Integer.MAX_VALUE, 20))
                    .isInstanceOf(ClientValidationException.class)
                    .hasMessageContaining("Pagination offset exceeds maximum supported limit");
        }

        @Test
        @DisplayName("page=Integer.MAX_VALUE with size=100 throws ClientValidationException")
        void maxIntPageWithSizeOneHundredThrowsClientValidationException() {
            assertThatThrownBy(() -> PaginationUtils.calculateOffset(Integer.MAX_VALUE, 100))
                    .isInstanceOf(ClientValidationException.class)
                    .hasMessageContaining("Pagination offset exceeds maximum supported limit");
        }

        @Test
        @DisplayName("page=Integer.MAX_VALUE with size=2 throws ClientValidationException")
        void maxIntPageWithSizeTwoThrowsClientValidationException() {
            assertThatThrownBy(() -> PaginationUtils.calculateOffset(Integer.MAX_VALUE, 2))
                    .isInstanceOf(ClientValidationException.class)
                    .hasMessageContaining("Pagination offset exceeds maximum supported limit");
        }

        @Test
        @DisplayName("calculateOffsetForNormalized validates and rejects overflowing inputs")
        void calculateOffsetForNormalizedThrowsOnOverflow() {
            assertThatThrownBy(() -> PaginationUtils.calculateOffsetForNormalized(Integer.MAX_VALUE, 2))
                    .isInstanceOf(ClientValidationException.class)
                    .hasMessageContaining("Pagination offset exceeds maximum supported limit");
        }
    }
}

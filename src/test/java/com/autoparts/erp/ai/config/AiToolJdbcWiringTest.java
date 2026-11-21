package com.autoparts.erp.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolJdbcWiringTest {

    private ApplicationContextRunner contextRunner(String profile) {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        AiToolJdbcConfiguration.class,
                        WiringTestConfiguration.class
                )
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues(
                        "spring.datasource.url=jdbc:postgresql://localhost:5432/autoparts_erp",
                        "spring.datasource.username=app_test",
                        "spring.datasource.password=test-only",
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "app.ai.datasource.url=jdbc:postgresql://localhost:5432/autoparts_erp",
                        "app.ai.datasource.username=autoparts_ai_reader",
                        "app.ai.datasource.password=test-only"
                );
    }

    @Test
    void developmentProfileUsesSeparatePrimaryAndAiReaderTemplates() {
        contextRunner("dev").run(context -> {
            assertThat(context).hasNotFailed();

            HikariDataSource primary =
                    context.getBean("dataSource", HikariDataSource.class);

            HikariDataSource reader =
                    context.getBean("aiToolDataSource", HikariDataSource.class);

            NamedParameterJdbcTemplate primaryTemplate =
                    context.getBean("namedParameterJdbcTemplate",
                            NamedParameterJdbcTemplate.class);

            NamedParameterJdbcTemplate readerTemplate =
                    context.getBean("aiToolNamedParameterJdbcTemplate",
                            NamedParameterJdbcTemplate.class);

            assertThat(primary).isNotSameAs(reader);
            assertThat(primaryTemplate.getJdbcTemplate().getDataSource())
                    .isSameAs(primary);
            assertThat(readerTemplate.getJdbcTemplate().getDataSource())
                    .isSameAs(reader);

            assertThat(context.getBean(NamedParameterJdbcTemplate.class))
                    .isSameAs(primaryTemplate);

            assertThat(reader.isReadOnly()).isTrue();
        });
    }

    @Test
    void productionProfileUsesSeparatePrimaryAndAiReaderTemplates() {
        contextRunner("prod").run(context -> {
            assertThat(context).hasNotFailed();

            HikariDataSource primary =
                    context.getBean("dataSource", HikariDataSource.class);

            HikariDataSource reader =
                    context.getBean("aiToolDataSource", HikariDataSource.class);

            NamedParameterJdbcTemplate primaryTemplate =
                    context.getBean("namedParameterJdbcTemplate",
                            NamedParameterJdbcTemplate.class);

            NamedParameterJdbcTemplate readerTemplate =
                    context.getBean("aiToolNamedParameterJdbcTemplate",
                            NamedParameterJdbcTemplate.class);

            assertThat(primary).isNotSameAs(reader);
            assertThat(primaryTemplate.getJdbcTemplate().getDataSource())
                    .isSameAs(primary);
            assertThat(readerTemplate.getJdbcTemplate().getDataSource())
                    .isSameAs(reader);

            assertThat(context.getBean(NamedParameterJdbcTemplate.class))
                    .isSameAs(primaryTemplate);

            assertThat(reader.isReadOnly()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class WiringTestConfiguration {

        @Bean
        TemplateSelection templateSelection(
                NamedParameterJdbcTemplate primary,
                @Qualifier("aiToolNamedParameterJdbcTemplate")
                NamedParameterJdbcTemplate reader) {
            return new TemplateSelection(primary, reader);
        }
    }

    record TemplateSelection(
            NamedParameterJdbcTemplate primary,
            NamedParameterJdbcTemplate reader) {
    }
}
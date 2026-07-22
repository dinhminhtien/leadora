package com.novax.leadora.infrastructure.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiles every declared JPQL query without touching a database.
 *
 * <p>Queries in {@code @Query} annotations are invisible to the Java compiler — a typo in a path,
 * a constructor expression whose signature no longer matches its record, or an aggregate returning
 * the wrong type all pass {@code mvn compile} and only fail when Spring Data builds the repository
 * proxies, i.e. at application startup. That is a slow and expensive place to find out.
 *
 * <p>Spring Data parses and validates declared queries eagerly when it creates each repository
 * bean, and Hibernate can build its metamodel from the annotated entities alone. The properties
 * below remove the only remaining need for a live server: metadata access is turned off (so the
 * dialect is taken from configuration rather than probed over JDBC) and Hikari is told not to fail
 * when its first connection attempt does. The context therefore starts, every query is compiled,
 * and nothing ever connects.
 *
 * <p>{@code NOT_SUPPORTED} suspends the transaction {@code @DataJpaTest} would otherwise open,
 * since opening one would require the connection we are deliberately not making.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/query-compilation-only",
        "spring.datasource.username=unused",
        "spring.datasource.password=unused",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.sql.init.mode=never",
})
class ChatQueryCompilationTest {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AiChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("every declared query compiles, including the chat snapshot projections")
    void declaredQueriesCompile() {
        // Reaching this point means Spring Data parsed and validated the @Query of every method on
        // these repositories — the constructor expressions into the chat DTO records included.
        assertThat(leadRepository).isNotNull();
        assertThat(dealRepository).isNotNull();
        assertThat(taskRepository).isNotNull();
        assertThat(chatMessageRepository).isNotNull();
    }
}

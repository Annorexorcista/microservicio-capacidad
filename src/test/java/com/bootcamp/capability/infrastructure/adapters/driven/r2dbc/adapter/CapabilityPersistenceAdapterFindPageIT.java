package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.adapter;

import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.domain.model.CapabilityPageQuery;
import com.bootcamp.capability.domain.model.CapabilitySortBy;
import com.bootcamp.capability.domain.model.CapabilitySortDirection;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.mapper.CapabilityEntityMapper;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityRepository;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityTechnologyRepository;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración del listado paginado y ordenado del
 * {@link CapabilityPersistenceAdapter} contra una base de datos MySQL real
 * levantada con Testcontainers.
 *
 * <p>Verifica que el ordenamiento y la paginación se resuelven en la base de
 * datos (ORDER BY + LIMIT/OFFSET) y que {@code findPage} recupera las
 * asociaciones de tecnología de cada capacidad de la página:
 * <ul>
 *   <li>orden por {@code name} ascendente y descendente (Req 2.1, 3.1, 3.2, 6.1);</li>
 *   <li>orden por {@code technologyCount} ascendente y descendente, incluyendo una
 *       capacidad con 0 tecnologías via LEFT JOIN (Req 2.2, 6.3);</li>
 *   <li>LIMIT/OFFSET devuelven solo la ventana solicitada (Req 1.1, 6.2);</li>
 *   <li>{@code countAll} devuelve el total (Req 1.2).</li>
 * </ul>
 *
 * <p><b>Requirements: 1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 6.1, 6.2, 6.3</b>
 *
 * <p>Se usa un {@code GenericContainer<>("mysql:8.0")} (NO {@code MySQLContainer})
 * porque el proyecto es puramente R2DBC y no tiene el driver JDBC en el classpath
 * de test. Requiere Docker en ejecución.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapabilityPersistenceAdapterFindPageIT {

    private static final int MYSQL_PORT = 3306;
    private static final String DB_NAME = "capability_db";
    private static final String DB_USER = "test";
    private static final String DB_PASSWORD = "test";

    private static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0")
            .withEnv("MYSQL_DATABASE", DB_NAME)
            .withEnv("MYSQL_USER", DB_USER)
            .withEnv("MYSQL_PASSWORD", DB_PASSWORD)
            .withEnv("MYSQL_ROOT_PASSWORD", "root")
            .withExposedPorts(MYSQL_PORT)
            .waitingFor(Wait.forLogMessage(".*port: 3306  MySQL Community Server.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(180)));

    private ConnectionFactory connectionFactory;
    private ICapabilityRepository capabilityRepository;
    private ICapabilityTechnologyRepository capabilityTechnologyRepository;
    private CapabilityPersistenceAdapter adapter;

    @BeforeAll
    void startContainer() {
        MYSQL.start();

        this.connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "mysql")
                .option(HOST, MYSQL.getHost())
                .option(PORT, MYSQL.getMappedPort(MYSQL_PORT))
                .option(USER, DB_USER)
                .option(PASSWORD, DB_PASSWORD)
                .option(DATABASE, DB_NAME)
                .build());

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new org.springframework.core.io.ClassPathResource("schema.sql"));
        populator.populate(connectionFactory).block();

        R2dbcEntityTemplate entityTemplate = new R2dbcEntityTemplate(connectionFactory);
        R2dbcRepositoryFactory repositoryFactory = new R2dbcRepositoryFactory(entityTemplate);
        this.capabilityRepository = repositoryFactory.getRepository(ICapabilityRepository.class);
        this.capabilityTechnologyRepository =
                repositoryFactory.getRepository(ICapabilityTechnologyRepository.class);

        R2dbcTransactionManager transactionManager = new R2dbcTransactionManager(connectionFactory);
        TransactionalOperator transactionalOperator = TransactionalOperator.create(transactionManager);

        this.adapter = new CapabilityPersistenceAdapter(
                capabilityRepository,
                capabilityTechnologyRepository,
                new CapabilityEntityMapper(),
                transactionalOperator,
                entityTemplate);
    }

    @AfterAll
    void stopContainer() {
        MYSQL.stop();
    }

    @BeforeEach
    void seedData() {
        capabilityTechnologyRepository.deleteAll().block();
        capabilityRepository.deleteAll().block();

        // "Beta" con 3 tecnologías, "Alfa" con 1, "Gamma" con 2, "Delta" con 0.
        adapter.save(new Capability(null, "Beta", "d", List.of(1L, 2L, 3L))).block();
        adapter.save(new Capability(null, "Alfa", "d", List.of(1L))).block();
        adapter.save(new Capability(null, "Gamma", "d", List.of(1L, 2L))).block();
        saveWithoutTechnologies("Delta");
    }

    /** Persiste una capacidad sin asociaciones (0 tecnologías) para ejercitar el LEFT JOIN. */
    private void saveWithoutTechnologies(String name) {
        capabilityRepository
                .save(new CapabilityEntityStub(name))
                .block();
    }

    // Helper minimalista para insertar una capacidad sin tecnologías reutilizando el repositorio.
    private static class CapabilityEntityStub
            extends com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityEntity {
        CapabilityEntityStub(String name) {
            super(null, name, "d");
        }
    }

    private CapabilityPageQuery query(int page, int size,
                                      CapabilitySortBy sortBy, CapabilitySortDirection dir) {
        return new CapabilityPageQuery(page, size, sortBy, dir);
    }

    // --- orden por name (Req 2.1, 6.1) ---

    @Test
    @DisplayName("Orden por name ASC")
    void orderByNameAsc() {
        StepVerifier.create(
                        adapter.findPage(query(0, 10, CapabilitySortBy.NAME, CapabilitySortDirection.ASC))
                                .map(Capability::getName).collectList())
                .assertNext(names -> assertThat(names).containsExactly("Alfa", "Beta", "Delta", "Gamma"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Orden por name DESC")
    void orderByNameDesc() {
        StepVerifier.create(
                        adapter.findPage(query(0, 10, CapabilitySortBy.NAME, CapabilitySortDirection.DESC))
                                .map(Capability::getName).collectList())
                .assertNext(names -> assertThat(names).containsExactly("Gamma", "Delta", "Beta", "Alfa"))
                .verifyComplete();
    }

    // --- orden por technologyCount con LEFT JOIN (Req 2.2, 6.3) ---

    @Test
    @DisplayName("Orden por technologyCount ASC (Delta=0 primero)")
    void orderByTechnologyCountAsc() {
        StepVerifier.create(
                        adapter.findPage(query(0, 10, CapabilitySortBy.TECHNOLOGY_COUNT,
                                        CapabilitySortDirection.ASC))
                                .map(Capability::getName).collectList())
                .assertNext(names -> {
                    // Delta(0) primero, Beta(3) último. Alfa(1) y Gamma(2) en medio.
                    assertThat(names.get(0)).isEqualTo("Delta");
                    assertThat(names.get(names.size() - 1)).isEqualTo("Beta");
                    assertThat(names).containsExactly("Delta", "Alfa", "Gamma", "Beta");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Orden por technologyCount DESC (Beta=3 primero)")
    void orderByTechnologyCountDesc() {
        StepVerifier.create(
                        adapter.findPage(query(0, 10, CapabilitySortBy.TECHNOLOGY_COUNT,
                                        CapabilitySortDirection.DESC))
                                .map(Capability::getName).collectList())
                .assertNext(names -> assertThat(names).containsExactly("Beta", "Gamma", "Alfa", "Delta"))
                .verifyComplete();
    }

    // --- LIMIT/OFFSET devuelven solo la ventana (Req 1.1, 6.2) ---

    @Test
    @DisplayName("LIMIT/OFFSET: size=2, page=1 devuelve la segunda ventana por name ASC")
    void limitOffsetReturnsWindow() {
        StepVerifier.create(
                        adapter.findPage(query(1, 2, CapabilitySortBy.NAME, CapabilitySortDirection.ASC))
                                .map(Capability::getName).collectList())
                .assertNext(names -> assertThat(names).containsExactly("Delta", "Gamma"))
                .verifyComplete();
    }

    @Test
    @DisplayName("findPage resuelve las tecnologías de cada capacidad de la página")
    void findPageResolvesTechnologies() {
        StepVerifier.create(
                        adapter.findPage(query(0, 1, CapabilitySortBy.NAME, CapabilitySortDirection.ASC))
                                .collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    // Alfa tiene la tecnología 1.
                    assertThat(list.get(0).getName()).isEqualTo("Alfa");
                    assertThat(list.get(0).getTechnologyIds()).containsExactly(1L);
                })
                .verifyComplete();
    }

    // --- countAll (Req 1.2) ---

    @Test
    @DisplayName("countAll devuelve el total de capacidades")
    void countAllReturnsTotal() {
        StepVerifier.create(adapter.countAll())
                .expectNext(4L)
                .verifyComplete();
    }
}

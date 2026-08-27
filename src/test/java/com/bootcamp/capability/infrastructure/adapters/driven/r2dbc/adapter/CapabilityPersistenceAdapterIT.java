package com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.adapter;

import com.bootcamp.capability.domain.exception.CapabilityAlreadyExistsException;
import com.bootcamp.capability.domain.model.Capability;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.entity.CapabilityTechnologyEntity;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.mapper.CapabilityEntityMapper;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityRepository;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityTechnologyRepository;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import reactor.core.publisher.Mono;
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
 * Tests de integración del {@link CapabilityPersistenceAdapter} contra una base
 * de datos MySQL real levantada con Testcontainers.
 *
 * <p>Verifica el comportamiento del adaptador driven R2DBC de extremo a extremo
 * (adaptador + mapper + repositorios + tabla puente + restricción UNIQUE),
 * usando {@link StepVerifier} para comprobar los flujos reactivos sin bloquear:
 * <ul>
 *   <li>{@code save} persiste la capacidad y sus asociaciones y retorna un
 *       {@link Capability} con id asignado (Req 1.2, 1.3).</li>
 *   <li>{@code existsByNameIgnoreCase} ignora mayúsculas y minúsculas (Req 2.1).</li>
 *   <li>Un insert con nombre duplicado (violación UNIQUE) se traduce a
 *       {@link CapabilityAlreadyExistsException} (Req 2.3).</li>
 * </ul>
 *
 * <p><b>Requirements: 1.2, 1.3, 2.1, 2.3</b>
 *
 * <p>El contenedor y el {@link ConnectionFactory} se gestionan manualmente (sin
 * contexto Spring Boot completo) para aislar la prueba en la capa de persistencia.
 * Se usa un {@code GenericContainer<>("mysql:8.0")} (NO {@code MySQLContainer})
 * porque el proyecto es puramente R2DBC y no tiene el driver JDBC de MySQL en el
 * classpath de test; {@code MySQLContainer} verificaría el arranque abriendo una
 * conexión JDBC y fallaría con {@code ClassNotFoundException}.
 *
 * <p>Requiere Docker en ejecución.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapabilityPersistenceAdapterIT {

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

        // Aplica el esquema (tablas capability y capability_technology).
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
    void cleanTables() {
        // Primero las asociaciones (FK hacia capability), luego las capacidades.
        capabilityTechnologyRepository.deleteAll().block();
        capabilityRepository.deleteAll().block();
    }

    // --- save: persiste capacidad + asociaciones y retorna id (Req 1.2, 1.3) ---

    @Test
    void save_assignsGeneratedId_andReturnsPersistedCapability() {
        Capability toSave = new Capability(
                null, "Backend Java", "Capacidad backend", List.of(1L, 2L, 3L));

        StepVerifier.create(adapter.save(toSave))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getId()).isPositive();
                    assertThat(saved.getName()).isEqualTo("Backend Java");
                    assertThat(saved.getDescription()).isEqualTo("Capacidad backend");
                    assertThat(saved.getTechnologyIds()).containsExactly(1L, 2L, 3L);
                })
                .verifyComplete();
    }

    @Test
    void save_actuallyPersistsCapabilityAndAssociations() {
        Capability toSave = new Capability(
                null, "Frontend", "Capacidad frontend", List.of(10L, 20L, 30L, 40L));

        Long generatedId = adapter.save(toSave).map(Capability::getId).block();
        assertThat(generatedId).isNotNull();

        StepVerifier.create(capabilityRepository.findById(generatedId))
                .assertNext(entity -> {
                    assertThat(entity.getName()).isEqualTo("Frontend");
                    assertThat(entity.getDescription()).isEqualTo("Capacidad frontend");
                })
                .verifyComplete();

        StepVerifier.create(
                        capabilityTechnologyRepository.findByCapabilityId(generatedId)
                                .map(CapabilityTechnologyEntity::getTechnologyId)
                                .sort()
                                .collectList())
                .assertNext(techIds -> assertThat(techIds).containsExactly(10L, 20L, 30L, 40L))
                .verifyComplete();
    }

    // --- existsByNameIgnoreCase: ignora mayúsculas y minúsculas (Req 2.1) ---

    @Test
    void existsByNameIgnoreCase_returnsTrueRegardlessOfCase() {
        adapter.save(new Capability(null, "DevOps", "Capacidad DevOps", List.of(1L, 2L, 3L))).block();

        StepVerifier.create(adapter.existsByNameIgnoreCase("DevOps")).expectNext(true).verifyComplete();
        StepVerifier.create(adapter.existsByNameIgnoreCase("devops")).expectNext(true).verifyComplete();
        StepVerifier.create(adapter.existsByNameIgnoreCase("DEVOPS")).expectNext(true).verifyComplete();
        StepVerifier.create(adapter.existsByNameIgnoreCase("DeVoPs")).expectNext(true).verifyComplete();
    }

    @Test
    void existsByNameIgnoreCase_returnsFalseWhenNameDoesNotExist() {
        adapter.save(new Capability(null, "Mobile", "Capacidad mobile", List.of(1L, 2L, 3L))).block();

        StepVerifier.create(adapter.existsByNameIgnoreCase("Data"))
                .expectNext(false)
                .verifyComplete();
    }

    // --- insert de nombre duplicado -> CapabilityAlreadyExistsException (Req 2.3) ---

    @Test
    void save_withDuplicateName_isTranslatedToCapabilityAlreadyExistsException() {
        adapter.save(new Capability(null, "Cloud", "Capacidad cloud", List.of(1L, 2L, 3L))).block();

        StepVerifier.create(adapter.save(
                        new Capability(null, "Cloud", "Otra descripción", List.of(4L, 5L, 6L))))
                .expectError(CapabilityAlreadyExistsException.class)
                .verify();
    }

    @Test
    void save_withDuplicateName_rollsBackAndDoesNotInsertSecondCapability() {
        adapter.save(new Capability(null, "Security", "Capacidad seguridad", List.of(1L, 2L, 3L))).block();

        adapter.save(new Capability(null, "Security", "Duplicada", List.of(4L, 5L, 6L)))
                .onErrorResume(CapabilityAlreadyExistsException.class, ex -> Mono.empty())
                .block();

        StepVerifier.create(capabilityRepository.count())
                .expectNext(1L)
                .verifyComplete();
    }
}

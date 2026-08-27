package com.bootcamp.capability.application.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Configuración de transaccionalidad reactiva para R2DBC.
 *
 * <p>Registra un {@link ReactiveTransactionManager} respaldado por
 * {@link R2dbcTransactionManager} sobre el {@link ConnectionFactory} que
 * autoconfigura Spring Data R2DBC, y un {@link TransactionalOperator}
 * programático. La transacción se propaga por el {@code Context} de Reactor (no
 * por {@code ThreadLocal}): commit al completar el pipeline y rollback ante error,
 * sin uso de {@code .block()}.
 *
 * <p>El {@link TransactionalOperator} lo consume el
 * {@code CapabilityPersistenceAdapter} para persistir la capacidad y sus
 * asociaciones de forma atómica (Req 1.2, 8.1, 8.2).
 */
@Configuration
public class R2dbcConfig {

    /**
     * Gestor de transacciones reactivas respaldado por R2DBC.
     *
     * @param connectionFactory factoría de conexiones autoconfigurada por Spring.
     * @return el {@link ReactiveTransactionManager} sobre el {@code connectionFactory}.
     */
    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    /**
     * Operador transaccional programático para envolver pipelines reactivos.
     *
     * @param transactionManager gestor de transacciones reactivas.
     * @return el {@link TransactionalOperator} creado a partir del gestor.
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}

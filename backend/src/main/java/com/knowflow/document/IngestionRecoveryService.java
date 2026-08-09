package com.knowflow.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(IngestionRecoveryService.class);

    private final IngestionRecoveryMapper mapper;
    private final Environment environment;

    public IngestionRecoveryService(
            IngestionRecoveryMapper mapper,
            Environment environment
    ) {
        this.mapper = mapper;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        int timeoutMinutes = environment.getProperty(
                "knowflow.ingestion.stale-timeout-minutes",
                Integer.class,
                60
        );

        timeoutMinutes = Math.max(15, timeoutMinutes);

        int versions = mapper.failStaleVersions(timeoutMinutes);
        int documents = mapper.failStaleDocuments(timeoutMinutes);
        int tasks = mapper.failStaleTasks(timeoutMinutes);

        if (tasks > 0 || documents > 0 || versions > 0) {
            log.warn(
                    "Recovered stale ingestion state: tasks={}, documents={}, versions={}, timeout={}min",
                    tasks, documents, versions, timeoutMinutes
            );
        }
    }
}

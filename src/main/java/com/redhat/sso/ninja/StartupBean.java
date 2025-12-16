package com.redhat.sso.ninja;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class StartupBean {
    private static final Logger log = Logger.getLogger(StartupBean.class);

    @Inject
    Config config;

    @Inject
    Database2 database;

    void onStart(@Observes StartupEvent ev) {
        log.info("Initializing Ninja Board application...");
        config.load();
        database.load();
        log.info("Ninja Board application started successfully");
    }
}


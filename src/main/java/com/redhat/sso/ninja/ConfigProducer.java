package com.redhat.sso.ninja;

import java.io.File;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

public class ConfigProducer {

    @ConfigProperty(name = "ninja.config.file", defaultValue = "target/ninja-persistence/config.json")
    String configFile;

    @ConfigProperty(name = "ninja.database.file", defaultValue = "target/ninja-persistence/database2.json")
    String databaseFile;

    @Produces
    @ApplicationScoped
    @Named("objectMapper")
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Produces
    @Dependent
    @Named("configFile")
    File configFile() {
        return new File(configFile);
    }

    @Produces
    @Dependent
    @Named("databaseFile")
    File databaseFile() {
        return new File(databaseFile);
    }
}


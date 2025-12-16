package com.redhat.sso.ninja;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.sso.ninja.utils.IOUtils2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named("config")
public class Config {
    private static final Logger log = Logger.getLogger(Config.class);
    
    @Inject
    @Named("configFile")
    File storageFile;
    
    @Inject
    @Named("objectMapper")
    ObjectMapper objectMapper;
    
    private List<Map<String, Object>> scripts = null;
    private Map<String, String> options = null;
    private Map<String, Object> values = null;
    private List<Map<String, String>> notifications = null;

    public Config() {
    }

    public Config(String json) {
        try {
            Config x = objectMapper.readValue(json, Config.class);
            this.options = x.options;
            this.scripts = x.scripts;
            this.values = x.values;
            this.notifications = x.notifications;
        } catch (IOException e) {
            log.error("Failed to parse config JSON", e);
        }
    }

    public Map<String, String> getOptions() {
        if (options == null) options = new HashMap<>();
        return options;
    }

    public List<Map<String, Object>> getScripts() {
        if (scripts == null) scripts = new ArrayList<>();
        return scripts;
    }

    public Map<String, Object> getValues() {
        if (values == null) values = new HashMap<>();
        return values;
    }

    public List<Map<String, String>> getNotifications() {
        if (notifications == null) notifications = new ArrayList<>();
        return notifications;
    }

    public void reload() {
        // Reload from file
        load();
    }

    public void save() {
        try {
            if (!storageFile.getParentFile().exists()) {
                log.info("Config storage folder didn't exist - creating new folder to store config");
                storageFile.getParentFile().mkdirs();
            }
            IOUtils2.writeAndClose(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(this), new FileOutputStream(storageFile));
            log.info("Config saved (size=" + storageFile.length() + ")");
        } catch (IOException e) {
            log.error("Failed to save config", e);
        }
    }

    public void load() {
        try {
            if (!storageFile.exists()) {
                log.info("Config file doesn't exist, creating default one here: " + storageFile.getAbsolutePath());
                if (!storageFile.getParentFile().exists()) storageFile.getParentFile().mkdirs();
                // Create default config
                initializeDefaults();
                save();
            }
            log.info("Config loading (location=" + storageFile.getAbsolutePath() + ", size=" + storageFile.length() + ")");
            String toLoad = IOUtils2.toStringAndClose(new FileInputStream(storageFile));
            Config loaded = objectMapper.readValue(toLoad, Config.class);
            this.options = loaded.options;
            this.scripts = loaded.scripts;
            this.values = loaded.values;
            this.notifications = loaded.notifications;
        } catch (Exception e) {
            log.error("Failed to load config, using defaults", e);
            initializeDefaults();
        }
    }

    private void initializeDefaults() {
        options = new HashMap<>();
        options.put("login.enabled", "false");
        options.put("heartbeat.intervalInSeconds", "86400");
        options.put("heartbeat.startTime", "21:00");
        options.put("events.max", "1000000");
        options.put("thresholds", "0:ZERO,5:BLUE,20:GREY,40:RED,75:BLACK");
        
        scripts = new ArrayList<>();
        values = new HashMap<>();
        notifications = new ArrayList<>();
    }

    public void setOptions(Map<String, String> value) {
        this.options = value;
    }

    @JsonIgnore
    public String getNextTaskNum() {
        if (!getValues().containsKey("lastTaskNum")) {
            getValues().put("lastTaskNum", 0);
        }

        int result = 1 + (Integer) getValues().get("lastTaskNum");
        getValues().put("lastTaskNum", result);
        save();

        return String.valueOf(result);
    }
}


package com.redhat.sso.ninja;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.redhat.sso.ninja.utils.IOUtils2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named("database")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Database2 {
    private static final Logger log = Logger.getLogger(Database2.class);
    
    @Inject
    @Named("databaseFile")
    File storageFile;
    
    @Inject
    Config config;
    
    @Inject
    @Named("objectMapper")
    ObjectMapper objectMapper;
    
    public static Integer maxEventEntries = 0;
    public static boolean systemUpdating = false;

    // User -> Pool (sub pool separated with a dot) + Score
    private Map<String, Map<String, Integer>> scorecards;
    private Map<String, Map<String, String>> users;
    private List<Map<String, String>> events;
    private List<Map<String, String>> tasks;

    // PoolId -> UserId + Score
    private String created;
    private String version;
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    static SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

    public Database2() {
        created = sdf.format(new Date());
    }

    public String getCreated() {
        return created;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    // markdown format
    public static String buildLinkMarkdown(Map<String, String> params) {
        if (!params.containsKey("linkId")) return "";
        if (params.get("id").startsWith("TR")) {
            return "[Trello: " + params.get("linkId") + "/" + params.get("id") + "](https://trello.com/c/" + params.get("linkId") + ")";
        } else if (params.get("id").startsWith("GH")) {
            if (params.get("pool").toLowerCase().contains("pull")) {
                return "<https://github.com/" + params.get("org") + "/" + params.get("board") + "/pull/" + params.get("linkId") + ">";
            } else { // assume "issues"
                return "<https://github.com/" + params.get("org") + "/" + params.get("board") + "/issues/" + params.get("linkId") + ">";
            }
        } else if (params.get("id").startsWith("SS")) { // smartsheets
            return "[Smartsheets: " + params.get("linkId") + "](https://app.smartsheet.com/sheets/" + params.get("board") + (null != params.get("rowId") ? "?rowId=" + params.get("rowId") : "") + ")";
        }
        return "";
    }

    public synchronized Integer getMaxEventEntries() {
        if (maxEventEntries <= 0) {
            String max = config.getOptions().get("events.max");
            if (null != max && max.matches("\\d+")) {
                maxEventEntries = Integer.parseInt(max);
            }
        }
        return maxEventEntries;
    }

    public Database2 increment(String poolId, String userId, Integer increment, Map<String, String> params) {
        if (null == poolId || null == userId) {
            log.error("Unable to add due to null key [poolId=" + poolId + ", userId=" + userId + "]");
            return this;
        }
        if (users.containsKey(userId)) { // means the user is registered
            if (null == scorecards.get(userId)) scorecards.put(userId, new HashMap<>());
            if (null == scorecards.get(userId).get(poolId)) scorecards.get(userId).put(poolId, 0);
            log.info("Incrementing points: user=" + userId + ", poolId=" + poolId + ", increment/points=" + increment + " + params=" + params);
            scorecards.get(userId).put(poolId, scorecards.get(userId).get(poolId) + increment);

            if (params != null && params.size() > 1) { // because "id" is always added
                addEvent2("Points Increment", userId, increment, buildLinkMarkdown(params), poolId);
            } else {
                // no params & therefore no link
                addEvent2("Points Increment", userId, increment, "", poolId);
            }

        } else {
            log.debug("Unregistered user detected [" + userId + "]");
        }

        return this;
    }

    public Map<String, Map<String, String>> getUsers() {
        if (null == users) users = new HashMap<>();
        return users;
    }

    public Map<String, Map<String, Integer>> getScoreCards() {
        if (null == scorecards) scorecards = new HashMap<>();
        return scorecards;
    }

    public List<Map<String, String>> getEvents() {
        if (null == events) events = new ArrayList<>();
        return events;
    }

    public enum EVENT_FIELDS {
        TIMESTAMP("timestamp"),
        TYPE("type"),
        USER("user"),
        TEXT("text"),
        POINTS("points"),
        SOURCE("source"),
        POOL("pool");
        public String v;

        EVENT_FIELDS(String v) {
            this.v = v;
        }
    }

    public List<Map<String, String>> getTasks() {
        if (null == tasks) tasks = new ArrayList<>();
        return tasks;
    }

    public enum TASK_FIELDS {
        TIMESTAMP("timestamp"),
        USER("user"),
        LIST("list"),
        ID("id"),
        UID("uid"),
        TITLE("title"),
        OWNERS("owners"),
        LABELS("labels");
        public String v;

        TASK_FIELDS(String v) {
            this.v = v;
        }
    }

    public void addEvent2(String type, String user, Integer points, String source, String pool) {
        Map<String, String> event = new HashMap<>();
        event.put(EVENT_FIELDS.TIMESTAMP.v, sdf2.format(new Date()));
        event.put(EVENT_FIELDS.TYPE.v, type);
        event.put(EVENT_FIELDS.USER.v, user);
        event.put(EVENT_FIELDS.POINTS.v, String.valueOf(points));
        event.put(EVENT_FIELDS.SOURCE.v, source);
        event.put(EVENT_FIELDS.POOL.v, pool);
        getEvents().add(event);

        // limit the events to a configurable number of entries
        while (getEvents().size() > getMaxEventEntries()) {
            getEvents().remove(0);
        }
    }

    public void addEvent(String type, String user, String text) {
        Map<String, String> event = new HashMap<>();
        event.put(EVENT_FIELDS.TIMESTAMP.v, sdf2.format(new Date()));
        event.put(EVENT_FIELDS.TYPE.v, type);
        event.put(EVENT_FIELDS.USER.v, user);
        if (text != null && !"".equals(text)) event.put(EVENT_FIELDS.TEXT.v, text);
        getEvents().add(event);

        // limit the events to a configurable number of entries
        while (getEvents().size() > getMaxEventEntries()) {
            getEvents().remove(0);
        }
    }

    // user is the target user: ie. fbloggs
    public void addTask(String taskText, String user) {
        Map<String, String> task = new HashMap<>();
        task.put(TASK_FIELDS.TIMESTAMP.v, sdf2.format(new Date()));
        task.put(TASK_FIELDS.UID.v, UUID.randomUUID().toString());
        task.put(TASK_FIELDS.ID.v, config.getNextTaskNum());
        task.put(TASK_FIELDS.TITLE.v, taskText);
        task.put(TASK_FIELDS.USER.v, user);
        task.put(TASK_FIELDS.LIST.v, "todo");
        getTasks().add(task);
    }

    private Set<String> pointsDuplicateChecker = new HashSet<>();

    public Set<String> getPointsDuplicateChecker() {
        if (null == pointsDuplicateChecker) pointsDuplicateChecker = new HashSet<>();
        return pointsDuplicateChecker;
    }

    @JsonIgnore
    private Map<String, Map<String, Integer>> leaderboard = new HashMap<>();

    public Map<String, Map<String, Integer>> getLeaderboard() {
        for (Entry<String, Map<String, String>> e : users.entrySet()) {
            leaderboard.put(e.getKey(), new HashMap<>());
        }
        for (Entry<String, Map<String, Integer>> e : scorecards.entrySet()) {
            leaderboard.get(e.getKey()).putAll(e.getValue());
        }
        return leaderboard;
    }

    private Map<String, Map<String, String>> scorecardHistory = new HashMap<>();

    public Map<String, Map<String, String>> getScorecardHistory() {
        if (null == scorecardHistory) scorecardHistory = new HashMap<>();
        return scorecardHistory;
    }

    public synchronized void save() {
        save(storageFile);
    }

    public synchronized void save(File storeHere) {
        try {
            long s = System.currentTimeMillis();
            if (!storeHere.getParentFile().exists())
                storeHere.getParentFile().mkdirs();
            IOUtils2.writeAndClose(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(this), new FileOutputStream(storeHere));
            log.info("Database saved (" + (System.currentTimeMillis() - s) + "ms, size=" + storeHere.length() + ")");
        } catch (FileNotFoundException e) {
            log.error("Failed to save database", e);
        } catch (IOException e) {
            log.error("Failed to save database", e);
        }
    }

    public void load() {
        try {
            if (!storageFile.exists()) {
                log.warn("No database file found, creating new/blank/default one...");
                save();
                return;
            }
            log.info("Database loading (size=" + storageFile.length() + ")");
            String content = IOUtils2.toStringAndClose(new FileInputStream(storageFile));
            Database2 loaded = objectMapper.readValue(content, Database2.class);
            this.scorecards = loaded.scorecards;
            this.users = loaded.users;
            this.events = loaded.events;
            this.tasks = loaded.tasks;
            this.created = loaded.created;
            this.version = loaded.version;
            this.scorecardHistory = loaded.scorecardHistory;
        } catch (FileNotFoundException e) {
            log.error("Database file not found", e);
        } catch (IOException e) {
            log.error("Failed to load database", e);
        }
    }
}


package com.redhat.sso.ninja;

import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestQuery;

import com.redhat.sso.ninja.chart.ChartJson;
import com.redhat.sso.ninja.chart.DataSet;
import com.redhat.sso.ninja.utils.LevelsUtil;
import com.redhat.sso.ninja.utils.MapBuilder;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
public class ManagementController {
    private static final Logger log = Logger.getLogger(ManagementController.class);

    @Inject
    Database2 database;

    @Inject
    Config config;

    @Inject
    LevelsUtil levelsUtil;

    public static boolean isLoginEnabled(Config config) {
        return "true".equalsIgnoreCase(config.getOptions().get("login.enabled"));
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("application", "ninja-board");
        status.put("version", "1.0.0");
        status.put("timestamp", new Date().toString());
        return Response.status(200).entity(status).build();
    }

    @GET
    @Path("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    public Response ping() {
        return Response.status(200).entity("pong").build();
    }

    @GET
    @Path("/tokens/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response tokensStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Check for API tokens (masked for security)
        String[] tokenVars = {
            "TRELLO_API_TOKEN", "TRELLO_API_KEY", "GITHUB_API_TOKEN", 
            "GITLAB_API_TOKEN", "SMARTSHEETS_API_TOKEN", "GD_CREDENTIALS"
        };
        
        Map<String, Object> tokens = new HashMap<>();
        for (String var : tokenVars) {
            String value = System.getenv(var);
            if (value != null && !value.isEmpty()) {
                // Show first 4 and last 4 characters, mask the middle
                String masked = maskToken(value);
                tokens.put(var, Map.of(
                    "configured", true,
                    "length", value.length(),
                    "preview", masked
                ));
            } else {
                tokens.put(var, Map.of("configured", false));
            }
        }
        
        status.put("tokens", tokens);
        status.put("timestamp", new Date().toString());
        
        return Response.status(200).entity(status).build();
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        // Show first 4 and last 4 characters
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    @POST
    @Path("/yearEnd/{priorYear}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response yearEnd(@PathParam("priorYear") String priorYear) throws IOException {
        log.info("Year Ending for - " + priorYear + ". Note: This will lose some data (such as point buckets) as it archives the current years information");

        if (database.getScorecardHistory().containsKey(priorYear))
            return Response.status(400).entity("Can't do that - the key '" + priorYear + "' already exists!").build();

        // clear outstanding tasks
        database.getTasks().clear();

        // cleanup scorecards and backup into a year dated bucket
        Map<String, String> history = new LinkedHashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        for (Entry<String, Map<String, Integer>> e : database.getScoreCards().entrySet()) {
            if (null != e.getValue() && e.getValue().size() > 0) {
                String belt = database.getUsers().get(e.getKey()).get("level");
                String total = String.valueOf(ChartsController.total(e.getValue()));
                history.put(e.getKey(), belt + "|" + total);
                totals.put(e.getKey(), Integer.valueOf(total));
            }
        }

        // reorder by total
        List<Entry<String, Integer>> list = new LinkedList<>(totals.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });
        HashMap<String, Integer> sortedTotals = new LinkedHashMap<>();
        for (Entry<String, Integer> e : list)
            sortedTotals.put(e.getKey(), e.getValue());

        Map<String, String> sortedHistory = new LinkedHashMap<>();
        for (Entry<String, Integer> e : sortedTotals.entrySet())
            sortedHistory.put(e.getKey(), history.get(e.getKey()));

        // write the history for the 'priorYear'
        database.getScorecardHistory().put(priorYear, sortedHistory);

        // clear current points
        database.getScoreCards().clear();

        // clear current belt status
        for (Entry<String, Map<String, String>> e : database.getUsers().entrySet()) {
            e.getValue().put("level", "ZERO");
            e.getValue().remove("levelChanged");
        }

        database.save();

        return Response.status(200).entity("OK, it's done!").build();
    }

    @GET
    @Path("/checkTrelloIDs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkTrelloIds(@RestQuery String max) {
        int maxCount = Integer.valueOf(max != null ? max : "-1");

        Map<String, String> unknownUsers = new HashMap<>();
        int count = 0;
        for (Entry<String, Map<String, String>> e : database.getUsers().entrySet()) {
            count += 1;
            String trelloId = e.getValue().get("trelloId");
            if (null == trelloId || "null".equals(trelloId.trim().toLowerCase()) || "".equals(trelloId.trim().toLowerCase())) {
                unknownUsers.put(e.getKey(), "Not Registered?");
                continue;
            }
            // TODO: Implement HTTP check for Trello IDs
            if (maxCount > 0 && count >= maxCount) break;
        }

        return Response.status(200).entity(unknownUsers).build();
    }

    @GET
    @Path("/config/get")
    @Produces(MediaType.APPLICATION_JSON)
    public Response configGet() {
        return Response.status(200).entity(config).build();
    }

    @POST
    @Path("/config/save")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response configSave(Config newConfig) {
        log.info("Saving config");
        newConfig.save();
        config.reload();
        return Response.status(200).entity(config).build();
    }

    @GET
    @Path("/scripts/runNow")
    @Produces(MediaType.TEXT_PLAIN)
    public Response runScriptsNow() {
        // TODO: Implement Heartbeat2.runOnceAsync()
        log.debug("Scripts run started - check logs for results");
        return Response.status(200).entity("RUNNING").build();
    }

    @GET
    @Path("/scripts/publishGraphs")
    @Produces(MediaType.TEXT_PLAIN)
    public Response pushGraphDataOnly() {
        // TODO: Implement graph publishing
        return Response.status(200).entity("RUNNING").build();
    }

    @GET
    @Path("/database/get")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDatabase() {
        return Response.status(200).entity(database).build();
    }

    @POST
    @Path("/database/save")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response databaseSave(Database2 db) {
        log.info("Saving database");
        db.save();
        database.load();
        log.info("New Database Saved");
        return Response.status(200).entity(database).build();
    }

    @PUT
    @Path("/users/{user}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUserProperty(@PathParam("user") String user, Map<String, String> values) {
        Map<String, String> userInfo = database.getUsers().get(user);
        if (null == userInfo)
            return Response.status(404).entity("User info for '" + user + "' not found").build();

        for (Entry<String, String> e : values.entrySet()) {
            String existingValue = userInfo.get(e.getKey());
            if ("displayName".equals(e.getKey()) || e.getKey().endsWith("Id")) {
                if (null == existingValue) {
                    userInfo.put(e.getKey(), e.getValue());
                    database.addEvent("User Update", user, e.getKey() + " added as " + e.getValue());
                } else {
                    if (!existingValue.equals(e.getValue())) {
                        userInfo.put(e.getKey(), e.getValue());
                        database.addEvent("User Update", user, e.getKey() + " changed from " + existingValue + " to " + e.getValue());
                    }
                }
            } else {
                if (e.getKey().contains("level")) {
                    log.warn("Suspicious Activity: User [" + user + "] attempting to update their level from [" + existingValue + "] to [" + e.getValue() + "]");
                }
            }
        }

        database.save();
        return Response.status(200).build();
    }

    @GET
    @Path("/scorecard/{user}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getScorecard(@PathParam("user") String user) {
        log.debug("Request made for user [" + user + "]");

        Map<String, Integer> scorecard = database.getScoreCards().get(user);
        Map<String, String> userInfo = database.getUsers().get(user);

        log.debug(user + " user data for scorecards " + (scorecard != null ? "found" : "NOT FOUND!"));
        log.debug(user + " user data for userInfo " + (userInfo != null ? "found" : "NOT FOUND!"));

        String payload = "{\"status\":\"ERROR\",\"message\":\"Unable to find user: " + user + "\", \"displayName\":\"You (" + user + ") are not registered\"}";

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user);
        if (null != scorecard)
            data.putAll(scorecard);
        if (null != userInfo)
            data.putAll(userInfo);

        return Response.status(userInfo == null ? 500 : 200).entity(data).build();
    }

    @GET
    @Path("/scorecard/breakdown/{user}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserBreakdown(@PathParam("user") String user) {
        Map<String, Integer> scorecard = database.getScoreCards().get(user);

        ChartJson chart = new ChartJson();
        chart.getDatasets().add(new DataSet());
        chart.getDatasets().get(0).setBorderWidth(1);
        if (null != scorecard) {
            for (Entry<String, Integer> s : scorecard.entrySet()) {
                chart.getLabels().add(s.getKey());
                chart.getDatasets().get(0).getData().add(s.getValue());
            }
        } else {
            chart.getLabels().add("No Points");
            chart.getDatasets().get(0).getData().add(0);
        }
        return Response.status(200).entity(chart).build();
    }

    @GET
    @Path("/scorecard/summary/{user}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getScorecardSummary(@PathParam("user") String user) {
        log.debug("Request made for user [" + user + "]");

        Map<String, Integer> scorecard = database.getScoreCards().get(user);
        Map<String, String> userInfo = database.getUsers().get(user);

        log.debug(user + " user data for scorecards " + (scorecard != null ? "found" : "NOT FOUND!"));
        log.debug(user + " user data for userInfo " + (userInfo != null ? "found" : "NOT FOUND!"));

        String payload = "{\"status\":\"ERROR\",\"message\":\"Unable to find user: " + user + "\", \"displayName\":\"" + user + " not registered\"}";

        if (userInfo != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user);

            Map<String, Integer> consolidatedTotals = new HashMap<>();
            Integer total = 0;
            if (scorecard != null) {
                for (Entry<String, Integer> e : scorecard.entrySet()) {
                    String consolidatedKey = e.getKey().substring(0, e.getKey().contains(".") ? e.getKey().indexOf(".") : e.getKey().length());
                    if (!consolidatedTotals.containsKey(consolidatedKey)) consolidatedTotals.put(consolidatedKey, 0);
                    consolidatedTotals.put(consolidatedKey, consolidatedTotals.get(consolidatedKey) + e.getValue());
                    total += e.getValue();
                }
            }
            data.put("total", total);
            data.putAll(consolidatedTotals);
            data.putAll(userInfo);
            return Response.status(200).entity(data).build();
        }

        return Response.status(200).entity(payload).build();
    }

    @POST
    @Path("/scorecard/{user}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveScorecard(@PathParam("user") String user, Map<String, Object> map) {
        log.debug("Saving scorecard for user: " + user);

        String username = (String) map.get("userId");

        Map<String, String> userInfo = database.getUsers().get(username);
        Map<String, Integer> scorecard = database.getScoreCards().get(username);

        for (String k : map.keySet()) {
            if (!k.equals("userId")) {
                if (userInfo.containsKey(k)) {
                    if (!userInfo.get(k).equals(map.get(k))) {
                        log.debug("Setting 'userInfo." + k + "' to " + (String) map.get(k));
                        database.addEvent("User Update", user, k + " changed from " + userInfo.get(k) + " to " + (String) map.get(k));
                        userInfo.put(k, (String) map.get(k));
                    }
                } else if (scorecard.containsKey(k)) {
                    if (!scorecard.get(k).equals(map.get(k))) {
                        log.debug("Setting 'scorecard." + k + "' to " + (String) map.get(k));
                        database.addEvent("User Update", user, k + " changed from " + scorecard.get(k) + " to " + (String) map.get(k));
                        scorecard.put(k, Integer.parseInt((String) map.get(k)));
                    }
                } else {
                    if (!userInfo.get(k).equals(map.get(k))) {
                        log.debug("Setting 'userInfo." + k + "' to " + (String) map.get(k));
                        database.addEvent("User Update", user, k + " set as " + (String) map.get(k));
                        userInfo.put(k, (String) map.get(k));
                    }
                }
            }
        }

        database.save();
        return Response.status(200).entity("OK").build();
    }

    @GET
    @Path("/scorecards")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getScorecards() {
        List<Map<String, Object>> data = new ArrayList<>();

        Set<String> fields = new HashSet<>();

        for (Entry<String, Map<String, String>> u : database.getUsers().entrySet()) {
            String username = u.getKey();
            Map<String, String> userInfo = u.getValue();
            Map<String, Integer> scorecard = database.getScoreCards().get(u.getKey());

            Map<String, Object> row = new HashMap<>();
            row.put("id", username);
            row.put("name", userInfo.containsKey("displayName") ? userInfo.get("displayName") : username);
            int total = 0;
            if (null != scorecard) {
                for (Entry<String, Integer> s : scorecard.entrySet()) {
                    row.put(s.getKey().replaceAll("\\.", " "), s.getValue());
                    total += s.getValue();
                    fields.add(s.getKey().replaceAll("\\.", " "));
                }
                row.put("total", total);
                row.put("level", userInfo.get("level"));
            } else {
                row.put("total", 0);
                row.put("level", "ZERO");
            }

            // points to next level
            if (null == userInfo.get("level") || null == levelsUtil.getNextLevel(userInfo.get("level"))) {
                log.error("Invalid level for user " + row.get("name") + " : " + userInfo);
                row.put("pointsToNextLevel", 0);
            } else {
                Integer pointsToNextLevel = levelsUtil.getNextLevel(userInfo.get("level")).getLeft() - total;
                if (pointsToNextLevel < 0) pointsToNextLevel = 0;
                row.put("pointsToNextLevel", pointsToNextLevel);
            }

            data.add(row);
        }

        // fill in the missing points fields with zero's
        for (Map<String, Object> e : data) {
            for (String field : fields) {
                if (!e.containsKey(field)) {
                    e.put(field, 0);
                }
            }
        }

        Map<String, Object> wrapper = new HashMap<>();
        List<Map<String, String>> columns = new ArrayList<>();
        columns.add(new MapBuilder<String, String>().put("title", "Name").put("data", "name").build());
        columns.add(new MapBuilder<String, String>().put("title", "Total").put("data", "total").build());
        columns.add(new MapBuilder<String, String>().put("title", "Ninja Belt").put("data", "level").build());
        columns.add(new MapBuilder<String, String>().put("title", "Points to next level").put("data", "pointsToNextLevel").build());

        for (String field : fields)
            columns.add(new MapBuilder<String, String>().put("title", field).put("data", field).build());

        wrapper.put("columns", columns);
        wrapper.put("data", data);

        return Response.status(200).entity(wrapper).build();
    }
}


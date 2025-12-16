package com.redhat.sso.ninja;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;

import org.jboss.logging.Logger;

import com.redhat.sso.ninja.chart.ChartJson;
import com.redhat.sso.ninja.chart.DataSet;
import com.redhat.sso.ninja.utils.LevelsUtil;
import com.redhat.sso.ninja.utils.MapBuilder;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
public class ChartsController {
    private static final Logger log = Logger.getLogger(ChartsController.class);

    @Inject
    Database2 database;

    @Inject
    Config config;

    @Inject
    LevelsUtil levelsUtil;

    // Mojo UI endpoint
    @GET
    @Path("/ninjas")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNinjas() {
        return Response.status(200)
                .header("Access-Control-Allow-Origin", "*")
                .header("Cache-Control", "no-store, must-revalidate, no-cache, max-age=0")
                .header("Pragma", "no-cache")
                .entity(getParticipants(null))
                .build();
    }

    // Mojo UI: "race to black belt"
    @GET
    @Path("/leaderboard/{max}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLeaderboard2(@PathParam("max") Integer max) {
        return Response.status(200)
                .header("Access-Control-Allow-Origin", "*")
                .header("Cache-Control", "no-store, must-revalidate, no-cache, max-age=0")
                .header("Pragma", "no-cache")
                .entity(getParticipants(max))
                .build();
    }

    public static Integer total(Map<String, Integer> points) {
        int t = 0;
        for (Entry<String, Integer> e : points.entrySet()) {
            t += e.getValue();
        }
        return t;
    }

    public ChartJson getParticipants(Integer max) {
        Map<String, Map<String, Integer>> leaderboard = database.getLeaderboard();
        Map<String, Integer> totals = new HashMap<>();
        for (Entry<String, Map<String, Integer>> e : leaderboard.entrySet()) {
            totals.put(e.getKey(), total(e.getValue()));
        }

        // identify past years for historical badges
        Set<String> historyYears = database.getScorecardHistory().keySet();
        List<String> historyYearsList = new ArrayList<>(historyYears);
        Collections.sort(historyYearsList);
        historyYears = new LinkedHashSet<>(historyYearsList);

        // reorder
        List<Entry<String, Integer>> list = new LinkedList<>(totals.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });
        HashMap<String, Integer> sortedTotals = new LinkedHashMap<>();
        for (Entry<String, Integer> e : list)
            sortedTotals.put(e.getKey(), e.getValue());

        // Build Chart data structure
        ChartJson c = new ChartJson();
        c.setDatasets(new ArrayList<>());
        int count = 0;
        for (Entry<String, Integer> e : sortedTotals.entrySet()) {
            Map<String, String> userInfo = database.getUsers().get(e.getKey());

            if (null == max && userInfo != null && userInfo.get("level").equalsIgnoreCase("zero"))
                continue;

            c.getLabels().add(null != userInfo && userInfo.containsKey("displayName") ? userInfo.get("displayName") : e.getKey());

            String geo = userInfo != null && userInfo.containsKey("geo") ? userInfo.get("geo") : "Unknown";
            String level = userInfo != null ? userInfo.get("level") : "none";
            if (level == null) level = "none";
            c.getCustom1().add(e.getKey() + "|" + level.toLowerCase() + "|" + geo);

            List<String> pastYearBadges = new ArrayList<>();
            for (String year : historyYears) {
                String pastYearHistory = database.getScorecardHistory().get(year).get(e.getKey());
                if (null != pastYearHistory) {
                    String belt = pastYearHistory.split("\\|")[0];
                    String total = pastYearHistory.split("\\|")[1];
                    pastYearBadges.add(String.format("%s|%s|%s", year, belt, total));
                }
            }
            StringJoiner joiner = new StringJoiner(",");
            for (String badge : pastYearBadges) {
                joiner.add(badge);
            }
            c.getCustom2().add(joiner.toString());

            if (c.getDatasets().size() <= 0) c.getDatasets().add(new DataSet());
            c.getDatasets().get(0).getData().add(e.getValue());
            c.getDatasets().get(0).setBorderWidth(1);

            // Belt colors
            Map<String, ColorPair> colors = new MapBuilder<String, ColorPair>()
                    .put("BLUE", new ColorPair("rgba(0,0,163,0.7)", "rgba(0,0,163,0.8)"))
                    .put("GREY", new ColorPair("rgba(130,130,130,0.7)", "rgba(130,130,130,0.8)"))
                    .put("RED", new ColorPair("rgba(163,0,0,0.7)", "rgba(163,0,0,0.8)"))
                    .put("BLACK", new ColorPair("rgba(20,20,20,0.7)", "rgba(20,20,20,0.8)"))
                    .put("GREEN", new ColorPair("rgba(65, 168, 95,0.7)", "rgba(65, 168, 95,0.8)"))
                    .put("GOLD", new ColorPair("rgba(250, 197, 28,0.7)", "rgba(250, 197, 28,0.8)"))
                    .put("ZERO", new ColorPair("rgba(255,255,255,0.7)", "rgba(255,255,255,0.8)"))
                    .build();

            String levelUpper = level.toUpperCase();
            if (!colors.containsKey(levelUpper)) {
                log.warn("Color [" + levelUpper + "] does not exist in our color mapping for charts - user = " + userInfo);
                levelUpper = "ZERO";
            }
            ColorPair colorPair = colors.get(levelUpper);
            c.getDatasets().get(0).getBackgroundColor().add(colorPair.first);
            c.getDatasets().get(0).getBorderColor().add(colorPair.second);

            count = count + 1;
            if (null != max && count >= max) break;
        }

        return c;
    }

    // UI call (user dashboard)
    @GET
    @Path("/scorecard/nextlevel/{user}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserNextLevel(@PathParam("user") String user) {
        boolean userExists = database.getScoreCards().containsKey(user);

        ChartJson chart = new ChartJson();
        chart.getLabels().add("Earned");
        chart.getLabels().add("To Next Level");
        chart.getDatasets().add(new DataSet());
        chart.getDatasets().get(0).setBorderWidth(1);

        if (userExists) {
            int currentTotal = getTotalPoints(user);
            int outOf = getPointsToNextLevel(user);
            chart.getDatasets().get(0).getData().add(currentTotal);
            chart.getDatasets().get(0).getData().add(outOf);
        } else {
            chart.getDatasets().get(0).getData().add(0);
            String thresholds = config.getOptions().getOrDefault("thresholds", "0:ZERO,5:BLUE,20:GREY,40:RED,75:BLACK");
            chart.getDatasets().get(0).getData().add(Integer.parseInt(thresholds.split(":")[0]));
        }

        return Response.status(200)
                .header("Access-Control-Allow-Origin", "*")
                .header("Cache-Control", "no-store, must-revalidate, no-cache, max-age=0")
                .header("Pragma", "no-cache")
                .entity(chart).build();
    }

    private int getTotalPoints(String username) {
        Map<String, Integer> scorecard = database.getScoreCards().get(username);
        int total = 0;
        if (scorecard != null) {
            for (Entry<String, Integer> s : scorecard.entrySet()) {
                total += s.getValue();
            }
        }
        return total;
    }

    private int getPointsToNextLevel(String username) {
        int total = getTotalPoints(username);
        Map<String, String> userInfo = database.getUsers().get(username);
        if (userInfo != null && userInfo.get("level") != null) {
            var nextLevel = levelsUtil.getNextLevel(userInfo.get("level"));
            if (nextLevel != null) {
                Integer pointsToNextLevel = nextLevel.getLeft() - total;
                if (pointsToNextLevel < 0) pointsToNextLevel = 0;
                return pointsToNextLevel;
            }
        }
        return 0;
    }

    private static class ColorPair {
        String first;
        String second;

        ColorPair(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }
}


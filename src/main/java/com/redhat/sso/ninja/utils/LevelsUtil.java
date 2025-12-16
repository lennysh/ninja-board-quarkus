package com.redhat.sso.ninja.utils;

import java.util.LinkedList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.redhat.sso.ninja.Config;

@ApplicationScoped
public class LevelsUtil {
    
    @Inject
    Config config;
    
    private List<Tuple<Integer, String>> levels = new LinkedList<>();
    private Tuple<Integer, String> base;
    private Tuple<Integer, String> top;

    public LevelsUtil() {
        // Default constructor for CDI
    }

    public void initialize(String levelConfig) {
        if (levelConfig == null || levelConfig.isEmpty()) {
            levelConfig = "0:ZERO,5:BLUE,20:GREY,40:RED,75:BLACK";
        }
        levels.clear();
        for (String levelValueAndText : levelConfig.split(",")) {
            String[] level = levelValueAndText.split(":");
            if (level.length == 2) {
                levels.add(new Tuple<>(Integer.valueOf(level[0].trim()), level[1].trim()));
            }
        }
        if (!levels.isEmpty()) {
            base = levels.get(0);
            top = levels.get(levels.size() - 1);
        }
    }

    public Tuple<Integer, String> getBaseLevel() {
        ensureInitialized();
        return base;
    }
    
    private void ensureInitialized() {
        if (levels.isEmpty() && config != null) {
            initialize(config.getOptions().getOrDefault("thresholds", "0:ZERO,5:BLUE,20:GREY,40:RED,75:BLACK"));
        }
    }

    public Tuple<Integer, String> getLevel(String levelName) {
        ensureInitialized();
        for (Tuple<Integer, String> l : levels) {
            if (l.getRight().equals(levelName)) return l;
        }
        return null;
    }

    public Tuple<Integer, String> getNextLevel(String currentLevelName) {
        ensureInitialized();
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).getRight().equals(currentLevelName))
                return levels.get((i + 1) == levels.size() ? i : i + 1);
        }
        return null;
    }

    public Tuple<Integer, String> getLastLevel(String currentLevelName) {
        ensureInitialized();
        if (!levels.isEmpty() && levels.get(0).getRight().equals(currentLevelName)) return levels.get(0);
        for (int i = levels.size() - 1; i >= 0; i--) {
            if (levels.get(i).getRight().equals(currentLevelName))
                return levels.get((i - 1) < 0 ? i : i - 1);
        }
        return null;
    }

    public Tuple<Integer, String> getLevelGivenPoints(Integer points) {
        ensureInitialized();
        Tuple<Integer, String> result = base;
        for (Tuple<Integer, String> l : levels) {
            if (points >= l.getLeft()) result = l;
        }
        return result;
    }
}


package com.cowboy.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Feedlot-specific choices are kept separate from app behavior and animal records. */
final class FeedlotConfig {
    static final int FORMAT_VERSION = 2;

    List<String> eastPens = new ArrayList<>();
    List<String> westPens = new ArrayList<>();
    List<String> issues = new ArrayList<>();
    List<String> animalColors = new ArrayList<>();
    List<String> treatTagColors = new ArrayList<>();
    List<String> destinations = new ArrayList<>();

    static FeedlotConfig defaults() {
        FeedlotConfig config = new FeedlotConfig();
        config.issues = new ArrayList<>();
        config.issues.add("Respiratory");
        config.issues.add("Footrot");
        config.issues.add("Buller");
        config.issues.add("Other");
        config.animalColors = new ArrayList<>();
        config.animalColors.add("Black");
        config.animalColors.add("Red");
        config.animalColors.add("Gray");
        config.animalColors.add("Brown");
        config.treatTagColors = new ArrayList<>();
        config.treatTagColors.add("White");
        config.treatTagColors.add("Green");
        config.treatTagColors.add("Yellow");
        config.treatTagColors.add("Red");
        config.destinations = new ArrayList<>();
        config.destinations.add("8 — Hospital");
        config.destinations.add("WSP — Hospital");
        config.destinations.add("SP — Buller / fat ration");
        config.destinations.add("44SP — Buller / fat ration");
        return config;
    }

    List<String> pensForSection(String section) {
        return "West".equals(section) ? westPens : eastPens;
    }

    List<String> allPens() {
        List<String> result = new ArrayList<>(eastPens);
        result.addAll(westPens);
        return result;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("formatVersion", FORMAT_VERSION);
        json.put("name", "Cowboy feedlot configuration");
        json.put("sections", new JSONObject()
                .put("East", toArray(eastPens))
                .put("West", toArray(westPens)));
        json.put("issues", toArray(issues));
        json.put("animalColors", toArray(animalColors));
        json.put("treatTagColors", toArray(treatTagColors));
        json.put("destinations", toArray(destinations));
        return json;
    }

    static FeedlotConfig fromJson(JSONObject json) throws JSONException {
        int version = json.optInt("formatVersion", 0);
        if (version != 1 && version != FORMAT_VERSION) {
            throw new JSONException("Unsupported feedlot config version: " + version);
        }
        FeedlotConfig config = new FeedlotConfig();
        if (version == 1) {
            // Preserve an older flat pen list so users can distribute those pens into sections in Settings.
            config.eastPens = fromArray(json.optJSONArray("pens"));
        } else {
            JSONObject sections = json.getJSONObject("sections");
            config.eastPens = fromArray(sections.optJSONArray("East"));
            config.westPens = fromArray(sections.optJSONArray("West"));
        }
        config.issues = fromArray(json.getJSONArray("issues"));
        config.animalColors = fromArray(json.getJSONArray("animalColors"));
        config.treatTagColors = fromArray(json.getJSONArray("treatTagColors"));
        config.destinations = fromArray(json.getJSONArray("destinations"));
        if (config.allPens().isEmpty() || config.issues.isEmpty()) {
            throw new JSONException("At least one pen and one health issue are required.");
        }
        return config;
    }

    static List<String> parseLines(String value) {
        List<String> values = new ArrayList<>();
        for (String line : value.split("\\n")) {
            String item = line.trim();
            if (!item.isEmpty()) values.add(item);
        }
        return values;
    }

    static String toLines(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (text.length() > 0) text.append('\n');
            text.append(value);
        }
        return text.toString();
    }

    private static JSONArray toArray(List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        return array;
    }

    private static List<String> fromArray(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String item = array.optString(i, "").trim();
            if (!item.isEmpty()) values.add(item);
        }
        return values;
    }
}

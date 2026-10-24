package com.cowboy.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Feedlot-specific choices are kept separate from app behavior and animal records. */
final class FeedlotConfig {
    static final int FORMAT_VERSION = 6;

    List<String> eastPens = new ArrayList<>();
    List<String> westPens = new ArrayList<>();
    List<String> issues = new ArrayList<>();
    List<String> animalColors = new ArrayList<>();
    List<String> treatTagColors = new ArrayList<>();
    List<String> destinations = new ArrayList<>();
    Map<String, List<String>> symptomsByIssue = new LinkedHashMap<>();
    List<String> appearanceFlags = new ArrayList<>();
    List<String> baseTreatments = new ArrayList<>();
    List<String> secondaryTreatments = new ArrayList<>();
    Map<String, List<String>> secondaryByBase = new LinkedHashMap<>();
    Map<String, List<String>> defaultSecondaryByBase = new LinkedHashMap<>();
    Map<String, List<String>> requiredSecondaryByBase = new LinkedHashMap<>();

    static FeedlotConfig defaults() {
        FeedlotConfig config = new FeedlotConfig();
        config.eastPens = new ArrayList<>();
        Collections.addAll(config.eastPens,
                "A", "22", "21", "20", "8", "14", "13", "12", "11", "SP",
                "9", "E/D", "B/C", "10", "3", "4", "5", "6", "7");
        config.westPens = new ArrayList<>();
        Collections.addAll(config.westPens,
                "1", "2/15", "17", "31", "32", "33", "34", "35", "36", "37",
                "38", "40", "43", "44SP", "WSP", "45");
        config.issues = new ArrayList<>();
        config.issues.add("Respiratory");
        config.issues.add("Footrot");
        config.issues.add("Buller");
        config.issues.add("Other");
        config.symptomsByIssue.put("Respiratory", list("Snotty nose", "Head down", "Looks off", "Walks slow", "Redish Eyes"));
        config.symptomsByIssue.put("Footrot", list("Limping", "Sore foot", "Swollen foot", "Raw between toes", "Bad smell"));
        config.symptomsByIssue.put("Buller", list("Riding another animal", "Rough hair coat", "Weak", "Poor condition"));
        config.symptomsByIssue.put("Other", list("Looks off", "Not eating", "Weak"));
        Collections.addAll(config.appearanceFlags,
                "Dull", "Thin", "Lame", "Swollen", "Wound", "Rough coat", "Eye discharge", "Other");
        Collections.addAll(config.baseTreatments, "Baytril", "Loncor", "Increxxa", "LA200", "Resflor", "Excede");
        Collections.addAll(config.secondaryTreatments, "Tylan 200", "Dexamethasone", "Banamine");
        for (String base : config.baseTreatments) {
            config.secondaryByBase.put(base, list("Dexamethasone", "Banamine"));
            config.defaultSecondaryByBase.put(base, list());
        }
        config.secondaryByBase.get("LA200").add(0, "Tylan 200");
        config.requiredSecondaryByBase.put("LA200", list("Tylan 200"));
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
        json.put("symptomsByIssue", toObject(symptomsByIssue));
        json.put("appearanceFlags", toArray(appearanceFlags));
        json.put("baseTreatments", toArray(baseTreatments));
        json.put("secondaryTreatments", toArray(secondaryTreatments));
        json.put("secondaryByBase", toObject(secondaryByBase));
        json.put("defaultSecondaryByBase", toObject(defaultSecondaryByBase));
        json.put("requiredSecondaryByBase", toObject(requiredSecondaryByBase));
        json.put("animalColors", toArray(animalColors));
        json.put("treatTagColors", toArray(treatTagColors));
        json.put("destinations", toArray(destinations));
        return json;
    }

    static FeedlotConfig fromJson(JSONObject json) throws JSONException {
        int version = json.optInt("formatVersion", 0);
        if (version < 1 || version > FORMAT_VERSION) {
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
        FeedlotConfig defaults = defaults();
        JSONObject symptoms = json.optJSONObject("symptomsByIssue");
        config.symptomsByIssue = symptoms == null ? defaults.symptomsByIssue : fromObject(symptoms);
        config.appearanceFlags = json.has("appearanceFlags")
                ? fromArray(json.optJSONArray("appearanceFlags")) : defaults.appearanceFlags;
        config.baseTreatments = json.has("baseTreatments")
                ? fromArray(json.optJSONArray("baseTreatments")) : defaults.baseTreatments;
        config.secondaryTreatments = json.has("secondaryTreatments")
                ? fromArray(json.optJSONArray("secondaryTreatments")) : defaults.secondaryTreatments;
        JSONObject secondary = json.optJSONObject("secondaryByBase");
        config.secondaryByBase = secondary == null ? defaults.secondaryByBase : fromObject(secondary);
        JSONObject defaultSecondary = json.optJSONObject("defaultSecondaryByBase");
        config.defaultSecondaryByBase = defaultSecondary == null
                ? defaults.defaultSecondaryByBase : fromObject(defaultSecondary);
        JSONObject requiredSecondary = json.optJSONObject("requiredSecondaryByBase");
        config.requiredSecondaryByBase = requiredSecondary != null ? fromObject(requiredSecondary)
                : (json.has("baseTreatments") ? new LinkedHashMap<>() : defaults.requiredSecondaryByBase);
        if (version < FORMAT_VERSION && hasPreviousTreatmentDefaults(config)) {
            config.baseTreatments = defaults.baseTreatments;
            config.secondaryTreatments = defaults.secondaryTreatments;
            config.secondaryByBase = defaults.secondaryByBase;
            config.defaultSecondaryByBase = defaults.defaultSecondaryByBase;
            config.requiredSecondaryByBase = defaults.requiredSecondaryByBase;
        }
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

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }

    private static boolean hasPreviousTreatmentDefaults(FeedlotConfig config) {
        List<String> previousBases = list("Baytril", "Loncor", "Intrexa");
        List<String> previousSecondary = list("Banamine", "Dexamine", "Vitamin C");
        Map<String, List<String>> previousAllowed = new LinkedHashMap<>();
        previousAllowed.put("Baytril", list("Banamine", "Dexamine"));
        previousAllowed.put("Loncor", list("Banamine", "Dexamine", "Vitamin C"));
        previousAllowed.put("Intrexa", list("Banamine", "Dexamine"));
        Map<String, List<String>> previousDefault = new LinkedHashMap<>();
        previousDefault.put("Baytril", list());
        previousDefault.put("Loncor", list("Vitamin C"));
        previousDefault.put("Intrexa", list());
        return config.baseTreatments.equals(previousBases)
                && config.secondaryTreatments.equals(previousSecondary)
                && config.secondaryByBase.equals(previousAllowed)
                && config.defaultSecondaryByBase.equals(previousDefault);
    }

    private static JSONObject toObject(Map<String, List<String>> values) throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            object.put(entry.getKey(), toArray(entry.getValue()));
        }
        return object;
    }

    private static Map<String, List<String>> fromObject(JSONObject object) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            result.put(key, fromArray(object.optJSONArray(key)));
        }
        return result;
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

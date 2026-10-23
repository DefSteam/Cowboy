package com.cowboy.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Small local offline store for reports and the active feedlot config. */
final class RecordStore {
    private static final String PREFS = "cowboy_local_data";
    private static final String RECORDS = "animal_records";
    private static final String CONFIG = "feedlot_config";
    private final SharedPreferences preferences;

    RecordStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<AnimalRecord> records() {
        List<AnimalRecord> result = new ArrayList<>();
        JSONArray saved;
        try {
            saved = new JSONArray(preferences.getString(RECORDS, "[]"));
        } catch (JSONException ignored) {
            return result;
        }
        for (int i = 0; i < saved.length(); i++) {
            JSONObject value = saved.optJSONObject(i);
            if (value != null) result.add(AnimalRecord.fromJson(value));
        }
        return result;
    }

    void saveRecords(List<AnimalRecord> records) {
        JSONArray saved = new JSONArray();
        for (AnimalRecord record : records) {
            try { saved.put(record.toJson()); } catch (JSONException ignored) { }
        }
        preferences.edit().putString(RECORDS, saved.toString()).apply();
    }

    FeedlotConfig config() {
        try {
            String saved = preferences.getString(CONFIG, null);
            return saved == null ? FeedlotConfig.defaults()
                    : FeedlotConfig.fromJson(new JSONObject(saved));
        } catch (JSONException ignored) {
            return FeedlotConfig.defaults();
        }
    }

    void saveConfig(FeedlotConfig config) throws JSONException {
        preferences.edit().putString(CONFIG, config.toJson().toString()).apply();
    }
}

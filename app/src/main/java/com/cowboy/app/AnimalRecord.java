package com.cowboy.app;

import org.json.JSONException;
import org.json.JSONObject;

/** One sick-animal report and its follow-up treatment information. */
final class AnimalRecord {
    String id;
    String date;
    long createdAt;
    String pen;
    String issue;
    String animalColor;
    String animalTag;
    String notes;
    String status;
    String treatTag;
    String treatTagColor;
    String treatedWith;
    String treatmentNotes;
    String destinationPen;
    long treatedAt;

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("date", date);
        json.put("createdAt", createdAt);
        json.put("pen", pen);
        json.put("issue", issue);
        json.put("animalColor", animalColor);
        json.put("animalTag", animalTag);
        json.put("notes", notes);
        json.put("status", status);
        json.put("treatTag", treatTag);
        json.put("treatTagColor", treatTagColor);
        json.put("treatedWith", treatedWith);
        json.put("treatmentNotes", treatmentNotes);
        json.put("destinationPen", destinationPen);
        json.put("treatedAt", treatedAt);
        return json;
    }

    static AnimalRecord fromJson(JSONObject json) {
        AnimalRecord record = new AnimalRecord();
        record.id = json.optString("id", "");
        record.date = json.optString("date", "");
        record.createdAt = json.optLong("createdAt", 0);
        record.pen = json.optString("pen", "");
        record.issue = json.optString("issue", "");
        record.animalColor = json.optString("animalColor", "");
        record.animalTag = json.optString("animalTag", "");
        record.notes = json.optString("notes", "");
        record.status = json.optString("status", "Needs pulling");
        record.treatTag = json.optString("treatTag", "");
        record.treatTagColor = json.optString("treatTagColor", "");
        record.treatedWith = json.optString("treatedWith", "");
        record.treatmentNotes = json.optString("treatmentNotes", "");
        record.destinationPen = json.optString("destinationPen", "");
        record.treatedAt = json.optLong("treatedAt", 0);
        return record;
    }
}

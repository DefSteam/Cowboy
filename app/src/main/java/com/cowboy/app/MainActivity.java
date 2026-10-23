package com.cowboy.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Material-styled sick-animal entry, pull/treatment, and history flows. */
public final class MainActivity extends Activity {
    private static final int EXPORT_CONFIG = 401;
    private static final int IMPORT_CONFIG = 402;
    private static final int MENU_SETTINGS = 501;
    private static final int MENU_CHECK_UPDATES = 502;
    private static final int MENU_CRASH_REPORT = 503;

    private RecordStore store;
    private FeedlotConfig config;
    private String pendingExport;
    private File pendingUpdateApk;
    private String pageTitle = "Cowboy";

    private interface StringCallback {
        void accept(String value);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        store = new RecordStore(this);
        config = store.config();
        showHome();
        if (BuildConfig.DEBUG) promptForCrashReport();
    }

    private void showHome() {
        LinearLayout body = body();
        addHeading(body, "Daily pen walk", "Record sick animals now, then pull and treat them when the walk is done.");
        int count = 0;
        for (AnimalRecord record : store.records()) {
            if (today().equals(record.date) && !"Removed from list".equals(record.status)) count++;
        }
        MaterialCardView summary = card(body);
        addText(column(summary), count + (count == 1 ? " animal recorded today" : " animals recorded today"), 17, true);
        addPrimaryButton(body, "Enter sick animal", this::showNewRecord);
        addSecondaryButton(body, "Today's pull and treatment list", this::showToday);
        addSecondaryButton(body, "History", this::showHistory);
        showPage("Cowboy", body, false);
    }

    private void showNewRecord() {
        LinearLayout body = body();
        addHeading(body, "New sick animal", "Choose the pen and add a quick health note.");
        String[] penValue = {""};
        MaterialButton penButton = choiceButton(body, "Found in pen", "Tap to choose");
        penButton.setOnClickListener(view -> showPenSections(section -> showPenGrid(section, pen -> {
            penValue[0] = pen;
            setChoiceText(penButton, "Found in pen", pen);
        })));

        String[] issueValue = {""};
        MaterialButton issueButton = choiceButton(body, "Health category", "Tap to choose");
        LinearLayout symptomsPanel = new LinearLayout(this);
        symptomsPanel.setOrientation(LinearLayout.VERTICAL);
        body.addView(symptomsPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Set<String> selectedSymptoms = new LinkedHashSet<>();
        issueButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setTitle("Health category")
                .setItems(config.issues.toArray(new String[0]), (dialog, which) -> {
                    issueValue[0] = config.issues.get(which);
                    setChoiceText(issueButton, "Health category", issueValue[0]);
                    selectedSymptoms.clear();
                    symptomsPanel.removeAllViews();
                    addText(symptomsPanel, "Symptoms · " + issueValue[0], 16, true);
                    addFlagChips(symptomsPanel, config.symptomsByIssue.get(issueValue[0]), selectedSymptoms);
                }).setNegativeButton("Cancel", null).show());
        String[] animalColor = {""};
        addListChoice(body, "Animal color", config.animalColors, animalColor);
        TextInputLayout tagField = addInput(body, "Animal tag number", "Leave blank if missing or unreadable", false);
        tagField.getEditText().setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        addText(body, "How the animal looks", 16, true);
        Set<String> selectedAppearance = new LinkedHashSet<>();
        addFlagChips(body, config.appearanceFlags, selectedAppearance);
        TextInputLayout notesField = addInput(body, "Other details", "Optional notes", true);
        String[] treatColor = {""};
        addText(body, "Treat tag", 16, true);
        addText(body, "Choose the color flag if the animal has a treat tag.", 14, false);
        LinearLayout treatTagDetails = new LinearLayout(this);
        treatTagDetails.setOrientation(LinearLayout.VERTICAL);
        treatTagDetails.setVisibility(View.GONE);
        TextInputLayout treatTagField = addInput(treatTagDetails, "Treat tag number", "Example: C823", false);
        MaterialButton lastTreatDateButton = choiceButton(treatTagDetails, "Last treated", "Add month and day");
        String[] lastTreatDate = {""};
        lastTreatDateButton.setOnClickListener(view -> showMonthDayPicker(lastTreatDate, lastTreatDateButton));
        addFlagChips(body, config.treatTagColors, new LinkedHashSet<>(), treatColor, selected -> {
            boolean hasTag = !selected.isEmpty();
            treatTagDetails.setVisibility(hasTag ? View.VISIBLE : View.GONE);
            if (!hasTag) {
                treatTagField.getEditText().setText("");
                lastTreatDate[0] = "";
                lastTreatDateButton.setText("Last treated\nAdd month and day");
            }
        });
        body.addView(treatTagDetails, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addPrimaryButton(body, "Save animal", () -> {
            if (penValue[0].isEmpty() || issueValue[0].isEmpty() || animalColor[0].isEmpty()) {
                toast("Choose a pen, health issue, and animal color first");
                return;
            }
            AnimalRecord record = new AnimalRecord();
            record.id = UUID.randomUUID().toString();
            record.date = today();
            record.createdAt = System.currentTimeMillis();
            record.pen = penValue[0];
            record.issue = issueValue[0];
            record.animalColor = animalColor[0];
            record.animalTag = text(tagField);
            record.notes = text(notesField);
            record.symptoms = new ArrayList<>(selectedSymptoms);
            record.appearance = new ArrayList<>(selectedAppearance);
            record.treatTag = text(treatTagField);
            record.treatTagColor = treatColor[0];
            record.lastTreatDate = lastTreatDate[0];
            record.status = "Needs pulling";
            List<AnimalRecord> records = store.records();
            records.add(record);
            store.saveRecords(records);
            toast("Added to today's list");
            showToday();
        });
        showPage("New animal", body, true);
    }

    private void showPenSections(StringCallback onSection) {
        String[] sections = {"East", "West"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose a section")
                .setItems(sections, (dialog, which) -> onSection.accept(sections[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPenGrid(String section, StringCallback onPen) {
        List<String> pens = config.pensForSection(section);
        if (pens.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(section + " pens are not set up")
                    .setMessage("Add this section's pens under Settings before recording animals.")
                    .setNegativeButton("Close", null)
                    .setPositiveButton("Settings", (dialog, which) -> showConfig())
                    .show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pad = dp(12);
        grid.setPadding(pad, dp(4), pad, dp(4));
        final androidx.appcompat.app.AlertDialog[] dialogRef = {null};
        for (String pen : pens) {
            MaterialButton button = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(pen);
            button.setTextSize(17);
            button.setMinHeight(dp(56));
            button.setAllCaps(false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(button, params);
            button.setOnClickListener(view -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                onPen.accept(pen);
            });
        }
        scroll.addView(grid);
        dialogRef[0] = new MaterialAlertDialogBuilder(this)
                .setTitle(section + " · choose a pen")
                .setView(scroll)
                .setNegativeButton("Back to sections", (dialog, which) -> showPenSections(next -> showPenGrid(next, onPen)))
                .setNeutralButton("Cancel", null)
                .create();
        dialogRef[0].show();
    }

    private void showToday() {
        LinearLayout body = body();
        List<AnimalRecord> records = recordsForDate(today());
        List<AnimalRecord> watchlist = watchlistRecords();
        addHeading(body, "Today's list", formatDate(today()) + " · " + records.size()
                + (records.size() == 1 ? " animal" : " animals")
                + (watchlist.isEmpty() ? "" : " · " + watchlist.size() + " to check"));
        if (records.isEmpty() && watchlist.isEmpty()) {
            addText(body, "No animals on today's list. Add one when you find a sick animal.", 16, false);
        }
        for (AnimalRecord record : records) addRecordCard(body, record, true);
        if (!watchlist.isEmpty()) {
            addText(body, "Check from earlier", 20, true);
            for (AnimalRecord record : watchlist) addRecordCard(body, record, true);
        }
        addPrimaryButton(body, "Enter another sick animal", this::showNewRecord);
        showPage("Today's list", body, true);
    }

    private void showHistory() {
        LinearLayout body = body();
        addHeading(body, "History", "Past records and treatment follow-up.");
        List<AnimalRecord> records = store.records();
        Collections.sort(records, (a, b) -> {
            int byDate = b.date.compareTo(a.date);
            if (byDate != 0) return byDate;
            int aPen = config.allPens().indexOf(a.pen);
            int bPen = config.allPens().indexOf(b.pen);
            if (aPen != bPen) return Integer.compare(aPen, bPen);
            return Long.compare(b.createdAt, a.createdAt);
        });
        if (records.isEmpty()) addText(body, "Saved animal records will appear here.", 16, false);
        String lastDate = "";
        for (AnimalRecord record : records) {
            if (!record.date.equals(lastDate)) {
                lastDate = record.date;
                addText(body, formatDate(lastDate), 20, true);
            }
            addRecordCard(body, record, false);
        }
        showPage("History", body, true);
    }

    private void addRecordCard(LinearLayout parent, AnimalRecord record, boolean actions) {
        MaterialCardView card = card(parent);
        LinearLayout content = column(card);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView penTitle = addText(titleRow, "Pen " + record.pen, 19, true);
        penTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (actions && "Pulled".equals(record.status)) addPulledRecordMenu(titleRow, record);
        if (!record.watchlistFromDate.isEmpty()) {
            addText(content, "Added to today's pull list from " + formatDate(record.watchlistFromDate), 14, true);
        } else if (record.watchlist && !today().equals(record.date)) {
            addText(content, "Watchlist · from " + formatDate(record.date), 14, true);
        }
        addText(content, (record.animalTag.isEmpty() ? "Tag missing / unreadable" : "Tag " + record.animalTag)
                + " · " + record.animalColor, 15, false);
        addText(content, record.issue, 16, true);
        if (!record.symptoms.isEmpty()) addText(content, "Symptoms · " + android.text.TextUtils.join(", ", record.symptoms), 14, false);
        if (!record.appearance.isEmpty()) addText(content, "Appearance · " + android.text.TextUtils.join(", ", record.appearance), 14, false);
        if (!record.notes.isEmpty()) addText(content, "Other details · " + record.notes, 14, false);
        addText(content, "Status · " + record.status, 14, true);
        if (!record.treatTag.isEmpty()) addText(content, "Treat tag · " + record.treatTag
                + (record.treatTagColor.isEmpty() ? "" : " · " + record.treatTagColor), 14, false);
        if (!record.lastTreatDate.isEmpty()) addText(content, "Last treated · " + displayMonthDay(record.lastTreatDate), 14, false);
        if (record.treatedAt > 0) addText(content, "Treated · " + formatTime(record.treatedAt), 14, false);
        if (!record.treatedWith.isEmpty()) addText(content, "Treatment · " + record.treatedWith, 14, false);
        if (!record.treatmentNotes.isEmpty()) addText(content, "Notes · " + record.treatmentNotes, 14, false);
        if (!record.destinationPen.isEmpty()) addText(content, "Moved to · " + record.destinationPen, 14, false);
        if (actions) {
            if (record.watchlist && !today().equals(record.date)) {
                addPrimaryButton(content, "Mark to pull today", () -> pullFromWatchlist(record));
                addSecondaryButton(content, "Confirm looks fine", () -> resolveWatchlist(record));
            } else if ("Pulled".equals(record.status)) {
                addPrimaryButton(content, "Record treatment", () -> showTreatment(record));
            } else if (!"Treated".equals(record.status) && !"Looks fine".equals(record.status)) {
                addSecondaryButton(content, "Mark pulled", () -> setStatus(record, "Pulled"));
                addPrimaryButton(content, "Record treatment", () -> showTreatment(record));
                addSecondaryButton(content, "Looks fine / eating", () -> showLooksFineOptions(record));
            } else if ("Looks fine".equals(record.status)) {
                addSecondaryButton(content, "Add to watchlist", () -> addToWatchlist(record));
            }
        }
    }

    private void showLooksFineOptions(AnimalRecord target) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Animal looks fine")
                .setItems(new String[]{"Check Again Tomorrow", "Remove from list", "Cancel"},
                        (dialog, which) -> {
                            if (which == 0) addToWatchlist(target);
                            else if (which == 1) updateRecord(target, record -> {
                                record.status = "Removed from list";
                                record.watchlist = false;
                            });
                        })
                .show();
    }

    private void addPulledRecordMenu(LinearLayout parent, AnimalRecord record) {
        MaterialButton more = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        more.setText("⋮");
        more.setTextSize(22);
        more.setMinWidth(dp(44));
        more.setMinHeight(dp(44));
        more.setContentDescription("More animal actions");
        parent.addView(more, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        more.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add("Undo mark pulled").setOnMenuItemClickListener(item -> {
                setStatus(record, "Needs pulling");
                return true;
            });
            menu.show();
        });
    }

    private void addToWatchlist(AnimalRecord target) {
        updateRecord(target, record -> {
            record.status = "Watchlist";
            record.watchlist = true;
        });
    }

    private void resolveWatchlist(AnimalRecord target) {
        updateRecord(target, record -> {
            record.status = "Looks fine";
            record.watchlist = false;
        });
    }

    private void pullFromWatchlist(AnimalRecord target) {
        List<AnimalRecord> records = store.records();
        AnimalRecord pulledForward = copyForToday(target);
        for (AnimalRecord record : records) if (record.id.equals(target.id)) {
            record.watchlist = false;
            record.status = "Added to today's pull list";
        }
        records.add(pulledForward);
        store.saveRecords(records);
        showToday();
    }

    private AnimalRecord copyForToday(AnimalRecord source) {
        AnimalRecord copy = new AnimalRecord();
        copy.id = UUID.randomUUID().toString();
        copy.date = today();
        copy.createdAt = System.currentTimeMillis();
        copy.pen = source.pen;
        copy.issue = source.issue;
        copy.animalColor = source.animalColor;
        copy.animalTag = source.animalTag;
        copy.notes = source.notes;
        copy.symptoms = new ArrayList<>(source.symptoms);
        copy.appearance = new ArrayList<>(source.appearance);
        copy.status = "Needs pulling";
        copy.treatTag = source.treatTag;
        copy.treatTagColor = source.treatTagColor;
        copy.treatedWith = source.treatedWith;
        copy.treatmentNotes = source.treatmentNotes;
        copy.destinationPen = source.destinationPen;
        copy.treatedAt = source.treatedAt;
        copy.lastTreatDate = source.lastTreatDate;
        copy.watchlistFromDate = source.date;
        return copy;
    }

    private interface RecordUpdate { void apply(AnimalRecord record); }

    private void updateRecord(AnimalRecord target, RecordUpdate update) {
        List<AnimalRecord> records = store.records();
        for (AnimalRecord record : records) if (record.id.equals(target.id)) update.apply(record);
        store.saveRecords(records);
        showToday();
    }

    private void setStatus(AnimalRecord target, String status) {
        List<AnimalRecord> records = store.records();
        for (AnimalRecord record : records) if (record.id.equals(target.id)) {
            record.status = status;
            if (!"Watchlist".equals(status)) record.watchlist = false;
        }
        store.saveRecords(records);
        showToday();
    }

    private void showTreatment(AnimalRecord target) {
        LinearLayout form = column(null);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        TextInputLayout treatTag = addInput(form, "Treat tag number", "If newly added", false);
        treatTag.getEditText().setText(target.treatTag);
        String[] tagColor = {target.treatTagColor};
        addListChoice(form, "Treat tag color", withNone(config.treatTagColors), tagColor);
        String[] treatment = {target.treatedWith};
        MaterialButton treatmentButton = choiceButton(form, "Treatment", treatment[0].isEmpty()
                ? "Tap to choose" : treatment[0]);
        treatmentButton.setOnClickListener(view -> showTreatmentPicker(treatment, treatmentButton));
        TextInputLayout notes = addInput(form, "Treatment notes", "Optional", true);
        notes.getEditText().setText(target.treatmentNotes);
        String[] destination = {target.destinationPen};
        addListChoice(form, "Moved to pen", withNone(config.destinations), destination);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Record treatment · pen " + target.pen)
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save treatment", (dialog, which) -> {
                    List<AnimalRecord> records = store.records();
                    for (AnimalRecord record : records) if (record.id.equals(target.id)) {
                        if (!text(treatTag).isEmpty()) record.treatTag = text(treatTag);
                        record.treatTagColor = tagColor[0];
                        record.treatedWith = treatment[0];
                        record.treatmentNotes = text(notes);
                        record.destinationPen = destination[0];
                        record.status = "Treated";
                        record.watchlist = false;
                        record.treatedAt = System.currentTimeMillis();
                    }
                    store.saveRecords(records);
                    toast("Treatment saved");
                    showToday();
                }).show();
    }

    private void showTreatmentPicker(String[] selection, MaterialButton button) {
        String[] presets = {
                "Baytril",
                "Baytril + Banamine",
                "Loncor",
                "Loncor + Vitamin C",
                "Loncor + Banamine",
                "Loncor + Banamine + Vitamin",
                "Intrexa",
                "Intrexa + Banamine",
                "Build custom…"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose treatment")
                .setItems(presets, (dialog, which) -> {
                    if (which == presets.length - 1) {
                        showCustomTreatmentPicker(selection, button);
                    } else {
                        selection[0] = presets[which];
                        setChoiceText(button, "Treatment", selection[0]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomTreatmentPicker(String[] selection, MaterialButton button) {
        List<String> ingredients = new ArrayList<>();
        Collections.addAll(ingredients, "Baytril", "Loncor", "Intrexa", "Banamine", "Vitamin C");
        Set<String> selected = new LinkedHashSet<>();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        addText(content, "Select the products to combine", 15, false);
        addFlagChips(content, ingredients, selected);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Build treatment")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Use treatment", (dialog, which) -> {
                    if (selected.isEmpty()) {
                        toast("Select at least one product");
                        return;
                    }
                    List<String> ordered = new ArrayList<>();
                    for (String ingredient : ingredients) if (selected.contains(ingredient)) ordered.add(ingredient);
                    selection[0] = android.text.TextUtils.join(" + ", ordered);
                    setChoiceText(button, "Treatment", selection[0]);
                })
                .show();
    }

    private void showConfig() {
        LinearLayout body = body();
        addHeading(body, "Feedlot settings", "One pen per line. Add all pens to the correct section.");
        TextInputLayout east = addInput(body, "East section pens", "Examples: 1, 2 and 15, A", true);
        east.getEditText().setText(FeedlotConfig.toLines(config.eastPens));
        TextInputLayout west = addInput(body, "West section pens", "Examples: 43, 44SP, WSP", true);
        west.getEditText().setText(FeedlotConfig.toLines(config.westPens));
        TextInputLayout issues = addInput(body, "Health issues · shown in this order", "Respiratory, Footrot, Buller", true);
        issues.getEditText().setText(FeedlotConfig.toLines(config.issues));
        TextInputLayout animalColors = addInput(body, "Animal colors", "Black, Red, Gray, Brown", true);
        animalColors.getEditText().setText(FeedlotConfig.toLines(config.animalColors));
        TextInputLayout tagColors = addInput(body, "Treat tag colors", "White, Green, Yellow, Red", true);
        tagColors.getEditText().setText(FeedlotConfig.toLines(config.treatTagColors));
        TextInputLayout symptoms = addInput(body, "Symptoms by health category",
                "One category per line: Respiratory: Snotty nose, Head down", true);
        symptoms.getEditText().setText(toFlagLines(config.symptomsByIssue));
        TextInputLayout appearance = addInput(body, "How the animal looks · flags",
                "One option per line", true);
        appearance.getEditText().setText(FeedlotConfig.toLines(config.appearanceFlags));
        TextInputLayout destinations = addInput(body, "Treatment destinations and purpose", "8 — Hospital", true);
        destinations.getEditText().setText(FeedlotConfig.toLines(config.destinations));
        addPrimaryButton(body, "Save feedlot settings", () -> {
            FeedlotConfig updated = new FeedlotConfig();
            updated.eastPens = FeedlotConfig.parseLines(text(east));
            updated.westPens = FeedlotConfig.parseLines(text(west));
            updated.issues = FeedlotConfig.parseLines(text(issues));
            updated.animalColors = FeedlotConfig.parseLines(text(animalColors));
            updated.treatTagColors = FeedlotConfig.parseLines(text(tagColors));
            updated.symptomsByIssue = parseFlagLines(text(symptoms));
            updated.appearanceFlags = FeedlotConfig.parseLines(text(appearance));
            updated.destinations = FeedlotConfig.parseLines(text(destinations));
            if (updated.allPens().isEmpty() || updated.issues.isEmpty() || updated.animalColors.isEmpty()
                    || updated.appearanceFlags.isEmpty()) {
                toast("Add at least one pen, health issue, animal color, and appearance flag");
                return;
            }
            try {
                store.saveConfig(updated);
                config = updated;
                toast("Feedlot settings saved");
            } catch (JSONException e) { toast("Could not save settings"); }
        });
        addSecondaryButton(body, "Export setup to share", this::beginExport);
        addSecondaryButton(body, "Import shared setup", this::beginImport);
        showPage("Settings", body, true);
    }

    private void beginExport() {
        try {
            pendingExport = config.toJson().toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "cowboy-feedlot-config.json");
            startActivityForResult(intent, EXPORT_CONFIG);
        } catch (JSONException e) { toast("Could not prepare settings file"); }
    }

    private void beginImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, IMPORT_CONFIG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == EXPORT_CONFIG) {
                OutputStream output = getContentResolver().openOutputStream(uri);
                if (output == null) throw new java.io.IOException("Cannot open destination");
                output.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                output.close();
                toast("Feedlot setup exported");
            } else if (requestCode == IMPORT_CONFIG) {
                InputStream input = getContentResolver().openInputStream(uri);
                if (input == null) throw new java.io.IOException("Cannot open selected file");
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                StringBuilder contents = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) contents.append(line);
                reader.close();
                FeedlotConfig imported = FeedlotConfig.fromJson(new JSONObject(contents.toString()));
                store.saveConfig(imported);
                config = imported;
                toast("Feedlot setup imported");
                showConfig();
            }
        } catch (Exception e) { toast("Could not use that setup file: " + e.getMessage()); }
    }

    private void showPage(String title, LinearLayout content, boolean back) {
        pageTitle = title;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF8F9F5);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), bars.top, view.getPaddingRight(), bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(title);
        toolbar.setTitleTextColor(0xFF20251F);
        toolbar.setBackgroundColor(0xFFF8F9F5);
        if (back) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationContentDescription("Back");
            toolbar.setNavigationOnClickListener(view -> showHome());
        }
        MenuItem updates = toolbar.getMenu().add(0, MENU_CHECK_UPDATES, 0, "Check for update");
        updates.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        MenuItem settings = toolbar.getMenu().add(0, MENU_SETTINGS, 1, "Settings");
        settings.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        if (BuildConfig.DEBUG) {
            MenuItem crashReport = toolbar.getMenu().add(0, MENU_CRASH_REPORT, 2, "Share crash report");
            crashReport.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_CHECK_UPDATES) {
                checkForUpdate();
                return true;
            }
            if (item.getItemId() == MENU_SETTINGS) { showConfig(); return true; }
            if (item.getItemId() == MENU_CRASH_REPORT) { shareLatestCrashReport(); return true; }
            return false;
        });
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void promptForCrashReport() {
        File report = CrashReporter.latestReport(this);
        if (report == null) return;
        String lastPrompted = getPreferences(MODE_PRIVATE).getString("last_crash_report_prompted", "");
        if (report.getName().equals(lastPrompted)) return;
        getPreferences(MODE_PRIVATE).edit().putString("last_crash_report_prompted", report.getName()).apply();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cowboy stopped unexpectedly")
                .setMessage("A debug crash report was saved on this device. Share it with the app developer?")
                .setPositiveButton("Share report", (dialog, which) -> shareCrashReport(report))
                .setNegativeButton("Later", null)
                .show();
    }

    private void shareLatestCrashReport() {
        File report = CrashReporter.latestReport(this);
        if (report == null) {
            toast("No crash report is saved on this device");
            return;
        }
        shareCrashReport(report);
    }

    private void shareCrashReport(File report) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", report);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Cowboy debug crash report");
        share.putExtra(Intent.EXTRA_TEXT, "Cowboy crash report attached. Please review it before forwarding.");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.setClipData(android.content.ClipData.newUri(getContentResolver(), "Crash report", uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share crash report"));
    }

    private void checkForUpdate() {
        toast("Checking for updates…");
        new Thread(() -> {
            try {
                JSONObject release = getJson("https://api.github.com/repos/DefSteam/Cowboy/releases/latest");
                String tag = release.optString("tag_name", "");
                String current = "v" + BuildConfig.VERSION_NAME;
                if (tag.isEmpty() || compareVersions(tag, current) <= 0) {
                    runOnUiThread(() -> toast("Cowboy is up to date"));
                    return;
                }
                String assetName = "Cowboy-" + tag.replaceFirst("^[vV]", "") + ".apk";
                String downloadUrl = "";
                org.json.JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset != null && assetName.equals(asset.optString("name"))) {
                            downloadUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
                if (downloadUrl.isEmpty()) {
                    throw new Exception("The latest release has no " + assetName + " asset.");
                }
                String finalDownloadUrl = downloadUrl;
                runOnUiThread(() -> toast("Downloading Cowboy " + tag + "…"));
                File updatesDir = new File(getCacheDir(), "updates");
                if (!updatesDir.exists() && !updatesDir.mkdirs()) throw new Exception("Could not create update folder.");
                File apk = new File(updatesDir, "Cowboy.apk");
                downloadFile(finalDownloadUrl, apk);
                runOnUiThread(() -> installUpdate(apk));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not check for updates." : e.getMessage();
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle("Update check failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show());
            }
        }, "cowboy-update-check").start();
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Cowboy-Android-App");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new Exception(status == 404
                        ? "No public Cowboy release was found. Make the repository public and publish a release first."
                        : "GitHub returned HTTP " + status + ".");
            }
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
            }
            return new JSONObject(json.toString());
        } finally {
            connection.disconnect();
        }
    }

    private void downloadFile(String address, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Cowboy-Android-App");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new Exception("APK download failed (HTTP " + status + ").");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            if (destination.length() == 0) throw new Exception("GitHub returned an empty APK.");
        } finally {
            connection.disconnect();
        }
    }

    private int compareVersions(String left, String right) {
        String[] a = left.replaceFirst("^[vV]", "").split("[.-]");
        String[] b = right.replaceFirst("^[vV]", "").split("[.-]");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            long av = i < a.length ? numericVersionPart(a[i]) : 0;
            long bv = i < b.length ? numericVersionPart(b[i]) : 0;
            int compare = Long.compare(av, bv);
            if (compare != 0) return compare;
        }
        return 0;
    }

    private long numericVersionPart(String part) {
        String digits = part.replaceAll("[^0-9].*$", "");
        try { return digits.isEmpty() ? 0 : Long.parseLong(digits); }
        catch (NumberFormatException e) { return 0; }
    }

    private void installUpdate(File apk) {
        pendingUpdateApk = apk;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Allow Cowboy updates")
                    .setMessage("Allow installs from Cowboy in Android settings, then return here to finish installing the update.")
                    .setNegativeButton("Later", (dialog, which) -> pendingUpdateApk = null)
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                    })
                    .show();
            return;
        }
        launchInstaller(apk);
    }

    private void launchInstaller(File apk) {
        Uri contentUri = androidx.core.content.FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", apk);
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setData(contentUri);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(install);
            pendingUpdateApk = null;
        } catch (android.content.ActivityNotFoundException e) {
            toast("Android could not open the package installer");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingUpdateApk != null && pendingUpdateApk.isFile()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls())) {
            File apk = pendingUpdateApk;
            pendingUpdateApk = null;
            launchInstaller(apk);
        }
    }

    private List<AnimalRecord> recordsForDate(String date) {
        List<AnimalRecord> result = new ArrayList<>();
        for (AnimalRecord record : store.records()) {
            if (date.equals(record.date) && !"Removed from list".equals(record.status)) result.add(record);
        }
        List<String> penOrder = config.allPens();
        Collections.sort(result, (a, b) -> {
            int ap = penOrder.indexOf(a.pen), bp = penOrder.indexOf(b.pen);
            if (ap != bp) return Integer.compare(ap, bp);
            return Long.compare(a.createdAt, b.createdAt);
        });
        return result;
    }

    private List<AnimalRecord> watchlistRecords() {
        List<AnimalRecord> result = new ArrayList<>();
        String currentDate = today();
        for (AnimalRecord record : store.records()) {
            if (record.watchlist && record.date.compareTo(currentDate) < 0) result.add(record);
        }
        Collections.sort(result, (a, b) -> {
            int byDate = b.date.compareTo(a.date);
            return byDate != 0 ? byDate : Long.compare(b.createdAt, a.createdAt);
        });
        return result;
    }

    private LinearLayout body() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        body.setPadding(pad, dp(10), pad, dp(28));
        return body;
    }

    private MaterialCardView card(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(1));
        card.setStrokeColor(ColorStateList.valueOf(0xFFE0E4DB));
        card.setStrokeWidth(dp(1));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(10));
        parent.addView(card, params);
        return card;
    }

    private LinearLayout column(ViewGroup parent) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(13), dp(16), dp(13));
        if (parent != null) parent.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    private void addHeading(LinearLayout parent, String title, String subtitle) {
        addText(parent, title, 25, true);
        TextView sub = addText(parent, subtitle, 15, false);
        sub.setPadding(0, dp(2), 0, dp(14));
    }

    private TextView addText(LinearLayout parent, String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(0xFF242922);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(4));
        parent.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private MaterialButton button(LinearLayout parent, String label, boolean primary, Runnable action) {
        MaterialButton button = new MaterialButton(this, null, primary
                ? com.google.android.material.R.attr.materialButtonStyle
                : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(primary ? 54 : 48));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        parent.addView(button, params);
        return button;
    }

    private void addPrimaryButton(LinearLayout parent, String label, Runnable action) {
        button(parent, label, true, action);
    }

    private void addSecondaryButton(LinearLayout parent, String label, Runnable action) {
        button(parent, label, false, action);
    }

    private MaterialButton choiceButton(LinearLayout parent, String label, String value) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label + "\n" + value);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinHeight(dp(58));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(7));
        parent.addView(button, params);
        return button;
    }

    private void setChoiceText(MaterialButton button, String label, String value) {
        button.setText(label + "\n" + value);
    }

    private void addListChoice(LinearLayout parent, String label, List<String> options, String[] selected) {
        String initial = selected[0].isEmpty() ? "Tap to choose" : selected[0];
        MaterialButton choice = choiceButton(parent, label, initial);
        choice.setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    selected[0] = options.get(which);
                    if (selected[0].equals("— None —")) selected[0] = "";
                    setChoiceText(choice, label, selected[0].isEmpty() ? "None" : selected[0]);
                }).setNegativeButton("Cancel", null).show());
    }

    private void addFlagChips(LinearLayout parent, List<String> options, Set<String> selected) {
        addFlagChips(parent, options, selected, null, null);
    }

    private void addFlagChips(LinearLayout parent, List<String> options, Set<String> selected,
                               String[] singleSelection, StringCallback onSingleSelected) {
        if (options == null) return;
        ChipGroup group = new ChipGroup(this);
        group.setSingleLine(false);
        group.setSingleSelection(singleSelection != null);
        group.setSelectionRequired(false);
        group.setChipSpacingHorizontal(dp(8));
        group.setChipSpacingVertical(dp(4));
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        groupParams.setMargins(0, dp(2), 0, dp(7));
        parent.addView(group, groupParams);
        int primary = 0xFF456348;
        int outline = 0xFF8A9088;
        int[][] checkedState = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
        ColorStateList fills = new ColorStateList(checkedState, new int[] { primary, Color.WHITE });
        ColorStateList strokes = new ColorStateList(checkedState, new int[] { primary, outline });
        ColorStateList textColors = new ColorStateList(checkedState, new int[] { Color.WHITE, 0xFF242922 });
        for (String option : options) {
            Chip chip = new Chip(this);
            chip.setText(option);
            chip.setTextSize(14);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setChipBackgroundColor(fills);
            chip.setChipStrokeColor(strokes);
            chip.setChipStrokeWidth(dp(1));
            chip.setTextColor(textColors);
            chip.setChipCornerRadius(dp(20));
            chip.setChecked(singleSelection == null
                    ? selected.contains(option) : option.equals(singleSelection[0]));
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (singleSelection == null) {
                    if (checked) selected.add(option);
                    else selected.remove(option);
                } else if (checked) {
                    singleSelection[0] = option;
                    if (onSingleSelected != null) onSingleSelected.accept(option);
                } else if (option.equals(singleSelection[0])) {
                    singleSelection[0] = "";
                    if (onSingleSelected != null) onSingleSelected.accept("");
                }
            });
            group.addView(chip);
        }
    }

    private void showMonthDayPicker(String[] storedDate, MaterialButton button) {
        Calendar todayCalendar = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(this, (pickerView, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.DAY_OF_MONTH, 1);
            chosen.set(Calendar.MONTH, month);
            chosen.set(Calendar.DAY_OF_MONTH, day);
            chosen.set(Calendar.HOUR_OF_DAY, 12);
            chosen.set(Calendar.MINUTE, 0);
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            Calendar today = Calendar.getInstance();
            if (chosen.after(today)) chosen.add(Calendar.YEAR, -1);
            storedDate[0] = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.getTime());
            button.setText("Last treated\n" + new SimpleDateFormat("MMM d", Locale.getDefault()).format(chosen.getTime()));
        }, todayCalendar.get(Calendar.YEAR), todayCalendar.get(Calendar.MONTH), todayCalendar.get(Calendar.DAY_OF_MONTH));
        int yearId = getResources().getIdentifier("year", "id", "android");
        if (yearId != 0 && picker.getDatePicker().findViewById(yearId) != null) {
            picker.getDatePicker().findViewById(yearId).setVisibility(View.GONE);
        }
        picker.setTitle("Last treated · month and day");
        picker.show();
    }

    private TextInputLayout addInput(LinearLayout parent, String label, String hint, boolean multiline) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.setMargins(0, dp(5), 0, dp(7));
        parent.addView(field, fieldParams);

        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTextSize(14);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        field.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextInputLayout layout = new TextInputLayout(this);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText edit = new TextInputEditText(this);
        edit.setTextSize(16);
        edit.setHint(hint);
        edit.setSingleLine(!multiline);
        if (multiline) {
            edit.setMinLines(2);
            edit.setGravity(Gravity.TOP | Gravity.START);
        }
        layout.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        field.addView(layout, params);
        return layout;
    }

    private String text(TextInputLayout field) {
        return field.getEditText() == null ? "" : field.getEditText().getText().toString().trim();
    }

    private String toFlagLines(Map<String, List<String>> flags) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : flags.entrySet()) {
            if (result.length() > 0) result.append('\n');
            result.append(entry.getKey()).append(": ")
                    .append(android.text.TextUtils.join(", ", entry.getValue()));
        }
        return result.toString();
    }

    private Map<String, List<String>> parseFlagLines(String value) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (String line : value.split("\\n")) {
            int separator = line.indexOf(':');
            if (separator < 1) continue;
            String category = line.substring(0, separator).trim();
            List<String> flags = new ArrayList<>();
            for (String flag : line.substring(separator + 1).split(",")) {
                String clean = flag.trim();
                if (!clean.isEmpty()) flags.add(clean);
            }
            if (!category.isEmpty()) result.put(category, flags);
        }
        return result;
    }

    private String displayMonthDay(String iso) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
        } catch (Exception ignored) { return iso; }
    }

    private List<String> withNone(List<String> choices) {
        List<String> values = new ArrayList<>();
        values.add("— None —");
        values.addAll(choices);
        return values;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String formatDate(String iso) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(date);
        } catch (Exception ignored) { return iso; }
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if ("Cowboy".equals(pageTitle)) super.onBackPressed();
        else showHome();
    }
}

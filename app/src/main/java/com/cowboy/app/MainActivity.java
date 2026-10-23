package com.cowboy.app;

import android.app.Activity;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
        for (AnimalRecord record : store.records()) if (today().equals(record.date)) count++;
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
        addListChoice(body, "What's wrong?", config.issues, issueValue);
        String[] animalColor = {""};
        addListChoice(body, "Animal color", config.animalColors, animalColor);
        TextInputLayout tagField = addInput(body, "Animal tag number", "Leave blank if missing or unreadable", false);
        TextInputLayout notesField = addInput(body, "What is wrong / how it looks", "Add details that help identify the animal", true);
        TextInputLayout treatTagField = addInput(body, "Treat tag number", "Optional", false);
        String[] treatColor = {""};
        addListChoice(body, "Treat tag color (optional)", withNone(config.treatTagColors), treatColor);

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
            record.treatTag = text(treatTagField);
            record.treatTagColor = treatColor[0];
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
        addHeading(body, "Today's list", formatDate(today()) + " · " + records.size() + (records.size() == 1 ? " animal" : " animals"));
        if (records.isEmpty()) addText(body, "No animals recorded yet. Start with the first sick animal you find.", 16, false);
        for (AnimalRecord record : records) addRecordCard(body, record, true);
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
        addText(content, "Pen " + record.pen, 19, true);
        addText(content, (record.animalTag.isEmpty() ? "Tag missing / unreadable" : "Tag " + record.animalTag)
                + " · " + record.animalColor, 15, false);
        addText(content, record.issue, 16, true);
        if (!record.notes.isEmpty()) addText(content, record.notes, 14, false);
        addText(content, "Status · " + record.status, 14, true);
        if (!record.treatTag.isEmpty()) addText(content, "Treat tag · " + record.treatTag
                + (record.treatTagColor.isEmpty() ? "" : " · " + record.treatTagColor), 14, false);
        if (record.treatedAt > 0) addText(content, "Treated · " + formatTime(record.treatedAt), 14, false);
        if (!record.treatedWith.isEmpty()) addText(content, "Treatment · " + record.treatedWith, 14, false);
        if (!record.treatmentNotes.isEmpty()) addText(content, "Notes · " + record.treatmentNotes, 14, false);
        if (!record.destinationPen.isEmpty()) addText(content, "Moved to · " + record.destinationPen, 14, false);
        if (actions) {
            if (!"Pulled".equals(record.status) && !"Treated".equals(record.status)) {
                addSecondaryButton(content, "Mark pulled", () -> setStatus(record, "Pulled"));
            }
            if (!"Treated".equals(record.status)) addPrimaryButton(content, "Record treatment", () -> showTreatment(record));
            if (!"Could not find".equals(record.status) && !"Treated".equals(record.status)) {
                addSecondaryButton(content, "Could not find", () -> setStatus(record, "Could not find"));
            }
        }
    }

    private void setStatus(AnimalRecord target, String status) {
        List<AnimalRecord> records = store.records();
        for (AnimalRecord record : records) if (record.id.equals(target.id)) record.status = status;
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
        TextInputLayout treatedWith = addInput(form, "What was he treated with?", "Product / treatment", false);
        treatedWith.getEditText().setText(target.treatedWith);
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
                        record.treatedWith = text(treatedWith);
                        record.treatmentNotes = text(notes);
                        record.destinationPen = destination[0];
                        record.status = "Treated";
                        record.treatedAt = System.currentTimeMillis();
                    }
                    store.saveRecords(records);
                    toast("Treatment saved");
                    showToday();
                }).show();
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
        TextInputLayout destinations = addInput(body, "Treatment destinations and purpose", "8 — Hospital", true);
        destinations.getEditText().setText(FeedlotConfig.toLines(config.destinations));
        addPrimaryButton(body, "Save feedlot settings", () -> {
            FeedlotConfig updated = new FeedlotConfig();
            updated.eastPens = FeedlotConfig.parseLines(text(east));
            updated.westPens = FeedlotConfig.parseLines(text(west));
            updated.issues = FeedlotConfig.parseLines(text(issues));
            updated.animalColors = FeedlotConfig.parseLines(text(animalColors));
            updated.treatTagColors = FeedlotConfig.parseLines(text(tagColors));
            updated.destinations = FeedlotConfig.parseLines(text(destinations));
            if (updated.allPens().isEmpty() || updated.issues.isEmpty() || updated.animalColors.isEmpty()) {
                toast("Add at least one pen, health issue, and animal color");
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
                if (downloadUrl.isEmpty()) throw new Exception("The latest release has no Cowboy.apk asset.");
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
        for (AnimalRecord record : store.records()) if (date.equals(record.date)) result.add(record);
        List<String> penOrder = config.allPens();
        Collections.sort(result, (a, b) -> {
            int ap = penOrder.indexOf(a.pen), bp = penOrder.indexOf(b.pen);
            if (ap != bp) return Integer.compare(ap, bp);
            return Long.compare(a.createdAt, b.createdAt);
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

    private TextInputLayout addInput(LinearLayout parent, String label, String hint, boolean multiline) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setHint(label);
        layout.setHelperText(hint.isEmpty() ? null : hint);
        TextInputEditText edit = new TextInputEditText(this);
        edit.setTextSize(16);
        edit.setHint(hint);
        edit.setSingleLine(!multiline);
        if (multiline) {
            edit.setMinLines(2);
            edit.setGravity(Gravity.TOP | Gravity.START);
        }
        layout.addView(edit, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(6));
        parent.addView(layout, params);
        return layout;
    }

    private String text(TextInputLayout field) {
        return field.getEditText() == null ? "" : field.getEditText().getText().toString().trim();
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

package ca.patrick.bgglogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 1;
    private static final int REQ_GALLERY = 2;
    private static final int MENU_GAMES = 1;
    private static final int MENU_SETTINGS = 2;

    private Uri pendingCameraUri;
    private String objectId;
    private String objectLabel;
    private List<BggClient.Game> lastResults = new ArrayList<>();
    private boolean busy;

    private ImageView preview;
    private TextView status, bggSelected;
    private ProgressBar progress;
    private EditText gameName, date, location, minutes, comments;
    private LinearLayout players;
    private Button btnLog, btnChoose;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        BggClient.installCookies();
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.preview);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        gameName = findViewById(R.id.game_name);
        bggSelected = findViewById(R.id.bgg_selected);
        date = findViewById(R.id.date);
        location = findViewById(R.id.location);
        minutes = findViewById(R.id.minutes);
        comments = findViewById(R.id.comments);
        players = findViewById(R.id.players);
        btnLog = findViewById(R.id.btn_log);
        btnChoose = findViewById(R.id.btn_choose);

        findViewById(R.id.btn_camera).setOnClickListener(v -> takePhoto());
        findViewById(R.id.btn_gallery).setOnClickListener(v -> pickPhoto());
        findViewById(R.id.btn_search).setOnClickListener(v -> searchBgg(gameName.getText().toString(), false));
        btnChoose.setOnClickListener(v -> showPicker());
        findViewById(R.id.btn_add_player).setOnClickListener(v -> addPlayerRow("", "", false));
        btnLog.setOnClickListener(v -> logPlay());

        if (state != null) {
            String u = state.getString("cameraUri");
            if (u != null) pendingCameraUri = Uri.parse(u);
            objectId = state.getString("objectId");
            objectLabel = state.getString("objectLabel");
        } else {
            date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            location.setText(Prefs.get(this, Prefs.LOCATION));
            addPlayerRow("", "", false);
        }
        refreshSelection();

        if (state == null && Prefs.get(this, Prefs.API_KEY).isEmpty()) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pendingCameraUri != null) out.putString("cameraUri", pendingCameraUri.toString());
        out.putString("objectId", objectId);
        out.putString("objectLabel", objectLabel);
    }

    // ---------- Menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_GAMES, 0, R.string.my_games).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, MENU_SETTINGS, 1, R.string.settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_GAMES) {
            startActivity(new Intent(this, GamesActivity.class));
            return true;
        }
        if (item.getItemId() == MENU_SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------- Photo ----------

    private void takePhoto() {
        if (busy) return;
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "bgg_" + System.currentTimeMillis() + ".jpg");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BGG Logger");
        pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (pendingCameraUri == null) {
            toast(getString(R.string.no_camera));
            return;
        }
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            discardPending();
            toast(getString(R.string.no_camera));
        }
    }

    private void pickPhoto() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, getString(R.string.choose_photo)), REQ_GALLERY);
    }

    private void discardPending() {
        if (pendingCameraUri != null) {
            try {
                getContentResolver().delete(pendingCameraUri, null, null);
            } catch (Exception ignored) {
                // nothing to clean
            }
            pendingCameraUri = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && pendingCameraUri != null) {
                Uri u = pendingCameraUri;
                pendingCameraUri = null;
                handleImage(u);
            } else {
                discardPending();
            }
        } else if (requestCode == REQ_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleImage(data.getData());
        }
    }

    private void handleImage(Uri uri) {
        String key = Prefs.get(this, Prefs.API_KEY);
        if (key.isEmpty()) {
            toast(getString(R.string.need_key));
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        String model = Prefs.get(this, Prefs.MODEL);
        String token = Prefs.get(this, Prefs.BGG_TOKEN);
        setBusy(true, getString(R.string.reading_photo));
        objectId = null;
        objectLabel = null;
        refreshSelection();

        new Thread(() -> {
            try {
                Bitmap bmp = ImageUtil.loadScaled(getContentResolver(), uri, 1568);
                byte[] jpeg = ImageUtil.toJpeg(bmp, 85);
                runOnUiThread(() -> preview.setImageBitmap(bmp));

                JSONObject result = ClaudeClient.analyze(key, model, jpeg);
                runOnUiThread(() -> {
                    applyAnalysis(result);
                    setBusy(true, getString(R.string.searching_bgg));
                });

                String term = result.optString("search_term", "").trim();
                if (term.isEmpty()) term = result.optString("game_name", "").trim();
                List<BggClient.Game> games = term.isEmpty()
                        ? new ArrayList<>() : BggClient.search(term, token);
                String finalTerm = term;
                String wantedYear = result.optString("year_published", "");
                runOnUiThread(() -> {
                    setBusy(false, null);
                    applyResults(games, finalTerm, wantedYear, true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e));
            }
        }).start();
    }

    private void applyAnalysis(JSONObject r) {
        String name = r.optString("game_name", "");
        gameName.setText(name);
        String msg = getString(R.string.identified, name, r.optString("confidence", "?"));
        String notes = r.optString("notes", "");
        if (!notes.isEmpty()) msg += "\n" + notes;
        JSONArray exp = r.optJSONArray("expansions");
        if (exp != null && exp.length() > 0) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < exp.length(); i++) names.add(exp.optString(i));
            msg += "\n" + getString(R.string.expansions_seen, String.join(", ", names));
        }
        status.setText(msg);

        players.removeAllViews();
        JSONArray list = r.optJSONArray("players");
        int count = list == null ? 0 : list.length();
        double best = Double.NEGATIVE_INFINITY;
        boolean allScored = count > 0;
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JSONObject p = list.optJSONObject(i);
            if (p == null) continue;
            String pName = p.optString("name", "").trim();
            if (pName.isEmpty() || "null".equals(pName)) pName = p.optString("color", "").trim();
            String score = "";
            if (p.has("score") && !p.isNull("score")) {
                double d = p.optDouble("score", Double.NaN);
                if (!Double.isNaN(d)) {
                    score = d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
                    best = Math.max(best, d);
                } else {
                    allScored = false;
                }
            } else {
                allScored = false;
            }
            rows.add(new String[]{pName, score});
        }
        for (String[] row : rows) {
            boolean win = allScored && !row[1].isEmpty() && Double.parseDouble(row[1]) == best;
            addPlayerRow(row[0], row[1], win);
        }
        if (rows.isEmpty()) addPlayerRow("", "", false);
    }

    // ---------- BGG game choice ----------

    private void searchBgg(String query, boolean autoPick) {
        query = query.trim();
        if (query.isEmpty() || busy) return;
        String token = Prefs.get(this, Prefs.BGG_TOKEN);
        String q = query;
        setBusy(true, getString(R.string.searching_bgg));
        new Thread(() -> {
            try {
                List<BggClient.Game> games = BggClient.search(q, token);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    applyResults(games, q, "", autoPick);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e));
            }
        }).start();
    }

    private void applyResults(List<BggClient.Game> games, String query, String year, boolean autoPick) {
        lastResults = games;
        if (games.isEmpty()) {
            objectId = null;
            objectLabel = null;
            refreshSelection();
            bggSelected.setText(R.string.bgg_no_match);
            return;
        }
        if (autoPick) select(best(games, query, year));
        else showPicker();
    }

    private static BggClient.Game best(List<BggClient.Game> games, String query, String year) {
        BggClient.Game nameMatch = null;
        for (BggClient.Game g : games) {
            if (g.name.equalsIgnoreCase(query)) {
                if (!year.isEmpty() && year.equals(g.year)) return g;
                if (nameMatch == null) nameMatch = g;
            }
        }
        return nameMatch != null ? nameMatch : games.get(0);
    }

    private void showPicker() {
        if (lastResults.isEmpty()) {
            searchBgg(gameName.getText().toString(), false);
            return;
        }
        String[] labels = new String[lastResults.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = lastResults.get(i).label();
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_game)
                .setItems(labels, (d, which) -> select(lastResults.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void select(BggClient.Game g) {
        objectId = g.id;
        objectLabel = g.label();
        refreshSelection();
    }

    private void refreshSelection() {
        if (objectId == null) {
            bggSelected.setText(R.string.bgg_none);
            btnLog.setEnabled(false);
        } else {
            bggSelected.setText(getString(R.string.bgg_selected, objectLabel));
            btnLog.setEnabled(!busy);
        }
    }

    // ---------- Players ----------

    private void addPlayerRow(String name, String score, boolean win) {
        View row = getLayoutInflater().inflate(R.layout.row_player, players, false);
        ((EditText) row.findViewById(R.id.player_name)).setText(name);
        ((EditText) row.findViewById(R.id.player_score)).setText(score);
        ((CheckBox) row.findViewById(R.id.player_win)).setChecked(win);
        ImageButton remove = row.findViewById(R.id.player_remove);
        remove.setOnClickListener(v -> players.removeView(row));
        players.addView(row);
    }

    // ---------- Logging ----------

    private void logPlay() {
        if (busy) return;
        if (objectId == null) {
            toast(getString(R.string.need_game));
            return;
        }
        String user = Prefs.get(this, Prefs.BGG_USER);
        String pass = Prefs.get(this, Prefs.BGG_PASS);
        if (user.isEmpty() || pass.isEmpty()) {
            toast(getString(R.string.need_bgg));
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        String day = date.getText().toString().trim();
        if (!day.matches("\\d{4}-\\d{2}-\\d{2}")) {
            toast(getString(R.string.bad_date));
            return;
        }

        BggClient.Play play = new BggClient.Play();
        play.objectId = objectId;
        play.date = day;
        play.location = location.getText().toString().trim();
        play.comments = comments.getText().toString().trim();
        try {
            play.minutes = Integer.parseInt(minutes.getText().toString().trim());
        } catch (NumberFormatException e) {
            play.minutes = 0;
        }
        for (int i = 0; i < players.getChildCount(); i++) {
            View row = players.getChildAt(i);
            BggClient.Player p = new BggClient.Player();
            p.name = ((EditText) row.findViewById(R.id.player_name)).getText().toString().trim();
            p.score = ((EditText) row.findViewById(R.id.player_score)).getText().toString().trim();
            p.win = ((CheckBox) row.findViewById(R.id.player_win)).isChecked();
            if (!p.name.isEmpty() || !p.score.isEmpty()) play.players.add(p);
        }

        String gameId = objectId;
        String gameLabel = objectLabel;
        String token = Prefs.get(this, Prefs.BGG_TOKEN);
        setBusy(true, getString(R.string.logging));
        new Thread(() -> {
            try {
                String playId = BggClient.logPlay(user, pass, play);

                Store.PlayRecord rec = new Store.PlayRecord();
                rec.playId = playId;
                rec.objectId = gameId;
                rec.name = gameLabel.replaceAll(" \\(\\d+\\)$", "");
                rec.date = day;
                Store store = Store.get(this);
                store.addPlay(rec);

                runOnUiThread(() -> {
                    setBusy(false, getString(R.string.logged, playId));
                    btnLog.setEnabled(false);
                    new AlertDialog.Builder(this)
                            .setMessage(getString(R.string.logged, playId))
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.view_game, (d, w) -> {
                                Intent i = new Intent(this, GameDetailActivity.class);
                                i.putExtra(GameDetailActivity.EXTRA_ID, gameId);
                                startActivity(i);
                            })
                            .show();
                });

                // Fetch cover and themes for "My games" in the background.
                GameInfo known = store.getGame(gameId);
                if (known == null || !known.hasDetails()) {
                    for (GameInfo g : BggClient.fetchGames(Collections.singletonList(gameId), token)) store.putGame(g);
                }
            } catch (Exception e) {
                runOnUiThread(() -> showError(e));
            }
        }).start();
    }

    // ---------- UI helpers ----------

    private void setBusy(boolean b, String message) {
        busy = b;
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        if (message != null) status.setText(message);
        btnLog.setEnabled(!b && objectId != null);
        btnChoose.setEnabled(!b);
    }

    private void showError(Exception e) {
        setBusy(false, null);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        status.setText(getString(R.string.error_prefix, msg));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}

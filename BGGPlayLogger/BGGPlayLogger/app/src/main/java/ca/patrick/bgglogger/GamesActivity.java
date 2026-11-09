package ca.patrick.bgglogger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "My games": every game played, filterable by theme, with cover pictures. */
public class GamesActivity extends Activity {
    private static final int MENU_SYNC = 1;
    private static final String ALL = "";
    private static final String NO_THEME = "\u0000none";

    private Spinner themes;
    private TextView status, empty;
    private ProgressBar progress;
    private final GameAdapter adapter = new GameAdapter();
    private final List<String> themeKeys = new ArrayList<>();
    private List<Store.GameSummary> all = new ArrayList<>();
    private String currentTheme = ALL;
    private boolean syncing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        BggClient.installCookies();
        setContentView(R.layout.activity_games);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        themes = findViewById(R.id.theme_spinner);
        status = findViewById(R.id.games_status);
        progress = findViewById(R.id.games_progress);
        empty = findViewById(R.id.games_empty);
        ListView list = findViewById(R.id.games_list);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Store.GameSummary s = adapter.getItem(position);
            Intent i = new Intent(this, GameDetailActivity.class);
            i.putExtra(GameDetailActivity.EXTRA_ID, s.info.id);
            startActivity(i);
        });
        themes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < themeKeys.size()) {
                    currentTheme = themeKeys.get(position);
                    applyFilter();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (state != null) currentTheme = state.getString("theme", ALL);
        reload();

        Store store = Store.get(this);
        if (store.lastSync() == 0 || !store.gameIdsMissingDetails().isEmpty()) sync();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("theme", currentTheme);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SYNC, 0, R.string.sync).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == MENU_SYNC) {
            sync();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------- Sync from BGG ----------

    private void sync() {
        if (syncing) return;
        String user = Prefs.get(this, Prefs.BGG_USER);
        String token = Prefs.get(this, Prefs.BGG_TOKEN);
        syncing = true;
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.sync_history);

        new Thread(() -> {
            Store store = Store.get(this);
            String problem = null;
            if (user.isEmpty()) {
                problem = getString(R.string.need_bgg);
            } else {
                try {
                    store.replacePlaysFromBgg(BggClient.fetchPlays(user, token));
                } catch (Exception e) {
                    problem = e.getMessage();
                }
            }
            runOnUiThread(this::reload);

            List<String> missing = store.gameIdsMissingDetails();
            if (!missing.isEmpty()) {
                runOnUiThread(() -> status.setText(getString(R.string.sync_covers, missing.size())));
                for (GameInfo g : BggClient.fetchGames(missing, token)) store.putGame(g);
            }

            String finalProblem = problem;
            runOnUiThread(() -> {
                syncing = false;
                progress.setVisibility(View.GONE);
                reload();
                if (finalProblem != null) {
                    status.setText(getString(R.string.sync_partial, finalProblem));
                }
            });
        }).start();
    }

    // ---------- Display ----------

    private void reload() {
        all = Store.get(this).summaries();

        Map<String, Integer> counts = new LinkedHashMap<>();
        int noTheme = 0;
        for (Store.GameSummary s : all) {
            if (s.info.categories.isEmpty()) noTheme++;
            for (String c : s.info.categories) counts.merge(c, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        Collections.sort(sorted, (a, b) -> {
            int d = b.getValue() - a.getValue();
            return d != 0 ? d : a.getKey().compareToIgnoreCase(b.getKey());
        });

        themeKeys.clear();
        List<String> labels = new ArrayList<>();
        themeKeys.add(ALL);
        labels.add(getString(R.string.all_themes, all.size()));
        for (Map.Entry<String, Integer> e : sorted) {
            themeKeys.add(e.getKey());
            labels.add(e.getKey() + " (" + e.getValue() + ")");
        }
        if (noTheme > 0 && !counts.isEmpty()) {
            themeKeys.add(NO_THEME);
            labels.add(getString(R.string.no_theme, noTheme));
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themes.setAdapter(a);
        int idx = themeKeys.indexOf(currentTheme);
        if (idx < 0) {
            idx = 0;
            currentTheme = ALL;
        }
        themes.setSelection(idx, false);
        applyFilter();

        int total = 0;
        for (Store.GameSummary s : all) total += s.plays;
        if (!syncing) status.setText(getString(R.string.games_summary, all.size(), total));
    }

    private void applyFilter() {
        List<Store.GameSummary> shown = new ArrayList<>();
        for (Store.GameSummary s : all) {
            if (ALL.equals(currentTheme)
                    || (NO_THEME.equals(currentTheme) && s.info.categories.isEmpty())
                    || s.info.categories.contains(currentTheme)) {
                shown.add(s);
            }
        }
        adapter.set(shown);
    }

    private final class GameAdapter extends BaseAdapter {
        private List<Store.GameSummary> items = new ArrayList<>();

        void set(List<Store.GameSummary> list) {
            items = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Store.GameSummary getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : getLayoutInflater().inflate(R.layout.row_game, parent, false);
            Store.GameSummary s = items.get(position);
            ((TextView) v.findViewById(R.id.row_title)).setText(s.info.name);
            ((TextView) v.findViewById(R.id.row_plays)).setText(
                    getResources().getQuantityString(R.plurals.plays_count, s.plays, s.plays, s.lastPlayed));
            TextView themesView = v.findViewById(R.id.row_themes);
            themesView.setText(String.join(" · ", s.info.categories));
            themesView.setVisibility(s.info.categories.isEmpty() ? View.GONE : View.VISIBLE);
            ImageView img = v.findViewById(R.id.row_cover);
            int px = (int) (72 * getResources().getDisplayMetrics().density);
            ImageLoader.get(GamesActivity.this).load(s.info.thumbUrl(), img, px, R.drawable.cover_placeholder);
            return v;
        }
    }
}

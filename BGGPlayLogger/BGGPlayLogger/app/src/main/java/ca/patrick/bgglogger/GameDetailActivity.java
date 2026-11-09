package ca.patrick.bgglogger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One game: big cover, themes, mechanics, designers, description and your plays. */
public class GameDetailActivity extends Activity {
    static final String EXTRA_ID = "objectId";

    private String objectId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        BggClient.installCookies();
        setContentView(R.layout.activity_game_detail);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        objectId = getIntent().getStringExtra(EXTRA_ID);
        if (objectId == null) {
            finish();
            return;
        }
        findViewById(R.id.btn_open_bgg).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BggClient.gamePageUrl(objectId)))));
        show();

        GameInfo known = Store.get(this).getGame(objectId);
        if (known == null || !known.hasDetails()) {
            findViewById(R.id.detail_progress).setVisibility(View.VISIBLE);
            String token = Prefs.get(this, Prefs.BGG_TOKEN);
            new Thread(() -> {
                for (GameInfo g : BggClient.fetchGames(Collections.singletonList(objectId), token)) {
                    Store.get(this).putGame(g);
                }
                runOnUiThread(() -> {
                    findViewById(R.id.detail_progress).setVisibility(View.GONE);
                    show();
                });
            }).start();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void show() {
        Store store = Store.get(this);
        GameInfo g = store.getGame(objectId);
        Store.GameSummary summary = store.summary(objectId);
        if (g == null) {
            g = summary != null ? summary.info : new GameInfo();
        }
        setTitle(g.name.isEmpty() ? getString(R.string.app_name) : g.name);

        ImageView cover = findViewById(R.id.detail_cover);
        int width = getResources().getDisplayMetrics().widthPixels;
        ImageLoader.get(this).load(g.coverUrl(), cover, width, R.drawable.cover_placeholder);

        ((TextView) findViewById(R.id.detail_title)).setText(
                g.year.isEmpty() ? g.name : g.name + " (" + g.year + ")");

        List<String> facts = new ArrayList<>();
        if (!g.minPlayers.isEmpty()) {
            String players = g.minPlayers.equals(g.maxPlayers) || g.maxPlayers.isEmpty()
                    ? g.minPlayers : g.minPlayers + "–" + g.maxPlayers;
            facts.add(getString(R.string.fact_players, players));
        }
        if (!g.playTime.isEmpty() && !"0".equals(g.playTime)) facts.add(getString(R.string.fact_time, g.playTime));
        if (!g.rating.isEmpty()) facts.add(getString(R.string.fact_rating, oneDecimal(g.rating)));
        if (!g.weight.isEmpty() && !g.weight.startsWith("0")) facts.add(getString(R.string.fact_weight, oneDecimal(g.weight)));
        setText(R.id.detail_facts, String.join("  ·  ", facts));

        setSection(R.id.detail_themes, R.string.label_themes, g.categories);
        setSection(R.id.detail_mechanics, R.string.label_mechanics, g.mechanics);
        setSection(R.id.detail_designers, R.string.label_designers, g.designers);

        if (summary != null) {
            String dates = String.join(", ", summary.dates.subList(0, Math.min(12, summary.dates.size())));
            if (summary.dates.size() > 12) dates += "…";
            setText(R.id.detail_plays, getResources().getQuantityString(
                    R.plurals.my_plays, summary.plays, summary.plays, dates));
        } else {
            setText(R.id.detail_plays, "");
        }

        String desc = g.description;
        CharSequence text = desc.contains("<")
                ? Html.fromHtml(desc, Html.FROM_HTML_MODE_COMPACT) : desc.replace("&#10;", "\n");
        TextView d = findViewById(R.id.detail_description);
        d.setText(text);
        d.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setSection(int viewId, int labelRes, List<String> items) {
        setText(viewId, items.isEmpty() ? "" : getString(labelRes) + " " + String.join(", ", items));
    }

    private void setText(int viewId, String s) {
        TextView t = findViewById(viewId);
        t.setText(s);
        t.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static String oneDecimal(String s) {
        try {
            return String.format(java.util.Locale.getDefault(), "%.1f", Double.parseDouble(s));
        } catch (Exception e) {
            return s;
        }
    }
}

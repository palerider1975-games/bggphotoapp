package ca.patrick.bgglogger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText key, model, user, pass, location, token;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        key = findViewById(R.id.set_api_key);
        model = findViewById(R.id.set_model);
        user = findViewById(R.id.set_bgg_user);
        pass = findViewById(R.id.set_bgg_pass);
        location = findViewById(R.id.set_location);
        token = findViewById(R.id.set_bgg_token);

        key.setText(Prefs.get(this, Prefs.API_KEY));
        model.setText(Prefs.get(this, Prefs.MODEL));
        user.setText(Prefs.get(this, Prefs.BGG_USER));
        pass.setText(Prefs.get(this, Prefs.BGG_PASS));
        location.setText(Prefs.get(this, Prefs.LOCATION));
        token.setText(Prefs.get(this, Prefs.BGG_TOKEN));

        findViewById(R.id.link_console).setOnClickListener(v -> open("https://console.anthropic.com/settings/keys"));
        findViewById(R.id.link_bgg_apps).setOnClickListener(v -> open("https://boardgamegeek.com/applications"));
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            // no browser
        }
    }

    private void save() {
        Prefs.put(this, Prefs.API_KEY, key.getText().toString());
        Prefs.put(this, Prefs.MODEL, model.getText().toString());
        Prefs.put(this, Prefs.BGG_USER, user.getText().toString());
        Prefs.put(this, Prefs.BGG_PASS, pass.getText().toString());
        Prefs.put(this, Prefs.LOCATION, location.getText().toString());
        Prefs.put(this, Prefs.BGG_TOKEN, token.getText().toString());
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

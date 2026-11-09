package ca.patrick.bgglogger;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings stored only on this phone. */
final class Prefs {
    static final String API_KEY = "anthropic_key";
    static final String MODEL = "model";
    static final String BGG_USER = "bgg_user";
    static final String BGG_PASS = "bgg_pass";
    static final String LOCATION = "default_location";
    static final String BGG_TOKEN = "bgg_token";

    static final String DEFAULT_MODEL = "claude-sonnet-5-5";

    private Prefs() {}

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static String get(Context c, String key) {
        String v = of(c).getString(key, "");
        if (MODEL.equals(key) && (v == null || v.trim().isEmpty())) return DEFAULT_MODEL;
        return v == null ? "" : v.trim();
    }

    static void put(Context c, String key, String value) {
        of(c).edit().putString(key, value == null ? "" : value.trim()).apply();
    }
}

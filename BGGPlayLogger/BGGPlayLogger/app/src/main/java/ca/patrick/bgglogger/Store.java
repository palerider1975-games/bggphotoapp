package ca.patrick.bgglogger;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Play history and game details, saved in a JSON file on the phone. */
final class Store {

    static final class PlayRecord {
        String playId = "", objectId = "", name = "", date = "";
        int quantity = 1;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("playId", playId).put("objectId", objectId).put("name", name)
                        .put("date", date).put("quantity", quantity);
            } catch (Exception ignored) {
                // cannot fail
            }
            return o;
        }

        static PlayRecord fromJson(JSONObject o) {
            PlayRecord p = new PlayRecord();
            p.playId = o.optString("playId");
            p.objectId = o.optString("objectId");
            p.name = o.optString("name");
            p.date = o.optString("date");
            p.quantity = Math.max(1, o.optInt("quantity", 1));
            return p;
        }

        String key() {
            return playId.isEmpty() ? objectId + "|" + date + "|" + name : playId;
        }
    }

    /** One game in "My games": its details plus how often it was played. */
    static final class GameSummary {
        GameInfo info;
        int plays;
        String lastPlayed = "";
        final List<String> dates = new ArrayList<>();
    }

    private static Store instance;

    private final File file;
    private final Map<String, PlayRecord> plays = new LinkedHashMap<>();
    private final Map<String, GameInfo> games = new HashMap<>();
    private long lastSync;

    static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context c) {
        file = new File(c.getFilesDir(), "history.json");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            JSONObject root = new JSONObject(new String(data, 0, off, StandardCharsets.UTF_8));
            JSONArray p = root.optJSONArray("plays");
            if (p != null) {
                for (int i = 0; i < p.length(); i++) {
                    JSONObject o = p.optJSONObject(i);
                    if (o != null) {
                        PlayRecord r = PlayRecord.fromJson(o);
                        plays.put(r.key(), r);
                    }
                }
            }
            JSONObject g = root.optJSONObject("games");
            if (g != null) {
                JSONArray names = g.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        JSONObject o = g.optJSONObject(names.optString(i));
                        if (o != null) {
                            GameInfo info = GameInfo.fromJson(o);
                            games.put(info.id, info);
                        }
                    }
                }
            }
            lastSync = root.optLong("lastSync", 0);
        } catch (Exception ignored) {
            // corrupt file: start fresh
        }
    }

    private void save() {
        try {
            JSONArray p = new JSONArray();
            for (PlayRecord r : plays.values()) p.put(r.toJson());
            JSONObject g = new JSONObject();
            for (GameInfo info : games.values()) g.put(info.id, info.toJson());
            JSONObject root = new JSONObject().put("plays", p).put("games", g).put("lastSync", lastSync);
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(file)) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
            // best effort
        }
    }

    synchronized void addPlay(PlayRecord r) {
        // A play logged by this app and later synced from BGG has the same id, so it is not counted twice.
        plays.put(r.key(), r);
        save();
    }

    synchronized void replacePlaysFromBgg(List<PlayRecord> fromBgg) {
        // BGG is the full truth when sync works: keep only plays that BGG knows about.
        plays.clear();
        for (PlayRecord r : fromBgg) plays.put(r.key(), r);
        lastSync = System.currentTimeMillis();
        save();
    }

    synchronized void putGame(GameInfo info) {
        if (info == null || info.id.isEmpty()) return;
        games.put(info.id, info);
        save();
    }

    synchronized GameInfo getGame(String id) {
        return games.get(id);
    }

    synchronized long lastSync() {
        return lastSync;
    }

    synchronized List<String> gameIdsMissingDetails() {
        List<String> out = new ArrayList<>();
        for (PlayRecord r : plays.values()) {
            GameInfo g = games.get(r.objectId);
            if ((g == null || !g.hasDetails()) && !out.contains(r.objectId) && !r.objectId.isEmpty()) {
                out.add(r.objectId);
            }
        }
        return out;
    }

    /** Games played, most played first. */
    synchronized List<GameSummary> summaries() {
        Map<String, GameSummary> byId = new LinkedHashMap<>();
        for (PlayRecord r : plays.values()) {
            GameSummary s = byId.get(r.objectId);
            if (s == null) {
                s = new GameSummary();
                GameInfo info = games.get(r.objectId);
                if (info == null) {
                    info = new GameInfo();
                    info.id = r.objectId;
                    info.name = r.name;
                }
                if (info.name.isEmpty()) info.name = r.name;
                s.info = info;
                byId.put(r.objectId, s);
            }
            s.plays += r.quantity;
            s.dates.add(r.date);
            if (r.date.compareTo(s.lastPlayed) > 0) s.lastPlayed = r.date;
        }
        List<GameSummary> list = new ArrayList<>(byId.values());
        for (GameSummary s : list) Collections.sort(s.dates, Collections.reverseOrder());
        Collections.sort(list, (a, b) -> {
            if (a.plays != b.plays) return b.plays - a.plays;
            return a.info.name.compareToIgnoreCase(b.info.name);
        });
        return list;
    }

    synchronized GameSummary summary(String objectId) {
        for (GameSummary s : summaries()) if (s.info.id.equals(objectId)) return s;
        return null;
    }
}

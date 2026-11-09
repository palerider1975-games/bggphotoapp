package ca.patrick.bgglogger;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BoardGameGeek: game search, sign-in and play logging.
 * BGG has no official API for logging plays, so this uses the same endpoints as the website.
 */
final class BggClient {
    private static final String BASE = "https://boardgamegeek.com";

    static final class Game {
        final String id, name, year;

        Game(String id, String name, String year) {
            this.id = id;
            this.name = name;
            this.year = year == null ? "" : year;
        }

        String label() {
            return year.isEmpty() ? name : name + " (" + year + ")";
        }
    }

    static final class Player {
        String name = "", score = "";
        boolean win;
    }

    static final class Play {
        String objectId, date, location = "", comments = "";
        int minutes;
        final List<Player> players = new ArrayList<>();
    }

    private BggClient() {}

    static void installCookies() {
        if (!(CookieHandler.getDefault() instanceof CookieManager)) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    // ---------- Search ----------

    static List<Game> search(String query, String xmlToken) throws IOException {
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        List<Game> games = new ArrayList<>();
        IOException lastError = null;

        // 1) The search the BGG website itself uses (JSON, no token needed).
        try {
            Map<String, String> h = new HashMap<>();
            h.put("Accept", "application/json");
            Http.Response r = Http.request("GET",
                    BASE + "/search/boardgame?nosession=1&showcount=20&q=" + q, h, null);
            if (r.code == 200 && r.body.trim().startsWith("{")) {
                JSONArray items = new JSONObject(r.body).optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it == null) continue;
                        String id = str(it, "objectid");
                        String name = str(it, "name");
                        if (!id.isEmpty() && !name.isEmpty()) games.add(new Game(id, name, str(it, "yearpublished")));
                    }
                }
            } else {
                lastError = new IOException("BGG search (" + r.code + ")");
            }
        } catch (IOException e) {
            lastError = e;
        } catch (JSONException e) {
            lastError = new IOException("BGG search: unexpected reply");
        }
        if (!games.isEmpty()) return games;

        // 2) Official XML API (needs a registered app token since late 2025).
        try {
            Map<String, String> h = new HashMap<>();
            if (xmlToken != null && !xmlToken.isEmpty()) h.put("Authorization", "Bearer " + xmlToken);
            Http.Response r = Http.request("GET",
                    BASE + "/xmlapi2/search?type=boardgame,boardgameexpansion&query=" + q, h, null);
            if (r.code == 200) {
                games.addAll(parseXmlSearch(r.body));
                return games;
            }
            lastError = new IOException("BGG XML search (" + r.code + ")");
        } catch (IOException e) {
            lastError = e;
        }
        if (lastError != null) throw lastError;
        return games;
    }

    private static String str(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    private static List<Game> parseXmlSearch(String xml) throws IOException {
        List<Game> out = new ArrayList<>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(xml));
            String id = null, name = null, year = null;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    switch (p.getName()) {
                        case "item":
                            id = p.getAttributeValue(null, "id");
                            name = null;
                            year = null;
                            break;
                        case "name":
                            if (name == null || "primary".equals(p.getAttributeValue(null, "type"))) {
                                name = p.getAttributeValue(null, "value");
                            }
                            break;
                        case "yearpublished":
                            year = p.getAttributeValue(null, "value");
                            break;
                        default:
                            break;
                    }
                } else if (ev == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                    if (id != null && name != null) out.add(new Game(id, name, year));
                    id = null;
                }
            }
        } catch (Exception e) {
            throw new IOException("BGG XML search: could not read reply");
        }
        return out;
    }

    // ---------- Sign-in and logging ----------

    static void login(String user, String pass) throws IOException {
        try {
            JSONObject body = new JSONObject().put("credentials",
                    new JSONObject().put("username", user).put("password", pass));
            Map<String, String> h = new HashMap<>();
            h.put("Content-Type", "application/json");
            h.put("Accept", "application/json");
            Http.Response r = Http.request("POST", BASE + "/login/api/v1", h, body.toString());
            if (r.code == 200 || r.code == 204) return;
            if (r.code == 400 || r.code == 401 || r.code == 403) {
                throw new IOException("BGG sign-in refused: check your username and password in Settings.");
            }
            throw new IOException("BGG sign-in (" + r.code + "): " + Http.snippet(r.body));
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    /** Signs in, logs the play and returns the new play id. */
    static String logPlay(String user, String pass, Play play) throws IOException {
        login(user, pass);
        try {
            JSONArray players = new JSONArray();
            for (Player pl : play.players) {
                players.put(new JSONObject()
                        .put("username", "")
                        .put("userid", 0)
                        .put("repeat", "true")
                        .put("name", pl.name)
                        .put("selected", "false")
                        .put("score", pl.score)
                        .put("win", pl.win)
                        .put("new", false)
                        .put("color", "")
                        .put("rating", 0)
                        .put("position", ""));
            }
            JSONObject body = new JSONObject()
                    .put("playdate", play.date)
                    .put("date", play.date + "T05:00:00.000Z")
                    .put("comments", play.comments)
                    .put("length", play.minutes)
                    .put("minutes", play.minutes)
                    .put("hours", 0)
                    .put("twitter", false)
                    .put("location", play.location)
                    .put("objectid", play.objectId)
                    .put("objecttype", "thing")
                    .put("quantity", 1)
                    .put("action", "save")
                    .put("ajax", 1)
                    .put("players", players);

            Map<String, String> h = new HashMap<>();
            h.put("Content-Type", "application/json;charset=UTF-8");
            h.put("Accept", "application/json");
            h.put("Origin", BASE);
            h.put("Referer", BASE + "/");
            Http.Response r = Http.request("POST", BASE + "/geekplay.php", h, body.toString());

            JSONObject reply;
            try {
                reply = new JSONObject(r.body);
            } catch (JSONException e) {
                throw new IOException("Unexpected BGG reply (" + r.code + "): " + Http.snippet(r.body));
            }
            if (reply.has("error")) throw new IOException("BGG: " + reply.optString("error"));
            String playId = reply.optString("playid", "");
            if (playId.isEmpty()) throw new IOException("BGG did not confirm the play: " + Http.snippet(r.body));
            return playId;
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    // ---------- Play history ----------

    /** All plays of a user, from the official XML API (100 per page). */
    static List<Store.PlayRecord> fetchPlays(String user, String token) throws IOException {
        List<Store.PlayRecord> out = new ArrayList<>();
        String u = URLEncoder.encode(user, StandardCharsets.UTF_8.name());
        for (int page = 1; page <= 50; page++) {
            Http.Response r = xmlGet(BASE + "/xmlapi2/plays?username=" + u + "&page=" + page, token);
            if (r.code == 401 || r.code == 403) {
                throw new IOException("BGG refused the play history. Add a BGG app token in Settings.");
            }
            if (r.code != 200) throw new IOException("BGG play history (" + r.code + ")");
            int[] total = new int[1];
            List<Store.PlayRecord> pagePlays = parsePlays(r.body, total);
            out.addAll(pagePlays);
            if (pagePlays.isEmpty() || out.size() >= total[0]) break;
            sleep(1500);
        }
        return out;
    }

    private static List<Store.PlayRecord> parsePlays(String xml, int[] total) throws IOException {
        List<Store.PlayRecord> out = new ArrayList<>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(xml));
            Store.PlayRecord cur = null;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("plays".equals(tag)) {
                        total[0] = parseInt(p.getAttributeValue(null, "total"));
                    } else if ("play".equals(tag)) {
                        cur = new Store.PlayRecord();
                        cur.playId = nz(p.getAttributeValue(null, "id"));
                        cur.date = nz(p.getAttributeValue(null, "date"));
                        cur.quantity = Math.max(1, parseInt(p.getAttributeValue(null, "quantity")));
                    } else if ("item".equals(tag) && cur != null) {
                        cur.objectId = nz(p.getAttributeValue(null, "objectid"));
                        cur.name = nz(p.getAttributeValue(null, "name"));
                    }
                } else if (ev == XmlPullParser.END_TAG && "play".equals(p.getName()) && cur != null) {
                    if (!cur.objectId.isEmpty()) out.add(cur);
                    cur = null;
                }
            }
        } catch (Exception e) {
            throw new IOException("Could not read BGG play history");
        }
        return out;
    }

    // ---------- Game details ----------

    /** Details for up to 20 games per request from the XML API; falls back to the website's JSON one game at a time. */
    static List<GameInfo> fetchGames(List<String> ids, String token) {
        List<GameInfo> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 20) {
            List<String> batch = ids.subList(i, Math.min(ids.size(), i + 20));
            List<GameInfo> got = null;
            try {
                Http.Response r = xmlGet(BASE + "/xmlapi2/thing?stats=1&id=" + String.join(",", batch), token);
                if (r.code == 200) got = parseThings(r.body);
            } catch (IOException ignored) {
                // fall back below
            }
            if (got == null || got.isEmpty()) {
                got = new ArrayList<>();
                for (String id : batch) {
                    GameInfo g = fetchGameJson(id);
                    if (g != null) got.add(g);
                    sleep(400);
                }
            }
            out.addAll(got);
            if (i + 20 < ids.size()) sleep(2000);
        }
        return out;
    }

    private static List<GameInfo> parseThings(String xml) {
        List<GameInfo> out = new ArrayList<>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(xml));
            GameInfo g = null;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("item".equals(tag)) {
                        g = new GameInfo();
                        g.id = nz(p.getAttributeValue(null, "id"));
                        continue;
                    }
                    if (g == null) continue;
                    String value = p.getAttributeValue(null, "value");
                    switch (tag) {
                        case "thumbnail":
                            g.thumbnail = p.nextText().trim();
                            break;
                        case "image":
                            g.image = p.nextText().trim();
                            break;
                        case "description":
                            g.description = p.nextText().trim();
                            break;
                        case "name":
                            if (g.name.isEmpty() || "primary".equals(p.getAttributeValue(null, "type"))) g.name = nz(value);
                            break;
                        case "yearpublished":
                            g.year = nz(value);
                            break;
                        case "minplayers":
                            g.minPlayers = nz(value);
                            break;
                        case "maxplayers":
                            g.maxPlayers = nz(value);
                            break;
                        case "playingtime":
                            g.playTime = nz(value);
                            break;
                        case "average":
                            g.rating = nz(value);
                            break;
                        case "averageweight":
                            g.weight = nz(value);
                            break;
                        case "link":
                            String type = p.getAttributeValue(null, "type");
                            if ("boardgamecategory".equals(type)) g.categories.add(nz(value));
                            else if ("boardgamemechanic".equals(type)) g.mechanics.add(nz(value));
                            else if ("boardgamedesigner".equals(type)) g.designers.add(nz(value));
                            break;
                        default:
                            break;
                    }
                } else if (ev == XmlPullParser.END_TAG && "item".equals(p.getName()) && g != null) {
                    if (!g.id.isEmpty()) out.add(g);
                    g = null;
                }
            }
        } catch (Exception ignored) {
            // return what was parsed
        }
        return out;
    }

    /** The JSON the BGG website uses for a game page (no token needed). */
    static GameInfo fetchGameJson(String id) {
        try {
            Map<String, String> h = new HashMap<>();
            h.put("Accept", "application/json");
            Http.Response r = Http.request("GET",
                    "https://api.geekdo.com/api/geekitems?nosession=1&objecttype=thing&subtype=boardgame&objectid=" + id,
                    h, null);
            if (r.code != 200) return null;
            JSONObject item = new JSONObject(r.body).optJSONObject("item");
            if (item == null) return null;
            GameInfo g = new GameInfo();
            g.id = id;
            g.name = str(item, "name");
            g.year = str(item, "yearpublished");
            g.description = str(item, "description");
            g.minPlayers = str(item, "minplayers");
            g.maxPlayers = str(item, "maxplayers");
            g.playTime = str(item, "maxplaytime");
            g.image = str(item, "imageurl");
            JSONObject images = item.optJSONObject("images");
            if (images != null) {
                g.thumbnail = str(images, "square200");
                if (g.thumbnail.isEmpty()) g.thumbnail = str(images, "thumb");
                if (g.image.isEmpty()) g.image = str(images, "original");
            }
            JSONObject links = item.optJSONObject("links");
            if (links != null) {
                names(links.optJSONArray("boardgamecategory"), g.categories);
                names(links.optJSONArray("boardgamemechanic"), g.mechanics);
                names(links.optJSONArray("boardgamedesigner"), g.designers);
            }
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    private static void names(JSONArray a, List<String> into) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) {
                String n = str(o, "name");
                if (!n.isEmpty()) into.add(n);
            }
        }
    }

    // ---------- helpers ----------

    private static Http.Response xmlGet(String url, String token) throws IOException {
        Map<String, String> h = new HashMap<>();
        if (token != null && !token.isEmpty()) h.put("Authorization", "Bearer " + token);
        Http.Response r = Http.request("GET", url, h, null);
        if (r.code == 429 || r.code == 202) { // busy or queued: wait and try once more
            sleep(5000);
            r = Http.request("GET", url, h, null);
        }
        return r;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    static String gamePageUrl(String objectId) {
        return BASE + "/boardgame/" + objectId;
    }

    static String myPlaysUrl(String user) {
        try {
            return BASE + "/plays/bydate/user/" + URLEncoder.encode(user, StandardCharsets.UTF_8.name())
                    + "/subtype/boardgame";
        } catch (Exception e) {
            return BASE;
        }
    }
}

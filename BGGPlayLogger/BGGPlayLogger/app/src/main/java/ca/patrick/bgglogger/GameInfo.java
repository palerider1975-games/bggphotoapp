package ca.patrick.bgglogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Details of one game on BGG (cover, themes, players, etc.). */
final class GameInfo {
    String id = "", name = "", year = "", image = "", thumbnail = "", description = "";
    String minPlayers = "", maxPlayers = "", playTime = "", rating = "", weight = "";
    final List<String> categories = new ArrayList<>();
    final List<String> mechanics = new ArrayList<>();
    final List<String> designers = new ArrayList<>();

    boolean hasDetails() {
        return !image.isEmpty() || !thumbnail.isEmpty() || !categories.isEmpty();
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("name", name).put("year", year).put("image", image)
                    .put("thumbnail", thumbnail).put("description", description)
                    .put("minPlayers", minPlayers).put("maxPlayers", maxPlayers)
                    .put("playTime", playTime).put("rating", rating).put("weight", weight)
                    .put("categories", new JSONArray(categories))
                    .put("mechanics", new JSONArray(mechanics))
                    .put("designers", new JSONArray(designers));
        } catch (Exception ignored) {
            // values are plain strings; cannot fail
        }
        return o;
    }

    static GameInfo fromJson(JSONObject o) {
        GameInfo g = new GameInfo();
        g.id = o.optString("id");
        g.name = o.optString("name");
        g.year = o.optString("year");
        g.image = o.optString("image");
        g.thumbnail = o.optString("thumbnail");
        g.description = o.optString("description");
        g.minPlayers = o.optString("minPlayers");
        g.maxPlayers = o.optString("maxPlayers");
        g.playTime = o.optString("playTime");
        g.rating = o.optString("rating");
        g.weight = o.optString("weight");
        fill(g.categories, o.optJSONArray("categories"));
        fill(g.mechanics, o.optJSONArray("mechanics"));
        fill(g.designers, o.optJSONArray("designers"));
        return g;
    }

    private static void fill(List<String> list, JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) list.add(s);
        }
    }

    String coverUrl() {
        return !image.isEmpty() ? image : thumbnail;
    }

    String thumbUrl() {
        return !thumbnail.isEmpty() ? thumbnail : image;
    }
}

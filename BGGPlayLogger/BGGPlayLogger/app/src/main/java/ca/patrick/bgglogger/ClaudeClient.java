package ca.patrick.bgglogger;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Sends the photo to the Claude API and gets back the game and players as JSON. */
final class ClaudeClient {
    private static final String URL = "https://api.anthropic.com/v1/messages";

    private static final String PROMPT =
            "This photo shows a tabletop game (board game, card game, etc.) on a table, "
            + "during or at the end of a play.\n"
            + "1. Identify the game. Use the box, board, cards, components and any visible text. "
            + "Give the title exactly as it appears on BoardGameGeek (English title, base game; "
            + "list expansions separately if you see them).\n"
            + "2. Work out the players: count the player areas or colours. If names are visible "
            + "(score pad, name cards) use them; otherwise use the colour as the name (e.g. \"Blue\").\n"
            + "3. Scores: only give a score if you can actually read it (score track position, score pad, "
            + "counters). Otherwise use null. Never invent scores.\n"
            + "Reply with ONLY this JSON, no other text:\n"
            + "{\"game_name\": string, \"search_term\": string (short title to search BGG with), "
            + "\"year_published\": number or null, \"expansions\": [string], "
            + "\"confidence\": \"high\" | \"medium\" | \"low\", "
            + "\"players\": [{\"name\": string, \"color\": string, \"score\": number or null}], "
            + "\"notes\": string (one short sentence about what you saw or were unsure of)}";

    private ClaudeClient() {}

    static JSONObject analyze(String apiKey, String model, byte[] jpeg) throws IOException, JSONException {
        JSONObject image = new JSONObject()
                .put("type", "image")
                .put("source", new JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)));
        JSONObject text = new JSONObject().put("type", "text").put("text", PROMPT);
        JSONObject message = new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray().put(image).put(text));
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("max_tokens", 1500)
                .put("messages", new JSONArray().put(message));

        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");
        headers.put("content-type", "application/json");

        Http.Response r = Http.request("POST", URL, headers, body.toString());
        if (r.code != 200) {
            String msg = Http.snippet(r.body);
            try {
                msg = new JSONObject(r.body).getJSONObject("error").getString("message");
            } catch (JSONException ignored) {
                // keep the raw snippet
            }
            throw new IOException("Claude API (" + r.code + "): " + msg);
        }

        JSONArray content = new JSONObject(r.body).optJSONArray("content");
        StringBuilder sb = new StringBuilder();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject part = content.optJSONObject(i);
                if (part != null && "text".equals(part.optString("type"))) sb.append(part.optString("text"));
            }
        }
        String out = sb.toString();
        int start = out.indexOf('{');
        int end = out.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IOException("Claude did not return game data: " + Http.snippet(out));
        return new JSONObject(out.substring(start, end + 1));
    }
}

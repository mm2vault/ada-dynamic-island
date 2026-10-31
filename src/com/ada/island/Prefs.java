package com.ada.island;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("ada_prefs", Context.MODE_PRIVATE);
    }

    public String apiKey() {
        return sp.getString("api_key", "");
    }

    public void setApiKey(String v) {
        sp.edit().putString("api_key", v == null ? "" : v.trim()).apply();
    }

    public String provider() {
        return sp.getString("ai_provider", "openai");
    }

    public void setProvider(String v) {
        sp.edit().putString("ai_provider", v == null ? "openai" : v.trim()).apply();
    }

    public boolean isGemini() {
        return "gemini".equals(provider());
    }

    public String model() {
        String m = sp.getString("model", "");
        if (m == null || m.isEmpty()) return isGemini() ? "gemini-3.8-flash" : "gpt-4o-mini";
        return m;
    }

    public void setModel(String v) {
        sp.edit().putString("model", v == null ? "" : v.trim()).apply();
    }

    public String baseUrl() {
        String v = sp.getString("base_url", "");
        if (v == null || v.isEmpty()) {
            v = isGemini() ? "https://generativelanguage.googleapis.com/v1beta" : "https://api.openai.com/v1";
        }
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    public void setBaseUrl(String v) {
        sp.edit().putString("base_url", v == null ? "" : v.trim()).apply();
    }

    public long lastUpdateCheck() {
        return sp.getLong("last_update_check", 0L);
    }

    public void setLastUpdateCheck(long value) {
        sp.edit().putLong("last_update_check", value).apply();
    }

    public boolean updateAwaitingInstall() {
        return sp.getBoolean("update_awaiting_install", false);
    }

    public void setUpdateAwaitingInstall(boolean value) {
        sp.edit().putBoolean("update_awaiting_install", value).apply();
    }

    public boolean aiOn() {
        return sp.getBoolean("ai_on", true);
    }

    public void setAiOn(boolean v) {
        sp.edit().putBoolean("ai_on", v).apply();
    }

    public boolean islandOn() {
        return sp.getBoolean("island_on", false);
    }

    public void setIslandOn(boolean v) {
        sp.edit().putBoolean("island_on", v).apply();
    }

    public boolean proactive() {
        return sp.getBoolean("proactive", true);
    }

    public void setProactive(boolean v) {
        sp.edit().putBoolean("proactive", v).apply();
    }

    public int islandY() {
        return sp.getInt("island_y", -1);
    }

    public void setIslandY(int px) {
        sp.edit().putInt("island_y", px).apply();
    }

    public long lastProactive() {
        return sp.getLong("last_proactive", 0L);
    }

    public void setLastProactive(long v) {
        sp.edit().putLong("last_proactive", v).apply();
    }

    public int proactiveGapMin() {
        int g = sp.getInt("gap", 20);
        return g < 2 ? 20 : g;
    }

    public void setProactiveGapMin(int m) {
        sp.edit().putInt("gap", m).apply();
    }

    public boolean hasKey() {
        return apiKey().length() > 12;
    }
}

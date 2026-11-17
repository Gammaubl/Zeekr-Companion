package com.zeekr7x.tubedash

import android.content.Context

object Prefs {
    private const val FILE = "zeekr_7x_tubedash_prefs"

    // Output Modes: 0 = Center Pop-Up only, 1 = AR-HUD only, 2 = Dual (Center + HUD)
    fun getOutputMode(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("output_mode", 2)

    fun setOutputMode(ctx: Context, mode: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("output_mode", mode).apply()

    // Last used driving mode (for auto-resume on shifting to D gear)
    fun getLastUsedDrivingMode(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("last_used_driving_mode", 2)

    fun setLastUsedDrivingMode(ctx: Context, mode: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("last_used_driving_mode", mode).apply()

    // Independent targets: "YOUTUBE" or "MAPS"
    fun getCenterSource(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("center_source", "YOUTUBE") ?: "YOUTUBE"

    fun setCenterSource(ctx: Context, src: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("center_source", src).apply()

    fun getHudSource(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("hud_source", "MAPS") ?: "MAPS"

    fun setHudSource(ctx: Context, src: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("hud_source", src).apply()

    // Map Layer: "DEFAULT" (Vector Street) or "SATELLITE"
    fun getMapLayer(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("map_layer", "DEFAULT") ?: "DEFAULT"

    fun setMapLayer(ctx: Context, layer: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("map_layer", layer).apply()

    // Native Maps / microG integration toggle
    fun isNativeMapsEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("native_maps_enabled", false)

    fun setNativeMapsEnabled(ctx: Context, enabled: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("native_maps_enabled", enabled).apply()

    // Top-Left HUD Telemetry Card toggle
    fun isHudTelemetryCardEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("hud_telemetry_card", true)

    fun setHudTelemetryCardEnabled(ctx: Context, enabled: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("hud_telemetry_card", enabled).apply()

    // Auto PiP fallback
    fun isAutoPip(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("auto_pip", false)

    fun setAutoPip(ctx: Context, enabled: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("auto_pip", enabled).apply()

    // AR-HUD Calibration
    fun getHudDisplayId(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("hud_display_id", 2)

    fun setHudDisplayId(ctx: Context, id: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("hud_display_id", id).apply()

    fun getHudScale(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("hud_scale", 42)

    fun setHudScale(ctx: Context, scale: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("hud_scale", scale).apply()

    fun getHudOffsetX(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("hud_offset_x", 40)

    fun setHudOffsetX(ctx: Context, x: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("hud_offset_x", x).apply()

    fun getHudOffsetY(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("hud_offset_y", 30)

    fun setHudOffsetY(ctx: Context, y: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("hud_offset_y", y).apply()

    fun applyHudPreset(ctx: Context, presetName: String) {
        when (presetName) {
            "Corner" -> {
                setHudScale(ctx, 35)
                setHudOffsetX(ctx, 80)
                setHudOffsetY(ctx, 50)
            }
            "Banner" -> {
                setHudScale(ctx, 45)
                setHudOffsetX(ctx, 40)
                setHudOffsetY(ctx, 20)
            }
            "Wide" -> {
                setHudScale(ctx, 65)
                setHudOffsetX(ctx, 20)
                setHudOffsetY(ctx, 20)
            }
        }
    }

    fun getLastUrl(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("last_url", "https://www.youtube.com") ?: "https://www.youtube.com"

    fun setLastUrl(ctx: Context, url: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("last_url", url).apply()

    fun getLastMapUrl(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("last_map_url", "https://www.google.com/maps") ?: "https://www.google.com/maps"

    fun setLastMapUrl(ctx: Context, url: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("last_map_url", url).apply()
}

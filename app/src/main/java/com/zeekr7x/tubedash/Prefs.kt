package com.zeekr7x.tubedash

import android.content.Context

object Prefs {
    private const val FILE = "zeekr_7x_tubedash_prefs"

    // Output Modes (0 = Center Pop-Up, 1 = AR-HUD, 2 = Dual Center + HUD)
    fun getOutputMode(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("output_mode", 0)

    fun setOutputMode(ctx: Context, mode: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("output_mode", mode).apply()

    // Last used active driving mode (for auto-resume on shifting to D)
    fun getLastUsedDrivingMode(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("last_used_driving_mode", 0)

    fun setLastUsedDrivingMode(ctx: Context, mode: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("last_used_driving_mode", mode).apply()

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
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("hud_scale", 38) // % of HUD width

    fun setHudScale(ctx: Context, scale: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("hud_scale", scale).apply()

    fun getHudOffsetX(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("hud_offset_x", 60)

    fun setHudOffsetX(ctx: Context, x: Int) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("hud_offset_x", x).apply()

    fun getHudOffsetY(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("hud_offset_y", 40)

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
                setHudScale(ctx, 60)
                setHudOffsetX(ctx, 30)
                setHudOffsetY(ctx, 30)
            }
        }
    }

    fun getLastUrl(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("last_url", "https://www.youtube.com") ?: "https://www.youtube.com"

    fun setLastUrl(ctx: Context, url: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("last_url", url).apply()
}

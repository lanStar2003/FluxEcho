package com.fluxecho.city;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldInfo;

import com.fluxecho.logic.CityBuild;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.logic.WeatherLook;
import com.fluxecho.logic.WeatherStationShape;

/**
 * 天穹气象站 The Weather Station (0.16.0, CITY.md §4.12): the sky ring's module over the gate (S0, site 30), an
 * instrument hut on a floating platform with the weather mast behind it, four weather consoles round the mast and two
 * vane pylons at the back. Its units are the four weather consoles, one slot each (weather cores; in the structures
 * phase any item). Its shape is {@link WeatherStationShape}; once it forms the mast's pillars, conduit and seat give
 * way to the rendered turning vanes under a slowly churning cloud, and players pass through them; the pylons, of the
 * same pillars, keep their blocks ({@link #dissolvesAt}). TE id {@code fluxecho:weather_station}, core
 * {@code fluxecho:module_core} meta 14.
 * <p>
 * Its cloud thickens before the rain (0.16.0, {@code WeatherStationRender}), which the client cannot know: only the
 * server's world keeps how long until it rains. So the station tells its clients the world tick the rain is due
 * ({@link WeatherLook#rainAt}, a constant while the countdown runs), again only when that changes (a command, a night
 * slept through), in its description as {@code Wf}; {@link #clientRainAt} on the client, -1 while it rains or none is
 * known.
 */
public class TileWeatherStation extends TileCityModule {

    /** Its accent colour, drawn in light only (CITY.md §1.1). */
    public static final int COLOR = 0x9AD0FF;

    /** The world tick the rain is due as last sent ({@code Wf}), -1 for none; the client's copy. */
    private long sentRainAt = -1;
    public long clientRainAt = -1;

    @Override
    public String moduleKey() {
        return WeatherStationShape.KEY;
    }

    @Override
    public int moduleColor() {
        return COLOR;
    }

    @Override
    public CityBuild build() {
        return WeatherStationShape.BUILD;
    }

    @Override
    public UnitLayout layout() {
        return WeatherStationShape.UNITS;
    }

    @Override
    public int coreMetaOf() {
        return ModuleCores.WEATHER_STATION;
    }

    @Override
    protected String dissolves() {
        return WeatherStationShape.DISSOLVES;
    }

    @Override
    protected String passes() {
        return WeatherStationShape.PASSES;
    }

    /** Only the mast's column dissolves: the vane pylons' pillars and rings stay. */
    @Override
    protected boolean dissolvesAt(int x, int y, int z) {
        return WeatherStationShape.mast(x, y, z);
    }

    /** Every second: tells the clients when the rain is due when that changed (the countdown itself keeps it). */
    @Override
    protected void serverTick() {
        super.serverTick();
        if (worldObj.getTotalWorldTime() % 20 != 0) return;
        WorldInfo info = worldObj.getWorldInfo();
        long at = WeatherLook.rainAt(info.isRaining(), worldObj.getTotalWorldTime(), info.getRainTime());
        if (!WeatherLook.forecastChanged(sentRainAt, at)) return;
        sentRainAt = at;
        sync();
    }

    @Override
    protected void writeSync(NBTTagCompound t) {
        super.writeSync(t);
        t.setLong("Wf", sentRainAt);
    }

    @Override
    protected void readSync(NBTTagCompound t) {
        super.readSync(t);
        clientRainAt = t.hasKey("Wf") ? t.getLong("Wf") : -1;
    }
}

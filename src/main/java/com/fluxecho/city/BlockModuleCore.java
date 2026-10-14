package com.fluxecho.city;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Builder;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.nexus.BlockNexus;
import com.fluxecho.nexus.TileMultiblock;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * {@code fluxecho:module_core} (0.11.0): the controller of every city module, one block whose meta names the module
 * ({@link ModuleCores}: 0 the Eco Dome), part {@code Parts.MODULE_CORE + meta}. Like the library core it is a
 * {@link BlockNexus}: its front faces the nexus once commissioned, right-click opens the module's window for its team.
 * It is manifested at the nexus, never crafted, and the campus builder takes a finished one (part-only). While the
 * builder edits the world ({@link Builder#QUIET}) a broken core does not tell the multiblocks round it.
 * <p>
 * Icons per meta: {@code fluxecho:module_core/<module>/front}, {@code front_on}, {@code side}, {@code top}; a meta
 * without a module yet shows the Eco Dome's. 0.12.0 adds the Echo Dome Generator (meta 7) and the root's Tidal Well
 * (meta 8), 0.13.0 the four pavilions: the Arcane Court (meta 2), the Blood Covenant Altar (3), the Echo Hunting
 * Ground (4) and the Mana Fountain Array (5), 0.14.0 the gate's Resonance Gate (6) and the Dual-foil Vault (1),
 * 0.15.0 the outer diagonals' Sophon Array (9) and Holo Sand Table (10).
 */
public class BlockModuleCore extends BlockNexus {

    @SideOnly(Side.CLIENT)
    private IIcon[] front, frontOn, side, top;

    public BlockModuleCore() {
        super("module_core", "module_core");
    }

    @Override
    public TileEntity createNewTileEntity(World w, int meta) {
        if (meta == ModuleCores.ECO_DOME) return new TileEcoDome();
        if (meta == ModuleCores.DUAL_FOIL_VAULT) return new TileDualFoilVault();
        if (meta == ModuleCores.ARCANE_COURT) return new TileArcaneCourt();
        if (meta == ModuleCores.BLOOD_COVENANT) return new TileBloodCovenant();
        if (meta == ModuleCores.HUNTING_GROUND) return new TileHuntingGround();
        if (meta == ModuleCores.MANA_FOUNTAIN) return new TileManaFountain();
        if (meta == ModuleCores.RESONANCE_GATE) return new TileResonanceGate();
        if (meta == ModuleCores.DOME_GENERATOR) return new TileDomeGenerator();
        if (meta == ModuleCores.ROOT) return new TileRoot();
        if (meta == ModuleCores.SOPHON_ARRAY) return new TileSophonArray();
        if (meta == ModuleCores.HOLO_TABLE) return new TileHoloTable();
        // the sky ring's four (0.16.0)
        if (meta == ModuleCores.SKY_SAILS) return new TileSkySails();
        if (meta == ModuleCores.OBSERVATORY) return new TileObservatory();
        if (meta == ModuleCores.DROPLET_HANGAR) return new TileDropletHangar();
        if (meta == ModuleCores.WEATHER_STATION) return new TileWeatherStation();
        return null;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m : ModuleCores.built()) list.add(new ItemStack(item, 1, m));
    }

    @Override
    protected boolean quiet() {
        return Builder.QUIET;
    }

    @Override
    protected void dropsMore(TileEntity te, World w, int x, int y, int z) {
        if (w.isRemote || !(te instanceof TileCityModule c)) return;
        c.takeDown(w, x, y, z);
    }

    /** The meta clamped to one that has a module (and icons): the Eco Dome's for the reserved ones. */
    static int built(int meta) {
        return ModuleCores.built(meta) ? meta : ModuleCores.ECO_DOME;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        int n = ModuleCores.KEYS.length;
        front = new IIcon[n];
        frontOn = new IIcon[n];
        side = new IIcon[n];
        top = new IIcon[n];
        for (int m : ModuleCores.built()) {
            String p = FluxEcho.MODID + ":module_core/" + ModuleCores.KEYS[m] + "/";
            front[m] = r.registerIcon(p + "front");
            frontOn[m] = r.registerIcon(p + "front_on");
            side[m] = r.registerIcon(p + "side");
            top[m] = r.registerIcon(p + "top");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        int m = built(meta);
        return s == 3 ? front[m] : s <= 1 ? top[m] : side[m];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        int m = built(w.getBlockMetadata(x, y, z));
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TileMultiblock t) {
            if (s == t.front()
                .ordinal()) return t.formed() ? frontOn[m] : front[m];
            return s <= 1 ? top[m] : side[m];
        }
        return getIcon(s, m);
    }
}

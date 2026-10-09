package com.fluxecho.mixins.early;

import java.util.List;
import java.util.Set;

import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.fluxecho.gate.Zone;
import com.fluxecho.logic.FoldedZone;

/**
 * A chunk of the folded zone that was never made is made empty, already populated, without asking the world's
 * generator: no terrain, no ores, no mod's world generation. Chunks already made (on disk or loaded) load as usual,
 * and everywhere else nothing changes.
 */
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer {

    @Shadow
    private Set<Long> chunksToUnload;
    @Shadow
    public IChunkLoader currentChunkLoader;
    @Shadow
    public LongHashMap loadedChunkHashMap;
    @Shadow
    public List<Chunk> loadedChunks;
    @Shadow
    public WorldServer worldObj;

    @Inject(method = "originalLoadChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluxecho$foldedZone(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        if (!FoldedZone.containsChunk(x, z)) return;
        long k = ChunkCoordIntPair.chunkXZ2Int(x, z);
        if (loadedChunkHashMap.containsItem(k)) return;
        if (currentChunkLoader instanceof AnvilChunkLoader l && l.chunkExists(worldObj, x, z)) return;
        Chunk c = Zone.emptyChunk(worldObj, x, z);
        chunksToUnload.remove(k);
        loadedChunkHashMap.add(k, c);
        loadedChunks.add(c);
        c.onChunkLoad();
        IChunkProvider self = (IChunkProvider) (Object) this;
        c.populateChunk(self, self, x, z);
        cir.setReturnValue(c);
    }
}

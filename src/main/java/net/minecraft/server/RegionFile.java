package net.minecraft.server;

/**
 * This is only kept to placate CraftBukkit, see usage in
 * {@link RegionFileCache}
 *
 */
public abstract class RegionFile {

	public abstract boolean chunkExists(int chunkX, int chunkZ);
}

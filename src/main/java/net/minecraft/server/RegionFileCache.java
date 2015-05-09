package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.util.SafeEncoder;

import com.google.common.collect.Maps;

public class RegionFileCache {

	private static final String DEFAULT_REDIS_HOST = "localhost";

	/**
	 * Spigot made this public so we have to keep it, but it is only used (in
	 * {@link org.bukkit.craftbukkit.CraftServer} to close files when unloading
	 * a world.
	 */
	public static final Map<File, RegionFile> a = Maps.newHashMap();

	private static final Logger logger = LogManager.getLogger();

	private static final JedisPool jedisPool = new JedisPool(new JedisPoolConfig(), System.getProperty("redisHost", DEFAULT_REDIS_HOST));

	/**
	 * This is the only place where RegionFile is exposed, but it's only used in
	 * CraftBukkit's variant of
	 * {@link net.minecraft.server.ChunkRegionLoader#chunkExists(World, int, int)}
	 * and there, only {@link RegionFile#chunkExists(File, int, int)} is called
	 * (with redundant arguments).
	 * 
	 * So we treat it as an elaborate way of asking whether a chunk exists.
	 * 
	 * @param worldDir
	 *            path to a directory that contained a 'region' sub-directory in
	 *            the original implementation
	 * @param chunkX
	 * @param chunkZ
	 * @return a dummy RegionFile that routes back here
	 */
	public static synchronized RegionFile a(File worldDir, int chunkX, int chunkZ) {
		return new RegionFile() {
			@Override
			public boolean chunkExists(int chunkX_lowBits, int chunkZ_lowBits) {
				// Based on the code of n.m.s.ChunkRegionLoader.chunkExists() we
				// could just ignore chunkX_lowBits and chunkZ_lowBits but eh.
				return RegionFileCache.chunkExists(worldDir, chunkX | (chunkX_lowBits & 31), chunkZ | (chunkZ_lowBits & 31));
			}
		};
	}

	/**
	 * This used to close all open files when stopping the server.
	 */
	public static synchronized void a() {
	}

	/**
	 * Minecraft calls {@link #c(File, int, int)} but really means {@link #openChunkForReading(File, int, int)}.
	 */
	public static DataInputStream c(File worldDir, int chunkX, int chunkZ) {
		return openChunkForReading(worldDir, chunkX, chunkZ);
	}
	
	/**
	 * Minecraft calls {@link #d(File, int, int)} but really means {@link #openChunkForWriting(File, int, int)}.
	 */
	public static DataOutputStream d(File worldDir, int chunkX, int chunkZ) {
		return openChunkForWriting(worldDir, chunkX, chunkZ);
	}

	/**
	 * This is where chunk read requests end up.
	 * 
	 * The original {@link RegionFileCache} implementation always returns a
	 * {@link DataInputStream} backed by a full-loaded byte array, and the
	 * server seems to rely on that : it will occasionally open the same chunk
	 * for reading and writing simultaneously.
	 * 
	 * @param worldDir
	 *            path to a directory that contained a 'region' sub-directory in
	 *            the original implementation
	 * @param chunkX
	 * @param chunkZ
	 * @return a {@link DataInputStream} over the chunk's bytes
	 */
	public static DataInputStream openChunkForReading(File worldDir, int chunkX, int chunkZ) {
		logger.debug("reading {} at {},{}", worldDir.getName(), chunkX, chunkZ);

		String key = chunkKey(worldDir, chunkX, chunkZ);

		byte[] bytes = redisGet(key);

		if (bytes == null)
			return null;

		logger.debug("{}: read {} bytes", key, bytes.length);

		return new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)));
	}

	/**
	 * This is where chunk write requests end up.
	 * 
	 * The original {@link RegionFileCache} implementation always waits until
	 * the {@link DataOutputStream} is closed before writing anything to disk,
	 * and the server seems to rely on that : it will occasionally open the same
	 * chunk for reading and writing simultaneously.
	 * 
	 * @param worldDir
	 *            path to a directory that contained a 'region' sub-directory in
	 *            the original implementation
	 * @param chunkX
	 * @param chunkZ
	 * @return a {@link DataOutputStream} to the chunk's bytes
	 */
	public static DataOutputStream openChunkForWriting(File worldDir, int chunkX, int chunkZ) {
		logger.debug("writing {} at {},{}", worldDir.getName(), chunkX, chunkZ);

		final String key = chunkKey(worldDir, chunkX, chunkZ);

		return new DataOutputStream(new DeflaterOutputStream(new StoreOnCloseOutputStream() {
			@Override
			protected void store(byte[] buf) {
				redisSet(key, buf);
				logger.debug("{}: wrote {} bytes", key, buf.length);
			}

		}));
	}

	/**
	 * Jedis requires us to provide the key as a byte array if we want to give
	 * the value as a byte array. We just ask Jedis to encode the key as it would
	 * for a {@link Jedis#get(String)} call (as it happens, using UTF-8).
	 * 
	 * @param key
	 * @return key as a byte array
	 */
	private static byte[] keyBytes(String key) {
		return SafeEncoder.encode(key);
	}

	private static byte[] redisGet(String key) {
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.get(keyBytes(key));
		}
	}

	private static void redisSet(String key, byte[] buf) {
		try (Jedis jedis = jedisPool.getResource()) {
			String response = jedis.set(keyBytes(key), buf);
			if (!"OK".equals(response)) {
				throw new RuntimeException("Unexpected Redis response: " + response);
			}
		}
	}

	private static boolean redisExists(String key) {
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.exists(keyBytes(key));
		}
	}

	private static boolean chunkExists(File worldDir, int chunkX, int chunkZ) {
		return redisExists(chunkKey(worldDir, chunkX, chunkZ));
	}

	/**
	 * This is where the mapping from (directory, x, z) triplets to Redis keys
	 * is established.
	 * 
	 * @param worldDir
	 *            path to a directory that contained a 'region' sub-directory in
	 *            the original implementation
	 * @param chunkX
	 * @param chunkZ
	 * @return a {@link String} that uniquely identifies a chunk.
	 */
	private static String chunkKey(File worldDir, int chunkX, int chunkZ) {
		return String.format("%s:%d_%d.chunk", worldDir.getName(), chunkX, chunkZ);
	}

	/**
	 * This class provides a convenient way to store all bytes written to it in
	 * one go upon stream closure.
	 * 
	 * It is just a slightly more generic version of the
	 * {@link net.minecraft.server.RegionFile#ChunkBuffer} class in the original
	 * implementation.
	 *
	 */
	private static abstract class StoreOnCloseOutputStream extends ByteArrayOutputStream {
		public StoreOnCloseOutputStream() {
			super(8096);
		}

		public void close() {
			this.store(this.toByteArray());
		}

		protected abstract void store(byte[] buf);
	}
}

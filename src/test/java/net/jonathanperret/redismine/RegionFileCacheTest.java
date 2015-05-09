package net.jonathanperret.redismine;

import static org.junit.Assert.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import net.minecraft.server.RegionFileCache;

import org.junit.Test;

/**
 * More of an integration test since it requires a running Redis server.
 */
public class RegionFileCacheTest {

	private static final String WORLD_PATH = "/testWorld";
	private static final int X = 1234;
	private static final int Z = 5678;
	private static final String CHUNK_CONTENT = "hello";

	@Test
	public void roundTrip() throws Exception {
		File worldDir = new File(WORLD_PATH);
		try (DataOutputStream outputStream = RegionFileCache.openChunkForWriting(worldDir, X, Z)) {
			outputStream.writeUTF(CHUNK_CONTENT);
		}
		try (DataInputStream inputStream = RegionFileCache.openChunkForReading(worldDir, X, Z)) {
			String readBack = inputStream.readUTF();
			assertEquals(CHUNK_CONTENT, readBack);
		}
	}

}

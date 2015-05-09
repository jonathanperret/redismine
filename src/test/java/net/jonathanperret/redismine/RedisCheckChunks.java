package net.jonathanperret.redismine;

import static com.google.common.base.Charsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Set;
import java.util.zip.InflaterInputStream;

import net.minecraft.server.NBTCompressedStreamTools;
import net.minecraft.server.NBTTagCompound;

import org.junit.Test;

import redis.clients.jedis.Jedis;

public class RedisCheckChunks {

	/**
	 * Not really a test.
	 */
	@Test
	public void checkStoredChunks() throws Exception {
		Jedis jedis = new Jedis("localhost");

		Set<String> keys = jedis.keys("world:*");

		for (String key : keys) {
			byte[] bytes = jedis.get(key.getBytes(UTF_8));
			DataInputStream dataStream = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)));

			try {
				NBTTagCompound a = NBTCompressedStreamTools.a(dataStream);
			} catch (Exception e) {
				System.out.println("bad chunk at " + key + " (" + bytes.length + " bytes) : " + e);
			}
		}
	}

}

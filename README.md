A Thursday hack to store Minecraft
[chunks](http://minecraft.gamepedia.com/Chunks) in [Redis](http://redis.io/) instead
of using the [Anvil](https://web.archive.org/web/20180929232134/https://mojang.com/2012/02/new-minecraft-map-format-anvil/) file
format.

This is based on [Spigot](http://spigotmc.org/) 1.8.3, a mod-friendly "fork" of the
official Minecraft server.

To run this code, you will first need to obtain Spigot, which can only be legally
distributed in source form, so they have provided a very handy tool that sets everything
up for you: http://www.spigotmc.org/threads/bukkit-craftbukkit-spigot-1-8-3.53745/ .

Once the Spigot JAR is in your local Maven repository, it should be as simple
as building this project:

```bash
$ mvn verify
```

Make sure you have a Redis server running:

```bash
$ echo 'save 300 1' > redis.conf
$ redis-server redis.conf
```

You can now start a Minecraft server, preferably in an empty directory:

```bash
$ mkdir minecraft-data
$ cd minecraft-data
$ java -jar /path/to/redismine/target/redismine-0.0.1-SNAPSHOT-shaded.jar
Loading libraries, please wait...
…
```

On the first launch, you will need to agree to the Minecraft EULA and restart
the server. Just follow the instructions.

After a while, the Minecraft server will start saving chunks to Redis. You can
see those by asking Redis:

```bash
$ redis-cli keys '*'
world:0_0.chunk
world:0_1.chunk
…
```

Control freaks will be happier monitoring writes to Redis as they happen:

```bash
$ redis-cli config set notify-keyspace-events EA && redis-cli --csv psubscribe '__key*__:*'
```


You can also try breaking a few blocks, stopping the Minecraft server and restarting
it to see that your changes persisted. But of course, no `.mca` files were created.

No guarantees whatsoever are given as to the suitability of this code for any
purpose (other than educational, maybe).

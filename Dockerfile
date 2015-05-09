FROM java:8

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y wget git \
  && mkdir /tmp/spigot && cd /tmp/spigot \
  && wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar \
  && java -jar BuildTools.jar --skip-compile \
  && cd Spigot && sh ../apache-maven-*/bin/mvn install \
  && mkdir -p /opt && mv /tmp/spigot/apache-maven-* /opt \
  && rm -rf /tmp/spigot

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y redis-server

ADD . /src

RUN cd /src && (redis-server & sh /opt/apache-maven-*/bin/mvn package)

RUN mkdir /data && echo 'eula=true' > /data/eula.txt && echo 'save 300 1' > /data/redis.conf

VOLUME /data

WORKDIR /data

EXPOSE 25565

CMD ["bash", "-c", "redis-server ./redis.conf & java -jar /src/target/redismine-0.0.1-SNAPSHOT-shaded.jar; kill -INT -1 & wait"]

package oti.twin;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {
  static Behavior<Void> create() {
    return Behaviors.setup(
        context -> {
          return Behaviors.receive(Void.class)
              .onSignal(Terminated.class, signal -> Behaviors.stopped())
              .build();
        }
    );
  }

  public static void main(String[] args) {
    ActorSystem<?> actorSystem = ActorSystem.create(Main.create(), "OTI-Twin");
    ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
    startClusterBootstrap(actorSystem);
    startHttpServer(actorSystem);

    clusterSharding.init(
        Entity.of(
            Device.entityTypeKey,
            entityContext ->
                Device.create(entityContext.getEntityId(), clusterSharding)
        )
    );
  }

  private static void startClusterBootstrap(ActorSystem<?> actorSystem) {
    AkkaManagement.get(actorSystem.classicSystem()).start();
    ClusterBootstrap.get(actorSystem.classicSystem()).start();
  }

  static void startHttpServer(ActorSystem<?> actorSystem) {
    try {
      String host = InetAddress.getLocalHost().getHostName();
      int port = actorSystem.settings().config().getInt("oti.twin.http.server.port");
      HttpServer.start(host, port, actorSystem);
    } catch (UnknownHostException e) {
      actorSystem.log().error("Http server start failure.", e);
    }
  }
}

package com.xjeffrose.chicago;

import java.nio.charset.Charset;
import java.net.InetSocketAddress;
import java.util.List;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkClient {
  private static final Logger log = LoggerFactory.getLogger(ZkClient.class.getName());

  private LeaderSelector leaderSelector;
  private final ChiLeaderSelectorListener leaderListener = new ChiLeaderSelectorListener();
  private CuratorFramework client;
  private String connectionString;

  public ZkClient(CuratorFramework client) {
    this.client = client;
    this.connectionString = client.getZookeeperClient().getCurrentConnectionString();
  }

  public ZkClient(String serverSet) {
    connectionString = serverSet;
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
    client = CuratorFrameworkFactory.newClient(serverSet, retryPolicy);
  }

  public void rebuild() {
    client.close();
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 4);
    client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
  }

  public void register(String NODE_LIST_PATH, ChiConfig config) {
    try {
      InetSocketAddress address = new InetSocketAddress(config.getDBBindIP(), config.getDBPort());
      client
        .create()
        .creatingParentsIfNeeded()
        .withMode(CreateMode.EPHEMERAL)
        .forPath(
          NODE_LIST_PATH + "/" + address.getAddress().getHostAddress() + ":" + address.getPort(),
          ConfigSerializer.serialize(config).getBytes());
    } catch (Exception e) {
      log.error("Error registering Server", e);
      throw new RuntimeException(e);
    }
  }

  public void electLeader(String ELECTION_PATH) {
    leaderSelector = new LeaderSelector(client, ELECTION_PATH, leaderListener);

    leaderSelector.autoRequeue();
    leaderSelector.start();

  }

  public void start() throws InterruptedException {
    client.start();
    client.blockUntilConnected();
  }

  public void stop() throws Exception {
    leaderListener.relinquish();
    if (leaderSelector != null) {
      leaderSelector.close();
    }
    client.close();
  }

  public void set(String path, String data) {
    try {
      client.create().creatingParentsIfNeeded().forPath(path, data.getBytes());
    } catch (Exception e) {
      //throw new RuntimeException(e);
    }
  }

  public String get(String path) {
    try {
      return new String(client.getData().forPath(path), Charset.forName("UTF-8"));
    } catch (Exception e) {
      //TODO: I need to deal with the error better
//      log.severe("No node for for: " + path);
//      throw new RuntimeException(e);
    }
    return null;
  }

  public void set(String path, String data, boolean compress) {
    try {
      client.create().compressed().creatingParentsIfNeeded().forPath(path, data.getBytes());
    } catch (Exception e) {
      //throw new RuntimeException(e);
    }
  }

  public String get(String path, boolean compress) {
    try {
      return new String(client.getData().decompressed().forPath(path), Charset.forName("UTF-8"));
    } catch (Exception e) {
      //throw new RuntimeException(e);
    }
    return null;
  }

  public List<String> list(String path) {
    try {
      return client.getChildren().forPath(path);
    } catch (Exception e) {
      //throw new RuntimeException(e);
    }
    return null;
  }

  public List<String> getChildren(String path) {
    try {
      return client.getChildren().forPath(path);
    } catch (Exception e) {
      //TODO: I need to deal with the error better
//      log.severe("No node for for: " + path);
//      throw new RuntimeException(e);
    }
    return null;
  }

  /* package access only */
  public CuratorFramework getClient() {
    return client;
  }

  public String getConnectionString() {
    return connectionString;
  }
}

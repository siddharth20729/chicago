package com.xjeffrose.chicago.client;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RendezvousHash<N> {
  private static final Logger log = LoggerFactory.getLogger(RendezvousHash.class.getName());

  private final HashFunction hasher;
  private final Funnel<N> nodeFunnel;
    private final int quorum;

  private ConcurrentSkipListSet<N> nodeList;

    public RendezvousHash(Funnel<N> nodeFunnel, Collection<N> init, int quorum) {
    this.hasher = Hashing.murmur3_128();
    this.nodeFunnel = nodeFunnel;
    this.nodeList = new ConcurrentSkipListSet<>(init);
    this.quorum = quorum;
  }

  public boolean remove(N node) {
    return nodeList.remove(node);
  }

  public boolean add(N node) {
    return nodeList.add(node);
  }

  public List<N> get(byte[] key) {
    while (nodeList.size() < quorum) {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Map<Long, N> hashMap = new ConcurrentHashMap<>();
    List<N> _nodeList = new ArrayList<>();

      nodeList.stream()
          .filter(xs -> !_nodeList.contains(xs))
          .forEach(xs -> {
            hashMap.put(hasher.newHasher()
                .putBytes(key)
                .putObject(xs, nodeFunnel)
                .hash().asLong(), xs);

    });

    for (int i = 0; i < quorum; i++) {
      _nodeList.add(hashMap.remove(hashMap.keySet().stream().max(Long::compare).orElse(null)));
    }

    return _nodeList;
  }

  public List<N> this_is_why_i_pay_chris(byte[] key) {
    Map<Long, N> sortedMap = new TreeMap<>(Comparator.reverseOrder());
    nodeList.stream().forEach(xs -> {
      sortedMap.put(hasher.newHasher().putBytes(key).putObject(xs, nodeFunnel).hash().asLong(), xs);
    });

    List<N> first_three = new ArrayList<>();
    int count = 0;
    for(N node : sortedMap.values()) {
      first_three.add(node);
      count++;
      if (count == 3) {
        break;
      }
    }
    return first_three;
  }

  public void refresh(List<N> list) {
    nodeList = new ConcurrentSkipListSet<>(list);
  }
}

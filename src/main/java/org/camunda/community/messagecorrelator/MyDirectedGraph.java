package org.camunda.community.messagecorrelator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import space.earlygrey.simplegraphs.Connection;
import space.earlygrey.simplegraphs.DirectedGraph;

public class MyDirectedGraph<V> extends DirectedGraph<V> {

  private Map<V, Set<V>> successors = new HashMap<>();
  private Map<V, Set<V>> predecessors = new HashMap<>();

  public boolean removeVertexFromPath(V vertex) {
    predecessors
        .getOrDefault(vertex, Set.of())
        .forEach(
            previousVertex ->
                successors
                    .getOrDefault(vertex, Set.of())
                    .forEach(nextVertex -> addEdge(previousVertex, nextVertex)));
    return removeVertex(vertex);
  }

  @Override
  public boolean removeVertex(V vertex) {
    predecessors
        .getOrDefault(vertex, Set.of())
        .forEach(
            previousVertex ->
                successors.computeIfPresent(
                    previousVertex,
                    (key, nextVertex) -> {
                      nextVertex.remove(vertex);
                      return nextVertex;
                    }));
    successors
        .getOrDefault(vertex, Set.of())
        .forEach(
            nextVertex ->
                predecessors.computeIfPresent(
                    nextVertex,
                    (key, previousVertex) -> {
                      previousVertex.remove(vertex);
                      return previousVertex;
                    }));
    successors.remove(vertex);
    predecessors.remove(vertex);
    return super.removeVertex(vertex);
  }

  @Override
  public Connection<V> addEdge(V source, V target) {
    successors.computeIfAbsent(source, v -> new HashSet<V>()).add(target);
    predecessors.computeIfAbsent(target, v -> new HashSet<V>()).add(source);
    return super.addEdge(source, target);
  }
}

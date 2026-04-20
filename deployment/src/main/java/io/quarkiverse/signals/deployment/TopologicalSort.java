package io.quarkiverse.signals.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Kahn's algorithm topological sort with alphabetical tiebreaker for deterministic ordering.
 */
class TopologicalSort {

    /**
     * @param allIds all component identifiers
     * @param beforeEdges map of id to the list of ids it must come before
     * @param afterEdges map of id to the list of ids it must come after
     * @param componentTypeName the name of the component type, used in error messages
     * @return the ordered list of identifiers
     * @throws IllegalStateException if a referenced identifier does not exist or a cycle is detected
     */
    static List<String> sort(Set<String> allIds, Map<String, List<String>> beforeEdges,
            Map<String, List<String>> afterEdges, String componentTypeName) {
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : allIds) {
            graph.put(id, new HashSet<>());
            inDegree.put(id, 0);
        }

        // "A before B" means edge A -> B
        for (Map.Entry<String, List<String>> entry : beforeEdges.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                if (!allIds.contains(to)) {
                    throw new IllegalStateException(
                            componentTypeName + " '" + from + "' declares @ComponentOrder(before = \"" + to
                                    + "\") but no " + componentTypeName + " with @Identifier(\"" + to + "\") exists");
                }
                if (graph.get(from).add(to)) {
                    inDegree.merge(to, 1, Integer::sum);
                }
            }
        }

        // "A after B" means edge B -> A
        for (Map.Entry<String, List<String>> entry : afterEdges.entrySet()) {
            String to = entry.getKey();
            for (String from : entry.getValue()) {
                if (!allIds.contains(from)) {
                    throw new IllegalStateException(
                            componentTypeName + " '" + to + "' declares @ComponentOrder(after = \"" + from
                                    + "\") but no " + componentTypeName + " with @Identifier(\"" + from + "\") exists");
                }
                if (graph.get(from).add(to)) {
                    inDegree.merge(to, 1, Integer::sum);
                }
            }
        }

        PriorityQueue<String> queue = new PriorityQueue<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String neighbor : graph.get(node)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != allIds.size()) {
            Set<String> remaining = new HashSet<>(allIds);
            remaining.removeAll(sorted);
            throw new IllegalStateException(
                    "Cycle detected in @ComponentOrder declarations involving " + componentTypeName + "s: " + remaining);
        }

        return sorted;
    }

}

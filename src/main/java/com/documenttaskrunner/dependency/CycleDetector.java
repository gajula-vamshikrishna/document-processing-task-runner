package com.documenttaskrunner.dependency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CycleDetector {

    public List<String> detectCycle(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<String> path = new ArrayList<>();
        List<String> cycle = new ArrayList<>();

        for (String node : graph.keySet()) {
            if (dfs(node, graph, visited, recursionStack, path, cycle)) {
                return cycle;
            }
        }
        return cycle;
    }

    private boolean dfs(String node,
                        Map<String, List<String>> graph,
                        Set<String> visited,
                        Set<String> recursionStack,
                        List<String> path,
                        List<String> cycle) {
        visited.add(node);
        recursionStack.add(node);
        path.add(node);

        for (String neighbor : graph.getOrDefault(node, List.of())) {
            if (!graph.containsKey(neighbor)) {
                continue;
            }
            if (!visited.contains(neighbor)) {
                if (dfs(neighbor, graph, visited, recursionStack, path, cycle)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                int index = path.indexOf(neighbor);
                List<String> segment = new ArrayList<>();
                for (int i = index; i < path.size(); i++) {
                    segment.add(path.get(i));
                }
                segment.add(neighbor);
                cycle.clear();
                cycle.addAll(segment);
                return true;
            }
        }

        path.remove(path.size() - 1);
        recursionStack.remove(node);
        return false;
    }
}

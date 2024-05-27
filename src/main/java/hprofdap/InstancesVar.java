
package hprofdap;

import java.util.List;

import org.netbeans.lib.profiler.heap.Instance;

public record InstancesVar(List<Instance> instances) implements Var {
}

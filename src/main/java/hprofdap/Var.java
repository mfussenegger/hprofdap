
package hprofdap;

import java.util.List;

import org.netbeans.lib.profiler.heap.Instance;

public record Var(int ref, Either<Instance, List<Instance>> object) {
}

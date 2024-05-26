package hprofdap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.netbeans.lib.profiler.heap.Instance;

public final class InstanceCache {

    private final AtomicInteger instanceIds = new AtomicInteger(0);
    private final Map<Integer, Var> vars = new ConcurrentHashMap<>();

    public InstanceCache() {
    }

    private int put(Either<Instance, List<Instance>> either) {
        int id = instanceIds.incrementAndGet();
        vars.put(id, new Var(id, either));
        return id;
    }

    public int put(Instance instance) {
        return put(Either.left(instance));
    }

    public int put(List<Instance> instances) {
        return put(Either.right(instances));
    }

    public Var get(int ref) {
        Var var = vars.get(ref);
        if (var == null) {
            throw new IllegalArgumentException("Invalid variable reference: " + ref);
        }
        return var;
    }

    public void clear() {
        instanceIds.set(0);
        vars.clear();
    }
}

package hprofdap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.netbeans.lib.profiler.heap.Instance;

public final class InstanceCache {

    private final AtomicInteger instanceIds = new AtomicInteger(0);
    private final Map<Integer, Var> vars = new HashMap<>();

    public InstanceCache() {
    }

    public int put(Instance instance) {
        int id = instanceIds.incrementAndGet();
        vars.put(id, new Var(id, Either.left(instance)));
        return id;
    }

    public int put(List<Instance> instances) {
        int id = instanceIds.incrementAndGet();
        vars.put(id, new Var(id, Either.right(instances)));
        return id;
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

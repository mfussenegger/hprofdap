package hprofdap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InstanceCache {

    private final AtomicInteger instanceIds = new AtomicInteger(0);
    private final Map<Integer, Var> vars = new ConcurrentHashMap<>();

    public InstanceCache() {
    }

    public int put(Var var) {
        int id = instanceIds.incrementAndGet();
        vars.put(id, var);
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

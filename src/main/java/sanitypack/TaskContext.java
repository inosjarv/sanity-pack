package sanitypack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The shared "blackboard" that flows through every step of a Task.
 * Each step reads what it needs and writes what it produces, so the
 * output of one API call becomes available to all later calls.
 *
 * Values are stored against typed Keys so you don't cast everywhere
 * and you catch wiring mistakes early. Declare keys as constants:
 *
 *   static final TaskContext.Key<String> TOKEN = TaskContext.Key.of("token");
 */
public final class TaskContext {

    public static final class Key<T> {
        private final String name;
        private Key(String name) { this.name = name; }
        public static <T> Key<T> of(String name) { return new Key<>(name); }
        public String name() { return name; }
        @Override public String toString() { return name; }
    }

    private final Map<Key<?>, Object> values = new HashMap<>();

    public <T> void put(Key<T> key, T value) {
        values.put(key, value);
    }

    /** Required read. Fails loudly if the value was never set. */
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        Object v = values.get(key);
        if (v == null) {
            throw new IllegalStateException(
                "Missing value for key '" + key + "'. "
                + "Did an earlier step forget to set it (or get skipped)?");
        }
        return (T) v;
    }

    /** Optional read. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(Key<T> key) {
        return Optional.ofNullable((T) values.get(key));
    }

    public boolean has(Key<?> key) {
        return values.containsKey(key);
    }
}

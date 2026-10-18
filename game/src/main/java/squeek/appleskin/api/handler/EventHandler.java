package squeek.appleskin.api.handler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Listener for an AppleSkin event. Replaces Fabric API's Event/EventFactory. */
public interface EventHandler<E> {
    void interact(E event);

    /** Minimal array-backed event bus (no Fabric API dependency). */
    final class Event<H extends EventHandler<?>> {
        private final List<H> listeners = new CopyOnWriteArrayList<>();

        public void register(H listener) {
            this.listeners.add(listener);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void fire(Object event) {
            for (H l : this.listeners) {
                ((EventHandler) l).interact(event);
            }
        }
    }

    static <T> Event<EventHandler<T>> createArrayBacked() {
        return new Event<>();
    }
}

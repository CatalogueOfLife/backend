package life.catalogue.event;

import life.catalogue.api.event.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches events to the registered listeners, each in isolation: a failing listener never keeps an event from
 * the others.
 */
abstract class AbstractEventBroker implements EventBroker {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractEventBroker.class);
  private static final int RECENT_SIZE = 100;
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final Deque<String> recent = new ArrayDeque<>(RECENT_SIZE);

  @Override
  public void register(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public List<String> recent() {
    synchronized (recent) {
      return new ArrayList<>(recent);
    }
  }

  private void remember(Object event) {
    synchronized (recent) {
      if (recent.size() >= RECENT_SIZE) {
        recent.removeFirst();
      }
      recent.addLast(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS) + " " + event);
    }
  }

  /**
   * Tells every listener to drop its caches, because events may have been missed.
   */
  protected void flushAll(String reason) {
    LOG.warn("Flush all caches: {}", reason);
    remember("flush caches: " + reason);
    for (Listener l : listeners) {
      try {
        l.flushCaches();
      } catch (Throwable e) {
        // an Error too: it would end the single thread listening for events, leaving every cache of the app stale
        LOG.error("Failed to flush the caches of {}", l, e);
      }
    }
  }

  protected void dispatch(Event event) {
    LOG.debug("Broker event {}", event);
    remember(event);
    switch (event) {
      case FlushCaches e -> flushAll("requested");
      case DatasetChanged e -> each(DatasetListener.class, l -> l.datasetChanged(e), e);
      case DatasetDataChanged e -> each(DatasetListener.class, l -> l.datasetDataChanged(e), e);
      case DatasetLogoChanged e -> each(DatasetListener.class, l -> l.datasetLogoChanged(e), e);
      case UserChanged e -> each(UserListener.class, l -> l.userChanged(e), e);
      case UserPermissionChanged e -> each(UserListener.class, l -> l.userPermissionChanged(e), e);
      default -> LOG.error("Unknown event type: {}", event.getClass().getSimpleName());
    }
  }

  private <L extends Listener> void each(Class<L> type, Consumer<L> action, Event event) {
    for (Listener l : listeners) {
      if (type.isInstance(l)) {
        try {
          action.accept(type.cast(l));
        } catch (Throwable e) {
          // an Error too: it would end the single thread listening for events, leaving every cache of the app stale
          LOG.error("Failed to broker event {} to {}", event, l.getClass().getSimpleName(), e);
        }
      }
    }
  }
}

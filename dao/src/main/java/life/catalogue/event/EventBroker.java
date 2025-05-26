package life.catalogue.event;

import life.catalogue.api.event.*;
import life.catalogue.common.Managed;
import life.catalogue.concurrent.ExecutorUtils;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

public class EventBroker implements Managed {
  private final BrokerConfig cfg;

  private final @NotNull SingleChronicleQueue queue;
  private final Publisher publisher;
  private final List<Listener> listeners = new ArrayList<>();
  private Thread polling;

  public EventBroker(BrokerConfig cfg) {
    this.cfg = cfg;
    this.queue = SingleChronicleQueueBuilder
      .single(cfg.queueDir)
      .rollCycle(RollCycles.FAST_DAILY)
      .build();
    this.publisher = new Publisher();
  }

  public void register(Listener listener) {
    if (polling != null) {
      throw new IllegalStateException("EventBroker is already running");
    }
    listeners.add(listener);
  }

  public void dumpQueue(Writer writer) {
    queue.dump(writer, -1, Long.MAX_VALUE);
  }

  public void dumpQueue() {
    System.out.println("\n--- DUMP ---");
    System.out.println(queue.dump());
    System.out.println("--- END ---\n");
  }

  public Publisher publish() {
    return publisher;
  }

  @Override
  public void stop() {
    if (polling != null) {
      polling.interrupt();
      queue.close();
    }
  }

  @Override
  public boolean hasStarted() {
    return polling != null;
  }

  @Override
  public void start() {
    if (polling == null) {
      var arr = listeners.toArray(new Listener[0]);
      polling = ExecutorUtils.runInNewThread(new Polling(arr), "event-broker-polling");
    }
  }

  public class Publisher implements DatasetListener, UserListener, SectorListener, DoiListener {
    private final DatasetListener dWriter;
    private final SectorListener sWriter;
    private final UserListener uWriter;
    private final DoiListener doiWriter;

    public Publisher() {
      var appender = queue.acquireAppender();
      this.dWriter = appender.methodWriter(DatasetListener.class);
      this.uWriter = appender.methodWriter(UserListener.class);
      this.sWriter = appender.methodWriter(SectorListener.class);
      this.doiWriter = appender.methodWriter(DoiListener.class);
    }

    @Override
    public void datasetChanged(DatasetChanged event) {
      dWriter.datasetChanged(event);
    }

    @Override
    public void sectorDeleted(DeleteSector event) {
      sWriter.sectorDeleted(event);
    }

    @Override
    public void userChanged(UserChanged event) {
      uWriter.userChanged(event);
    }

    @Override
    public void userPermissionChanged(UserPermissionChanged event) {
      uWriter.userPermissionChanged(event);
    }

    @Override
    public void datasetDataChanged(DatasetDataChanged event) {
      dWriter.datasetDataChanged(event);
    }

    @Override
    public void datasetLogoChanged(DatasetLogoChanged event) {
      dWriter.datasetLogoChanged(event);
    }

    @Override
    public void doiChanged(DoiChange event) {
      doiWriter.doiChanged(event);
    }
  }

  private class Polling implements Runnable {
    private final Listener[] listeners;

    public Polling(Listener[] listeners) {
      this.listeners = listeners;
    }

    @Override
    public void run() {
      ExcerptTailer tailer = queue.createTailer(cfg.name).toEnd(); // we wind to the end to only consume new messages
      MethodReader reader = tailer.methodReader((Object[]) listeners);
      // Continuously read messages and distribute them to listeners
      while (true) {
        // If no message was available, pause for a short time to avoid busy-waiting
        if (!reader.readOne()) {
          try {
            Thread.sleep(cfg.pollingLatency);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
  }
}

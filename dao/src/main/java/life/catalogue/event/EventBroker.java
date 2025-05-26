package life.catalogue.event;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import life.catalogue.api.event.*;
import life.catalogue.common.Managed;
import life.catalogue.concurrent.ExecutorUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.openhft.chronicle.queue.ExcerptAppender;

import net.openhft.chronicle.wire.DocumentContext;

import org.jetbrains.annotations.NotNull;

import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

import org.mapdb.DataIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventBroker implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(EventBroker.class);

  private final BrokerConfig cfg;
  private final SingleChronicleQueue queue;
  private final ExcerptAppender appender;
  private final List<DatasetListener> datasetListeners = new ArrayList<>();
  private final List<DoiListener> doiListeners = new ArrayList<>();
  private final List<UserListener> userListeners = new ArrayList<>();
  private final List<SectorListener> sectorListeners = new ArrayList<>();
  private final KryoHelper io;
  private Thread polling;

  public EventBroker(BrokerConfig cfg) {
    this.cfg = cfg;
    this.queue = SingleChronicleQueueBuilder
      .single(cfg.queueDir)
      .rollCycle(RollCycles.FAST_DAILY)
      .build();
    this.appender = queue.acquireAppender();
    this.io = new KryoHelper(cfg);
  }

  public void register(Listener listener) {
    if (listener instanceof DatasetListener) {
      datasetListeners.add((DatasetListener) listener);
    } else if (listener instanceof DoiListener) {
      doiListeners.add((DoiListener) listener);
    } else if (listener instanceof UserListener) {
      userListeners.add((UserListener) listener);
    } else if (listener instanceof SectorListener) {
      sectorListeners.add((SectorListener) listener);
    } else {
      throw new IllegalArgumentException("Unknown listener type: " + listener.getClass().getSimpleName());
    }
  }

  public void dumpQueue(Writer writer) {
    queue.dump(writer, -1, Long.MAX_VALUE);
  }

  public void dumpQueue() {
    System.out.println("\n--- DUMP ---");
    System.out.println(queue.dump());
    System.out.println("--- END ---\n");
  }

  public synchronized void publish(Event event) {
    try (DocumentContext dc = appender.writingDocument()) {
      io.write(event, dc.wire().bytes().outputStream());
    } catch (IOException e) {
      LOG.error("Failed to publish event {}", event, e);
    }
  }

  @Override
  public void stop() {
    if (polling != null) {
      LOG.info("Stop event broker with queue at {}", cfg.queueDir);
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
      LOG.info("Start event broker with queue at {}", cfg.queueDir);
      polling = ExecutorUtils.runInNewThread(new Polling(), "event-broker-polling");
    }
  }

  private class Polling implements Runnable {
    @Override
    public void run() {
      final ExcerptTailer tailer = queue.createTailer(cfg.name).toEnd(); // we wind to the end to only consume new messages
      // Continuously read messages and distribute them to listeners
      while (true) {
        // If no message was available, pause for a short time to avoid busy-waiting
        var dc = tailer.readingDocument();
        if (dc.wire() == null) {
          try {
            Thread.sleep(cfg.pollingLatency);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        } else {
          broker(io.read(dc.wire().bytes().inputStream()));
        }
      }
    }

    private void broker(Object obj) {
      if (obj instanceof DatasetLogoChanged) {
        DatasetLogoChanged event = (DatasetLogoChanged) obj;
        for (DatasetListener l : datasetListeners) {
          l.datasetLogoChanged(event);
        }
      } else if (obj instanceof DatasetChanged) {
        DatasetChanged event = (DatasetChanged) obj;
        for (DatasetListener l : datasetListeners) {
          l.datasetChanged(event);
        }
      } else if (obj instanceof DatasetDataChanged) {
        DatasetDataChanged event = (DatasetDataChanged) obj;
        for (DatasetListener l : datasetListeners) {
          l.datasetDataChanged(event);
        }
      } else if (obj instanceof DeleteSector) {
        DeleteSector event = (DeleteSector) obj;
        for (SectorListener l : sectorListeners) {
          l.sectorDeleted(event);
        }
      } else if (obj instanceof ChangeDoi) {
        ChangeDoi event = (ChangeDoi) obj;
        for (DoiListener l : doiListeners) {
          l.doiChanged(event);
        }
      } else if (obj instanceof UserChanged) {
        UserChanged event = (UserChanged) obj;
        for (UserListener l : userListeners) {
          l.userChanged(event);
        }
      } else if (obj instanceof UserPermissionChanged) {
        UserPermissionChanged event = (UserPermissionChanged) obj;
        for (UserListener l : userListeners) {
          l.userPermissionChanged(event);
        }

      } else {
        LOG.error("Unknown event type: " + obj.getClass().getSimpleName());
      }
    }
  }
}

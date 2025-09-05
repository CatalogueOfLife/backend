package life.catalogue.event;

import life.catalogue.api.event.*;
import life.catalogue.common.Managed;
import life.catalogue.concurrent.ExecutorUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.openhft.chronicle.core.io.AbstractCloseable;
import net.openhft.chronicle.core.io.ThreadingIllegalStateException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.queue.util.FileUtil;
import net.openhft.chronicle.wire.DocumentContext;

import static java.util.stream.Collectors.toList;

/**
 * Broker of events being shared between JDKs using a Chronicle queue on disk.
 */
public class EventBroker implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(EventBroker.class);

  private final BrokerConfig cfg;
  private final List<DoiListener> doiListeners = new ArrayList<>();
  private final List<UserListener> userListeners = new ArrayList<>();
  private final List<SectorListener> sectorListeners = new ArrayList<>();
  private final List<DatasetListener> datasetListeners = new ArrayList<>();
  private final KryoHelper io;
  private final Thread polling;
  private final SingleChronicleQueue queue;
  private final ExcerptAppender appender;

  public EventBroker(BrokerConfig cfg) {
    this.cfg = cfg;
    this.io = new KryoHelper(cfg);
    queue = SingleChronicleQueueBuilder
      .single(cfg.queueDir)
      .rollCycle(RollCycles.FAST_DAILY)
      .build();
    appender = queue.acquireAppender();
    polling = ExecutorUtils.runInNewThread(new Polling(), "event-broker-polling");
    LOG.info("Started event broker with queue at {}", cfg.queueDir);
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

  public synchronized void publish(Event event) {
    LOG.debug("publish new event {}", event);
    // the chronicle appender remembers which thread wrote the last message
    // it only allows the same thread to write to the queue
    // we disable this check as we synchronize the method, so we never have multiple threads writing to the queue at the same time
    if (appender != null) {
      appender.singleThreadedCheckReset();
      try (DocumentContext dc = appender.writingDocument()) {
        io.write(event, dc.wire().bytes().outputStream());
      } catch (Exception e) {
        LOG.error("Failed to publish event {}", event, e);
      }

    } else {
      LOG.warn("Broker not started yet, cannot publish event {}", event);
    }
  }

  public boolean isAlive() {
    return polling.isAlive();
  }

  @Override
  public void close() throws IOException {
    LOG.info("Stoping event broker with queue at {} and polling thread {}", cfg.queueDir, polling);
    polling.interrupt();
    queue.close();
  }

  private class Polling implements Runnable {
    @Override
    public void run() {
      long lastDeleteCheck = System.currentTimeMillis();
      // we create a unique tailer for every webapp instance
      // this allows us to deploy several aps in parallel and still read all messages
      final ExcerptTailer tailer = queue.createTailer(cfg.name + "-" + lastDeleteCheck);
      // we wind to the end to only consume new messages as the queue likely already exists
      tailer.toEnd();
      // Continuously read messages and distribute them to listeners
      while (!Thread.currentThread().isInterrupted() && !tailer.isClosing()) {
        try {
          // If no message was available, pause for a short time to avoid busy-waiting
          var dc = tailer.readingDocument();
          if (dc.wire() == null) {
            // check for rolling file deletions once a day
            if (System.currentTimeMillis() - lastDeleteCheck > 24*60*60*1000) {
              removeUnusedFiles();
              lastDeleteCheck = System.currentTimeMillis();
            } else {
              try {
                Thread.sleep(cfg.pollingLatency);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
              }
            }

          } else {
            broker(io.read(dc.wire().bytes().inputStream()));
          }
        } catch (Exception e) {
          LOG.error("Event polling throws exception", e);
        }
      }
      LOG.warn("Event polling stopped!");
    }

    private void removeUnusedFiles() {
      LOG.info("Look for unused files from queue at {}", cfg.queueDir);
      List<File> candidates = FileUtil.removableRollFileCandidates(queue.file()).collect(toList());
      candidates.forEach(FileUtils::deleteQuietly);
      LOG.info("Removed {} unused files from queue at {}", candidates.size(), cfg.queueDir);
    }

    private void broker(Object obj) {
      LOG.debug("Broker event {}", obj);
      if (obj instanceof DatasetLogoChanged) {
        DatasetLogoChanged event = (DatasetLogoChanged) obj;
        for (DatasetListener l : datasetListeners) {
          try {
            l.datasetLogoChanged(event);
          } catch (Exception e) {
            LOG.error("Failed to broker logo change event: {}", event, e);
          }
        }
      } else if (obj instanceof DatasetChanged) {
        DatasetChanged event = (DatasetChanged) obj;
        for (DatasetListener l : datasetListeners) {
          try {
            l.datasetChanged(event);
          } catch (Exception e) {
            LOG.error("Failed to broker dataset change event: {}", event, e);
          }
        }
      } else if (obj instanceof DatasetDataChanged) {
        DatasetDataChanged event = (DatasetDataChanged) obj;
        for (DatasetListener l : datasetListeners) {
          try {
            l.datasetDataChanged(event);
          } catch (Exception e) {
            LOG.error("Failed to broker dataset data change event: {}", event, e);
          }
        }
      } else if (obj instanceof DeleteSector) {
        DeleteSector event = (DeleteSector) obj;
        for (SectorListener l : sectorListeners) {
          try {
            l.sectorDeleted(event);
          } catch (Exception e) {
            LOG.error("Failed to broker sector delete event: {}", event, e);
          }
        }
      } else if (obj instanceof ChangeDoi) {
        ChangeDoi event = (ChangeDoi) obj;
        for (DoiListener l : doiListeners) {
          try {
            l.doiChanged(event);
          } catch (Exception e) {
            LOG.error("Failed to broker DOI change event: {}", event, e);
          }
        }
      } else if (obj instanceof UserChanged) {
        UserChanged event = (UserChanged) obj;
        for (UserListener l : userListeners) {
          try {
            l.userChanged(event);
          } catch (Exception e) {
            LOG.error("Failed to broker user change event: {}", event, e);
          }
        }
      } else if (obj instanceof UserPermissionChanged) {
        UserPermissionChanged event = (UserPermissionChanged) obj;
        for (UserListener l : userListeners) {
          try {
            l.userPermissionChanged(event);
          } catch (Exception e) {
            LOG.error("Failed to broker user permission change event: {}", event, e);
          }
        }

      } else {
        LOG.error("Unknown event type: " + obj.getClass().getSimpleName());
      }
    }
  }
}

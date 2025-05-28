package life.catalogue.event;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DatasetLogoChanged;
import life.catalogue.api.vocab.Users;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import life.catalogue.common.io.TmpIO;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class EventBrokerTest {

  private EventBroker broker;
  private AtomicInteger cntD;
  private AtomicInteger cntDL;
  private TmpIO dir;

  @BeforeEach
  public void init() throws Exception {
    var cfg = new BrokerConfig();
    dir = new TmpIO.Dir();
    cfg.queueDir = dir.file+"/queue";
    cfg.pollingLatency = 1;
    this.broker = new EventBroker(cfg);
    this.cntD = new AtomicInteger(0);
    this.cntDL = new AtomicInteger(0);
    broker.register(new DatasetListener() {

      @Override
      public void datasetChanged(DatasetChanged event) {
        cntD.incrementAndGet();
        System.out.println(event);
      }

      @Override
      public void datasetLogoChanged(DatasetLogoChanged event) {
        cntDL.incrementAndGet();
        System.out.println(event);
      }

      @Override
      public void datasetDataChanged(DatasetDataChanged event) {
        System.out.println(event);
      }
    });
    broker.start();
  }

  @AfterEach
  public void stop() throws Exception {
    broker.stop();
    dir.close();
  }

  @Test
  public void publish() throws Exception {
    var d = TestEntityGenerator.newDataset("test D1");
    d.setKey(1);
    broker.publish(DatasetChanged.deleted(d, Users.TESTER));

    broker.publish(new DatasetLogoChanged(d.getKey()));

    d = TestEntityGenerator.newDataset("test D2");
    d.setKey(2);
    broker.publish(DatasetChanged.deleted(d, Users.TESTER));

    Thread.sleep(100);

    assertEquals(2, cntD.get());
    assertEquals(1, cntDL.get());
  }

  @Test
  public void multithreaded() throws Exception {
    var p1 = new PublishTask(broker, 1, 75);
    Thread t1 = new Thread(p1);

    var p2 = new PublishTask(broker, 1001, 1110);
    Thread t2 = new Thread(p2);

    var p3 = new PublishTask(broker, 2001, 2115);
    Thread t3 = new Thread(p3);
    t1.start();
    t2.start();
    t3.start();

    t1.join();
    t2.join();
    t3.join();

    Thread.sleep(100);

    assertEquals(300, cntD.get());
    assertEquals(300, cntDL.get());
  }

  class PublishTask implements Runnable {
    private final EventBroker broker;
    int start;
    int end;

    public PublishTask(EventBroker broker, int start, int end) {
      this.broker = broker;
      this.start = start;
      this.end = end;
    }

    @Override
    public void run() {
      for (int i = start; i <= end; i++) {
        var d = TestEntityGenerator.newDataset("test D"+i);
        d.setKey(i);
        broker.publish(DatasetChanged.deleted(d, Users.TESTER));
        broker.publish(new DatasetLogoChanged(d.getKey()));
      }
    }
  }
}
package life.catalogue.event;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DatasetLogoChanged;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TmpIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class EventBroker3Test {

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
}
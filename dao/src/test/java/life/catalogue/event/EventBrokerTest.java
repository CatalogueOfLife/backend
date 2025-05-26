package life.catalogue.event;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DatasetLogoChanged;
import life.catalogue.api.vocab.Users;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventBrokerTest {

  @Test
  void publish() throws Exception {
    var cfg = new BrokerConfig();
    cfg.pollingLatency = 2;
    cfg.name = "main";
    var broker = new EventBroker(cfg);
    var cntD = new AtomicInteger(0);
    var cntDL = new AtomicInteger(0);
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

    var d = TestEntityGenerator.newDataset("test D1");
    d.setKey(1);
    broker.publish(DatasetChanged.deleted(d, Users.TESTER));

    broker.publish(new DatasetLogoChanged(d.getKey()));

    d = TestEntityGenerator.newDataset("test D2");
    d.setKey(2);
    broker.publish(DatasetChanged.deleted(d, Users.TESTER));

    //broker.dumpQueue();
    Thread.sleep(50);
    broker.stop();

    assertEquals(2, cntD.get());
    assertEquals(1, cntDL.get());
  }
}
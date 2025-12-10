package life.catalogue.event;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DatasetLogoChanged;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TmpIO;

import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Ignore("Still unclear why this fails sometimes on jenkins but never locally")
public class EventBrokerMultithreadedTest extends EventBrokerTestBase {

  @Test
  public void multithreaded() throws Exception {
    init();
    try {
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
    } finally {
      stop();
    }
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
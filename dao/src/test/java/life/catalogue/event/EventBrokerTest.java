package life.catalogue.event;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetLogoChanged;
import life.catalogue.api.vocab.Users;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventBrokerTest extends EventBrokerTestBase {

  @Test
  public void publish() throws Exception {
    init();
    try {
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
    } finally {
      stop();
    }
  }
}
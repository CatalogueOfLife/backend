package life.catalogue;

import life.catalogue.event.EventBroker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtils {

  public static EventBroker mockedBroker() {
    EventBroker bus = mock(EventBroker.class);
    return bus;
  }
}

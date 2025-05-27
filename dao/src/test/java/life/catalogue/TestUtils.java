package life.catalogue;

import life.catalogue.event.EventBroker;

import static org.mockito.Mockito.mock;

public class TestUtils {

  public static EventBroker mockedBroker() {
    EventBroker bus = mock(EventBroker.class);
    return bus;
  }
}

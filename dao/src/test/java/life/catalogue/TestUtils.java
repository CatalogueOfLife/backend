package life.catalogue;

import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.event.EventBroker;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.mockito.Mockito.mock;

public class TestUtils {

  public static EventBroker mockedBroker() {
    EventBroker bus = mock(EventBroker.class);
    return bus;
  }

  public static DatasetInfoCache mockedInfoCache() throws Exception {
    var cache = mock(DatasetInfoCache.class);
    setFinalStatic(DatasetInfoCache.class.getDeclaredField("CACHE"), cache);
    return cache;
  }

  public static void setFinalStatic(Field field, Object newValue) throws Exception {
    field.setAccessible(true);
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    field.set(null, newValue);
  }
}

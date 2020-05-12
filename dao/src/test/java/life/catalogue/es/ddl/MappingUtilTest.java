package life.catalogue.es.ddl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MappingUtilTest {

  public List<String> testList = new ArrayList<>();

  public Set<Integer> testMethod() {
    return null;
  }

  @Test
  public void testGetTypeArgument1() throws NoSuchFieldException, SecurityException {
    Field f = getClass().getDeclaredField("testList");
    assertEquals(String.class, MappingUtil.getTypeArgument(f));
  }

  @Test
  public void testGetTypeArgument2() throws NoSuchMethodException, SecurityException {
    Method m = getClass().getDeclaredMethod("testMethod");
    assertEquals(Integer.class, MappingUtil.getTypeArgument(m));
  }

}

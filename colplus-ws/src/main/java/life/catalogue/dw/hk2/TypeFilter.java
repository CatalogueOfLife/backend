package life.catalogue.dw.hk2;

import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.MultiException;

public class TypeFilter implements Filter {

  private final Class<?> type;

  public TypeFilter(Class<?> type) {
    this.type = type;
  }

  @Override
  public boolean matches(Descriptor descriptor) {
    try {
      Class implClass = Class.forName(descriptor.getImplementation());
      return type.isAssignableFrom(implClass);
    } catch (ClassNotFoundException e) {
      throw new MultiException(e);
    }
  }
}

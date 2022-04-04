package life.catalogue.dw;

import io.dropwizard.lifecycle.Managed;

public interface ManagedExtended extends Managed {

  boolean hasStarted();

}

package life.catalogue.dw.jersey.provider;

import javax.ws.rs.ext.Provider;
import java.time.LocalDate;

/**
 * Jersey parameter converter & provider that uses our jackson Mapper
 * to serde LocalDate instances.
 */
@Provider
public class LocalDateParamConverterProvider extends AbstractJacksonConverterProvider<LocalDate> {

  public LocalDateParamConverterProvider() {
    super(LocalDate.class);
  }
}

package life.catalogue.dw.jersey.provider;

import java.time.LocalDate;

import jakarta.ws.rs.ext.Provider;

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

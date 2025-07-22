package life.catalogue.resources.legacy;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class LegacyConfig {

  /**
   * Optional sunset value for the deprecation header.
   * See https://datatracker.ietf.org/doc/draft-ietf-httpapi-deprecation-header/
   */
  public LocalDate sunset;

  @NotNull
  public String support = "support@catalogueoflife.org";

  /**
   * Delay in milliseconds to all requests to the legacy API.
   */
  @Min(0)
  public int delay = 0;
}

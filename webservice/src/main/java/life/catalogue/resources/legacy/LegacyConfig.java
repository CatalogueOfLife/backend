package life.catalogue.resources.legacy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;

public class LegacyConfig {

  /**
   * Optional URI to a TSV file that contains a mapping of legacy COL IDs to new name usage IDs.
   * First column must be the legacy ID, second column the new name usage ID.
   */
  @Nullable
  public URI idMapURI;

  /**
   * File to persist legacy id map on disk. If empty will use a volatile memory map.
   */
  public File idMapFile;

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

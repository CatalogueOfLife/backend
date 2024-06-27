package life.catalogue.matching.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * A status value derived from a dataset or external source. E.g. IUCN Red List status.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A status value derived from a dataset or external source. E.g. IUCN Red List status.",
  title = "Status", type = "object")
public class Status {
  @Schema(description = "The dataset key for the dataset that the status is associated with")
  private String datasetKey;
  @Schema(description = "The dataset alias for the dataset that the status is associated with")
  private String datasetAlias;
  @Schema(description = "The gbif registry key for the dataset that the status is associated with")
  private String gbifKey;
  @Schema(description = "The status value")
  private String status;
}
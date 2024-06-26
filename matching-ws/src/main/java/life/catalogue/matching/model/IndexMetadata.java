package life.catalogue.matching.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Metadata about an index.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Schema(description = "Metadata about an specific section of the index (e.g. main or identifier index)", title = "IndexMetadata", type = "object")
public class IndexMetadata {
  @Schema(description = "The dataset key")
  String datasetKey;
  @Schema(description = "The GBIF key")
  String gbifKey;
  @Schema(description = "The dataset description")
  String datasetTitle;
  @Schema(description = "The size of the index in MB")
  Long sizeInMB = 0L;
  @Schema(description = "The number of name usages in the index")
  Long nameUsageCount = 0L;
  @Schema(description = "The number of name usages matched to main index")
  Long matchesToMain;
  @Schema(description = "Counts of name usages by rank")
  Map<String, Long> nameUsageByRankCount = new HashMap<>();
}

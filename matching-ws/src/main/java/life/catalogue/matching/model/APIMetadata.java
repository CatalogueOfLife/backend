package life.catalogue.matching.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata about this API and about the indexes behind the API.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Schema(description = "Metadata about the index and software used to access the index through webservices",
  title = "APIMetadata", type = "object")
public class APIMetadata {
  @Schema(description = "When the index was created. Typically the build date of the docker image", format = "date-time")
  String created;
  @Schema(description = "Software version information", type = "object", implementation = BuildInfo.class)
  BuildInfo buildInfo ;
  @Schema(description = "The main index metadata", type = "object", implementation = IndexMetadata.class)
  IndexMetadata mainIndex;
  @Schema(description = "The list of identifier indexes", type = "array", implementation = IndexMetadata.class)
  List<IndexMetadata> identifierIndexes = new ArrayList<>();
  @Schema(description = "The list of ancillary indexes", type = "array", implementation = IndexMetadata.class)
  List<IndexMetadata> ancillaryIndexes = new ArrayList<>();
}

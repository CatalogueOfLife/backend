package life.catalogue.matching.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@Schema(description = "Diagnostics for a name match including the type of match and confidence level", title = "Diagnostics", type = "object")
public class Diagnostics {
  @Schema(description = "The match type, e.g. 'exact', 'fuzzy', 'partial', 'none'")
  MatchType matchType;
  @Schema(description = "Issues with the name usage match that has been returned")
  List<Issue> issues;
  @Schema(description = "Issues encountered during steps of the match process")
  List<ProcessFlag> processingFlags;
  @Schema(description = "Confidence level in percent")
  Integer confidence;
  @Schema(description = "The status of the match e.g. ACCEPTED, SYNONYM, AMBIGUOUS, EXCLUDED, etc.")
  TaxonomicStatus status;
  @Schema(description = "Additional notes about the match")
  String note;
  @Schema(description = "Time taken to perform the match in milliseconds")
  long timeTaken;
  @Schema(description = "A list of similar matches with lower confidence scores ")
  List<NameUsageMatch> alternatives;
}

package life.catalogue.matching;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Version 1 name usage match with legacy integer keys.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@Builder
@Schema(description = "A name usage match returned by the webservices. Includes higher taxonomy and diagnostics", title = "NameUsageMatch", type = "object")
public class NameUsageMatchV1 {

  @Schema(description = "If the matched usage is a synonym")
  boolean synonym = false;
  @Schema(description = "The matched name usage")
  RankedNameV1 usage;
  @Schema(description = "The accepted name usage for the match. This will only be populated when we've matched a synonym name usage.")
  RankedNameV1 acceptedUsage;
  @Schema(description = "The classification of the accepted name usage. ")
  List<RankedNameV1> classification = new ArrayList<>();
  @Schema(description = "A list of similar matches with lower confidence scores ")
  List<NameUsageMatchV1> alternatives = new ArrayList<>();
  @Schema(description = "Diagnostics for a name match including the type of match and confidence level")
  DiagnosticsV1 diagnostics = DiagnosticsV1.builder().build();

  @Data
  @Builder
  @Schema(description = "A name with an identifier and a taxonomic rank", title = "RankedName", type = "object")
  public static class RankedNameV1 {
    @Schema(description = "The identifier for the name usage")
    private Integer key;
    @Schema(description = "The name usage")
    private String name;
    @JsonIgnore
    private String canonicalName;
    @Schema(description = "The taxonomic rank for the name usage")
    private Rank rank;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Data
  @Builder
  @Schema(description = "Diagnostics for a name match including the type of match and confidence level", title = "Diagnostics", type = "object")
  public static class DiagnosticsV1 {
    @Schema(description = "The match type, e.g. 'exact', 'fuzzy', 'partial', 'none'")
    MatchType matchType;
    @Schema(description = "Confidence level in percent")
    Integer confidence;
    @Schema(description = "The status of the match e.g. ACCEPTED, SYNONYM, AMBIGUOUS, EXCLUDED, etc.")
    TaxonomicStatus status;
    @Schema(description = "Additional notes about the match")
    String note;
    @Schema(description = "Time taken to perform the match in milliseconds")
    long timeTaken;
    @Schema(description = "A list of similar matches with lower confidence scores ")
    List<NameUsageMatchV1> alternatives = new ArrayList<>();
  }

  public static Optional<NameUsageMatchV1> createFrom(NameUsageMatch nameUsageMatch) {
    if (nameUsageMatch == null)
      return Optional.empty();
    try {
      NameUsageMatchV1Builder builder = NameUsageMatchV1.builder();
      builder.synonym(nameUsageMatch.isSynonym());
      builder.usage(RankedNameV1.builder()
        .key(Integer.parseInt(nameUsageMatch.getUsage().getKey()))
        .name(nameUsageMatch.getUsage().getName())
        .rank(nameUsageMatch.getUsage().getRank())
        .build()
      );
      if (nameUsageMatch.getAcceptedUsage() != null) {
        builder.acceptedUsage(RankedNameV1.builder()
          .key(Integer.parseInt(nameUsageMatch.getAcceptedUsage().getKey()))
          .name(nameUsageMatch.getAcceptedUsage().getName())
          .rank(nameUsageMatch.getAcceptedUsage().getRank())
          .build()
        );
      }
      if (nameUsageMatch.getClassification() != null) {
        List<RankedNameV1> classification = new ArrayList<>();
        for (RankedName cl : nameUsageMatch.getClassification()) {
          classification.add(
            RankedNameV1.builder()
              .key(Integer.parseInt(cl.getKey()))
              .name(cl.getName())
              .rank(cl.getRank()).build())
          ;
        }
        builder.classification(classification);
      }

      if (nameUsageMatch.getDiagnostics() != null) {
        DiagnosticsV1.DiagnosticsV1Builder diagBuilder = DiagnosticsV1.builder();
        diagBuilder.matchType(nameUsageMatch.getDiagnostics().getMatchType());
        diagBuilder.confidence(nameUsageMatch.getDiagnostics().getConfidence());
        diagBuilder.status(nameUsageMatch.getDiagnostics().getStatus());
        diagBuilder.note(nameUsageMatch.getDiagnostics().getNote());
        diagBuilder.timeTaken(nameUsageMatch.getDiagnostics().getTimeTaken());
        if (nameUsageMatch.getDiagnostics().getAlternatives() != null) {
          List<NameUsageMatchV1> alts = new ArrayList<>();
          for (NameUsageMatch alt : nameUsageMatch.getDiagnostics().getAlternatives()) {
            alts.add(createFrom(alt).get());
          }
          diagBuilder.alternatives(alts);
        }
        builder.diagnostics(diagBuilder.build());
      }
      return Optional.of(builder.build());
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}

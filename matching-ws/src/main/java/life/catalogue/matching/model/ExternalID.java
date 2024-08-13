package life.catalogue.matching.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * An identifier from another checklist that may be associated with a name usage in the main index
 */
@Schema(description = "An identifier from another checklist that may be associated with a name usage in the main index", title = "ExternalID", type = "object")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ExternalID {
    @Schema(description = "The external identifier that may or may not have been associated with the main index")
    private String id;
    @Schema(description = "The main index identifier that the external identifier was matched to")
    private String mainIndexID;
    @Schema(description = "The gbif key of the dataset containing the joined external ID")
    private String gbifKey;
    @Schema(description = "The dataset key of the joined external ID")
    private String datasetKey;
    @Schema(description = "The dataset title of the joined external ID")
    private String datasetTitle;
    @Schema(description = "The parent ID of the external identifier")
    private String parentID;
    @Schema(description = "The scientific name associated with the external identifier")
    private String scientificName;
    @Schema(description = "The accepted taxon ID of the external identifier")
    private String acceptedTaxonID;
    @Schema(description = "The rank of the external identifier")
    private String rank;
    @Schema(description = "The status of the external identifier")
    private String status;
}

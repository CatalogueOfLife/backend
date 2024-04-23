package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import life.catalogue.api.vocab.MatchType;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Diagnostics {
    MatchType matchType;
    @JsonIgnore
    MatchIssueType matchIssueType;
    Integer confidence;
    String status;
    String note;
}

package life.catalogue.matching;

import life.catalogue.api.vocab.MatchType;
import lombok.Data;

@Data
public class Diagnostics {

    MatchType matchType;
    Integer confidence;
    String status;
    String note;
}

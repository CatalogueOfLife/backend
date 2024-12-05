package life.catalogue.matching.model;

import lombok.*;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class StoredParsedName {
  private String rank;
  private String code;
  private String uninomial;
  private String genus;
  private String infragenericEpithet;
  private String specificEpithet;
  private String infraspecificEpithet;
  private String cultivarEpithet;
  private String phrase;
  private String voucher;
  private String nominatingParty;
  private boolean candidatus;
  private String notho;
  private Boolean originalSpelling;
  private Map<String, String> epithetQualifier;
  private String type;
  protected boolean extinct;
  private StoredAuthorship combinationAuthorship;
  private StoredAuthorship basionymAuthorship;
  private String sanctioningAuthor;
  private String taxonomicNote;
  private String nomenclaturalNote;
  private String publishedIn;
  private String unparsed;
  private boolean doubtful;
  private boolean manuscript;
  private String state;
  private Set<String> warnings;

  //additional flags
  private boolean isAbbreviated;
  private boolean isAutonym;
  private boolean isBinomial;
  private boolean isTrinomial;
  private boolean isIncomplete;
  private boolean isIndetermined;
  private boolean isPhraseName;
  private String terminalEpithet;

  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  public static class StoredAuthorship {
    private List<String> authors = new ArrayList();
    private List<String> exAuthors = new ArrayList();
    private String year;
  }
}

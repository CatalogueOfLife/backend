package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.*;

import java.net.URI;
import java.util.Objects;

/**
 *
 */
public class Name {

  /**
   * Internal surrogate key of the name as provided by postgres.
   * This key is unique across all datasets but not exposed in the API.
   */
  @JsonIgnore
  private Integer key;

  /**
   * Primary key of the name as given in the dataset dwc:scientificNameID.
   * Only guaranteed to be unique within a dataset and can follow any kind of schema.
   */
  private String id;

  /**
   * Key to dataset instance. Defines context of the name key.
   */
  private Dataset dataset;

  /**
   * Entire canonical name string with a rank marker for infragenerics and infraspecfics, but excluding the authorship.
   * For uninomials, e.g. families or names at higher ranks, this is just the uninomial.
   */
  private String scientificName;

  /**
   * Parsed authorship of the name incl basionym and years
   */
  private Authorship authorship = new Authorship();

  /**
   * rank of the name from enumeration above
   */
  //@JsonProperty("rankMarker")
  //@JsonSerialize(using=RankSerde.RankJsonSerializer.class)
  //@JsonDeserialize(using=RankSerde.RankJsonDeserializer.class)
  private Rank rank;

  private Origin origin;

  private NomCode nomenclaturalCode;

  /**
   * The genus part of a bi- or trinomial name. Not used for genus names which are represented by the scientificName alone.
   */
  private String genus;

  /**
   * The infrageneric epithet. Used only as the terminal epithet for names at infrageneric ranks, not for species
   */
  private String infragenericEpithet;

  private String specificEpithet;

  private String infraspecificEpithet;

  /**
   * The part of the name which is considered a hybrid;  see [GBIF](https://github.com/gbif/gbif-api/blob/master/src/main/java/org/gbif/api/vocabulary/NamePart.java#L24)
   */
  private NamePart notho;

  /**
   * Link to the original combination.
   * In case of [replacement names](https://en.wikipedia.org/wiki/Nomen_novum) it points back to the replaced synonym.
   */
  private Name originalName;

  /**
   * true if the type specimen of the name is a fossil
   */
  private Boolean fossil;

  /**
   * Current nomenclatural status of the name taking into account all known nomenclatural acts.
   */
  private NomStatus status;

  /**
   * The kind of name classified in broad catagories based on their syntactical structure
   */
  private NameType type;

  private URI sourceUrl;

  /**
   * notes for general remarks on the name, i.e. its nomenclature
   */
  private String remarks;

  // TODO: add to stack incl DAO
  private String etymology;

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Dataset getDataset() {
    return dataset;
  }

  public void setDataset(Dataset dataset) {
    this.dataset = dataset;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  public Authorship getAuthorship() {
    return authorship;
  }

  public void setAuthorship(Authorship authorship) {
    this.authorship = authorship;
  }

  public String getEtymology() {
    return etymology;
  }

  public void setEtymology(String etymology) {
    this.etymology = etymology;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public Origin getOrigin() {
    return origin;
  }

  public void setOrigin(Origin origin) {
    this.origin = origin;
  }

  public NomCode getNomenclaturalCode() {
    return nomenclaturalCode;
  }

  public void setNomenclaturalCode(NomCode nomenclaturalCode) {
    this.nomenclaturalCode = nomenclaturalCode;
  }

  public String getGenus() {
    return genus;
  }

  public void setGenus(String genus) {
    this.genus = genus;
  }

  public String getInfragenericEpithet() {
    return infragenericEpithet;
  }

  public void setInfragenericEpithet(String infragenericEpithet) {
    this.infragenericEpithet = infragenericEpithet;
  }

  public String getSpecificEpithet() {
    return specificEpithet;
  }

  public void setSpecificEpithet(String specificEpithet) {
    this.specificEpithet = specificEpithet;
  }

  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }

  public void setInfraspecificEpithet(String infraspecificEpithet) {
    this.infraspecificEpithet = infraspecificEpithet;
  }

  public NamePart getNotho() {
    return notho;
  }

  public void setNotho(NamePart notho) {
    this.notho = notho;
  }

  public Name getOriginalName() {
    return originalName;
  }

  public void setOriginalName(Name originalName) {
    this.originalName = originalName;
  }

  public Boolean getFossil() {
    return fossil;
  }

  public void setFossil(Boolean fossil) {
    this.fossil = fossil;
  }

  public NomStatus getStatus() {
    return status;
  }

  public void setStatus(NomStatus status) {
    this.status = status;
  }

  public NameType getType() {
    return type;
  }

  public void setType(NameType type) {
    this.type = type;
  }

  public URI getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(URI sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public boolean hasAuthorship() {
    return authorship != null && !authorship.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Name name = (Name) o;
    return Objects.equals(key, name.key) &&
        Objects.equals(id, name.id) &&
        Objects.equals(dataset, name.dataset) &&
        Objects.equals(scientificName, name.scientificName) &&
        Objects.equals(authorship, name.authorship) &&
        rank == name.rank &&
        origin == name.origin &&
        nomenclaturalCode == name.nomenclaturalCode &&
        Objects.equals(genus, name.genus) &&
        Objects.equals(infragenericEpithet, name.infragenericEpithet) &&
        Objects.equals(specificEpithet, name.specificEpithet) &&
        Objects.equals(infraspecificEpithet, name.infraspecificEpithet) &&
        notho == name.notho &&
        Objects.equals(originalName, name.originalName) &&
        Objects.equals(fossil, name.fossil) &&
        status == name.status &&
        type == name.type &&
        Objects.equals(sourceUrl, name.sourceUrl) &&
        Objects.equals(remarks, name.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, dataset, scientificName, authorship, rank, origin, nomenclaturalCode, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, notho, originalName, fossil, status, type, sourceUrl, remarks);
  }

  @Override
  public String toString() {
    return "Name{" +
        "key=" + key +
        ", id='" + id + '\'' +
        ", dataset=" + dataset +
        ", scientificName='" + scientificName + '\'' +
        ", authorship='" + authorship + '\'' +
        ", rank=" + rank +
        ", origin=" + origin +
        ", nomenclaturalCode=" + nomenclaturalCode +
        ", genus='" + genus + '\'' +
        ", infragenericEpithet='" + infragenericEpithet + '\'' +
        ", specificEpithet='" + specificEpithet + '\'' +
        ", infraspecificEpithet='" + infraspecificEpithet + '\'' +
        ", notho=" + notho +
        ", originalName=" + originalName +
        ", fossil=" + fossil +
        ", status=" + status +
        ", type=" + type +
        ", sourceUrl=" + sourceUrl +
        ", remarks='" + remarks + '\'' +
        ", etymology='" + etymology + '\'' +
        '}';
  }

  /**
   * Builds a scientific name without authorship from the individual properties
   */
  public String buildScientificName() {
    return null;
  }

  /**
   * Builds a scientific name without authorship from the individual properties
   */
  public String buildScientificName(boolean authorship, boolean rank) {
    return null;
  }

  //
//  public String buildScientificName() {
//    return null;
//  }
//
//  /**
//   * build a name controlling all available flags for name parts to be included in the resulting name.
//   *
//   * @param hybridMarker    include the hybrid marker with the name if existing
//   * @param rankMarker      include the infraspecific or infrageneric rank marker with the name if existing
//   * @param authorship      include the names authorship (authorteam and year)
//   * @param infrageneric include the infrageneric name in brackets for species or infraspecies
//   * @param genusForInfrageneric include the genus name in front of an infrageneric name (not a species)
//   * @param abbreviateGenus if true abreviate the genus with its first character
//   * @param decomposition   decompose unicode ligatures into their corresponding ascii ones, e.g. æ beomes ae
//   * @param asciiOnly       transform unicode letters into their corresponding ascii ones, e.g. ø beomes o and ü u
//   * @param showIndet       if true include the rank marker for incomplete determinations, for example Puma spec.
//   * @param nomNote         include nomenclatural notes
//   * @param remarks         include informal remarks
//   */
//  public String buildName(
//      boolean hybridMarker,
//      boolean rankMarker,
//      boolean authorship,
//      boolean infrageneric,
//      boolean genusForInfrageneric,
//      boolean abbreviateGenus,
//      boolean decomposition,
//      boolean asciiOnly,
//      boolean showIndet,
//      boolean nomNote,
//      boolean remarks,
//      boolean showSensu,
//      boolean showCultivar,
//      boolean showStrain
//  ) {
//    StringBuilder sb = new StringBuilder();
//
//    if (org.gbif.api.vocabulary.NameType.CANDIDATUS == type) {
//      sb.append("Candidatus ");
//    }
//
//    if (genusOrAbove != null && (genusForInfrageneric || infraGeneric == null || specificEpithet != null)) {
//      if (hybridMarker && org.gbif.api.vocabulary.NamePart.GENERIC == notho) {
//        sb.append(HYBRID_MARKER);
//      }
//      if (abbreviateGenus) {
//        sb.append(genusOrAbove.substring(0, 1)).append('.');
//      } else {
//        sb.append(genusOrAbove);
//      }
//    }
//    if (specificEpithet == null) {
//      if (org.gbif.api.vocabulary.Rank.SPECIES == rank) {
//        // no species epitheton given, but rank=species. Indetermined species!
//        if (showIndet) {
//          sb.append(" spec.");
//        }
//      } else if (rank != null && rank.isInfraspecific()) {
//        // no species epitheton given, but rank below species. Indetermined!
//        if (showIndet) {
//          sb.append(' ');
//          sb.append(rank.getMarker());
//        }
//      } else if (infraGeneric != null) {
//        // this is the terminal name part - always show it!
//        if (rankMarker && rank != null) {
//          // If we know the rank we use explicit rank markers
//          // this is how botanical infrageneric names are formed, see http://www.iapt-taxon.org/nomen/main.php?page=art21
//          sb.append(' ');
//          appendRankMarker(sb, rank);
//          sb.append(infraGeneric);
//
//        } else {
//          if (genusForInfrageneric && genusOrAbove != null) {
//            // if we have shown the genus already and we do not know the rank we use parenthesis to indicate an infrageneric
//            sb.append(" (")
//                .append(infraGeneric)
//                .append(")");
//          } else {
//            // no genus shown yet, just show the plain infrageneric name
//            sb.append(infraGeneric);
//          }
//        }
//      }
//      // genus/infrageneric authorship
//      if (authorship) {
//        appendAuthorship(sb);
//      }
//    } else {
//      if (infrageneric && infraGeneric != null && (rank == null || rank == org.gbif.api.vocabulary.Rank.GENUS)) {
//        // only show subgenus if requested
//        sb.append(" (");
//        sb.append(infraGeneric);
//        sb.append(')');
//      }
//
//      // species part
//      sb.append(' ');
//      if (hybridMarker && org.gbif.api.vocabulary.NamePart.SPECIFIC == notho) {
//        sb.append(HYBRID_MARKER);
//      }
//      String epi = specificEpithet.replaceAll("[ _-]", "-");
//      sb.append(epi);
//
//      if (infraSpecificEpithet == null) {
//        // Indetermined? Only show indet cultivar marker if no cultivar epithet exists
//        if (showIndet && rank != null && rank.isInfraspecific() && (org.gbif.api.vocabulary.Rank.CULTIVAR != rank || cultivarEpithet == null)) {
//          // no infraspecific epitheton given, but rank below species. Indetermined!
//          sb.append(' ');
//          sb.append(rank.getMarker());
//        }
//
//        // species authorship
//        if (authorship) {
//          appendAuthorship(sb);
//        }
//      } else {
//        // infraspecific part
//        sb.append(' ');
//        if (hybridMarker && org.gbif.api.vocabulary.NamePart.INFRASPECIFIC == notho) {
//          if (rankMarker) {
//            sb.append("notho");
//          } else {
//            sb.append(HYBRID_MARKER);
//          }
//        }
//        if (rankMarker) {
//          appendRankMarker(sb, rank);
//        }
//        epi = infraSpecificEpithet.replaceAll("[ _-]", "-");
//        sb.append(epi);
//        // non autonym authorship ?
//        if (authorship && !isAutonym()) {
//          appendAuthorship(sb);
//        }
//      }
//    }
//
//    // add cultivar name
//    if (showStrain && strain != null) {
//      sb.append(" ");
//      sb.append(strain);
//    }
//
//    // add cultivar name
//    if (showCultivar && cultivarEpithet != null) {
//      sb.append(" '");
//      sb.append(cultivarEpithet);
//      sb.append("'");
//    }
//
//    // add sensu/sec reference
//    if (showSensu && sensu != null) {
//      sb.append(" ");
//      sb.append(sensu);
//    }
//
//    // add nom status
//    if (nomNote && nomStatus != null) {
//      sb.append(", ");
//      sb.append(nomStatus);
//    }
//
//    // add remarks
//    if (remarks && this.remarks != null) {
//      sb.append(" [");
//      sb.append(this.remarks);
//      sb.append("]");
//    }
//
//    String name = sb.toString().trim();
//    if (decomposition) {
//      name = UnicodeUtils.decompose(name);
//    }
//    if (asciiOnly) {
//      name = UnicodeUtils.ascii(name);
//    }
//    return Strings.emptyToNull(name);
//  }
//
//  private void appendRankMarker(StringBuilder sb, org.gbif.api.vocabulary.Rank rank) {
//    if (rank != null && rank.getMarker() != null) {
//      sb.append(rank.getMarker());
//      sb.append(' ');
//    }
//  }
//
//  private void appendAuthorship(StringBuilder sb) {
//    if (bracketAuthorship == null) {
//      if (bracketYear != null) {
//        sb.append(" (");
//        sb.append(bracketYear);
//        sb.append(")");
//      }
//    } else {
//      sb.append(" (");
//      sb.append(bracketAuthorship);
//      if (bracketYear != null) {
//        sb.append(", ");
//        sb.append(bracketYear);
//      }
//      sb.append(")");
//    }
//    if (authorship != null) {
//      sb.append(" ").append(authorship);
//    }
//    if (year != null) {
//      sb.append(", ");
//      sb.append(year);
//    }
//  }

}

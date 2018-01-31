package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.Origin;
import org.gbif.nameparser.api.*;
import org.gbif.nameparser.util.NameFormatter;

import javax.annotation.Nonnull;
import java.net.URI;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import static org.gbif.nameparser.util.NameFormatter.HYBRID_MARKER;

/**
 *
 */
public class Name {

	/**
	 * Internal surrogate key of the name as provided by postgres. This key is
	 * unique across all datasets but not exposed in the API.
	 */
	private Integer key;

	/**
	 * Primary key of the name as given in the dataset dwc:scientificNameID. Only
	 * guaranteed to be unique within a dataset and can follow any kind of schema.
	 */
	private String id;

	/**
	 * Key to dataset instance. Defines context of the name key.
	 */
	private Integer datasetKey;

  /**
   * Link to the original combination. In case of [replacement
   * names](https://en.wikipedia.org/wiki/Nomen_novum) it points back to the
   * replaced synonym.
   */
  private Integer basionymKey;

	/**
	 * Global name identifier from a nomenclator or the clearinghouse
	 */
	private String scientificNameID;

	/**
	 * Entire canonical name string with a rank marker for infragenerics and
	 * infraspecfics, but excluding the authorship. For uninomials, e.g. families or
	 * names at higher ranks, this is just the uninomial.
	 */
	private String scientificName;

  /**
   * Rank of the name from enumeration
   */
  @Nonnull
  private Rank rank = Rank.UNRANKED;

  /**
   * Represents the monomial for genus, families or names at higher ranks which do not have further epithets.
   */
  private String uninomial;

  /**
   * The genus part of a bi- or trinomial name. Not used for genus names which are
   * represented by the scientificName alone.
   */
  private String genus;

  /**
   * The infrageneric epithet. Used only as the terminal epithet for names at
   * infrageneric ranks, not for species
   */
  private String infragenericEpithet;

  private String specificEpithet;

  private String infraspecificEpithet;

  private String cultivarEpithet;

  private String strain;

  /**
   * A bacterial candidate name.
   * Candidatus is a provisional status for incompletely described procaryotes
   * (e.g. that cannot be maintained in a Bacteriology Culture Collection)
   * which was published in the January 1995.
   * The category Candidatus is not covered by the Rules of the Bacteriological Code but is a taxonomic assignment.
   *
   * The names included in the category Candidatus are usually written as follows:
   * Candidatus (in italics), the subsequent name(s) in roman type and the entire name in quotation marks.
   * For example, "Candidatus Phytoplasma", "Candidatus Phytoplasma allocasuarinae".
   *
   * See http://www.bacterio.net/-candidatus.html
   * and https://en.wikipedia.org/wiki/Candidatus
   */
  private boolean candidatus;

  /**
   * The part of the named hybrid which is considered a hybrid
   */
  private NamePart notho;

  /**
   * Authorship with years of the name, but excluding any basionym authorship.
   * For binomials the combination authors.
   */
  private Authorship combinationAuthorship = new Authorship();

  /**
   * Basionym authorship with years of the name
   */
  private Authorship basionymAuthorship = new Authorship();

  /**
   * The sanctioning author for sanctioned fungal names.
   * Fr. or Pers.
   */
  private String sanctioningAuthor;

  private NomCode code;

  /**
   * Current nomenclatural status of the name taking into account all known
   * nomenclatural acts.
   */
  private NomStatus status;

  private Origin origin;

  /**
   * The kind of name classified in broad catagories based on their syntactical
   * structure
   */
  private NameType type;

	private URI sourceUrl;

	/**
	 * true if the type specimen of the name is a fossil
	 */
	private Boolean fossil;

  /**
   * Any informal remarks found in the name
   */
  private String remarks;

	/**
	 * Issues related to this name with potential values in the map
	 */
	private Set<Issue> issues = EnumSet.noneOf(Issue.class);



  public Name() {
	}

  /**
   * @return a ParsedName instance representing this name
   */
	public static ParsedName toParsedName(Name n) {
    ParsedName pn = new ParsedName();
    pn.setUninomial(n.getUninomial());
    pn.setGenus(n.getGenus());
    pn.setInfragenericEpithet(n.getInfragenericEpithet());
    pn.setSpecificEpithet(n.getSpecificEpithet());
    pn.setInfraspecificEpithet(n.getInfraspecificEpithet());
    pn.setCultivarEpithet(n.getCultivarEpithet());
    pn.setStrain(n.getStrain());
    pn.setCombinationAuthorship(n.getCombinationAuthorship());
    pn.setBasionymAuthorship(n.getBasionymAuthorship());
    pn.setSanctioningAuthor(n.getSanctioningAuthor());
    pn.setRank(n.getRank());
    pn.setCode(n.getCode());
    pn.setCandidatus(pn.isCandidatus());
    pn.setNotho(n.getNotho());
    pn.setRemarks(n.getRemarks());
    pn.setType(n.getType());
    return pn;
  }

	/**
	 * //TODO Copy constructor. Creates shallow copy.
	 */
	public Name(Name n) {
	  throw new NotImplementedException("");
	}

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

	public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer key) {
		this.datasetKey = key;
	}

	public String getScientificNameID() {
		return scientificNameID;
	}

	public void setScientificNameID(String scientificNameID) {
		this.scientificNameID = scientificNameID;
	}

	public String getScientificName() {
		return scientificName;
	}

	public void setScientificName(String scientificName) {
		this.scientificName = scientificName;
	}

	public Origin getOrigin() {
		return origin;
	}

	public void setOrigin(Origin origin) {
		this.origin = origin;
	}

	public Integer getBasionymKey() {
		return basionymKey;
	}

	public void setBasionymKey(Integer key) {
		this.basionymKey = key;
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

	public URI getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(URI sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public Set<Issue> getIssues() {
		return issues;
	}

	public void setIssues(Set<Issue> issues) {
		this.issues = issues;
	}

	public void addIssue(Issue issue) {
		issues.add(issue);
	}





  public Authorship getCombinationAuthorship() {
    return combinationAuthorship;
  }

  public void setCombinationAuthorship(Authorship combinationAuthorship) {
    this.combinationAuthorship = combinationAuthorship;
  }

  public Authorship getBasionymAuthorship() {
    return basionymAuthorship;
  }

  public void setBasionymAuthorship(Authorship basionymAuthorship) {
    this.basionymAuthorship = basionymAuthorship;
  }

  public String getSanctioningAuthor() {
    return sanctioningAuthor;
  }

  public void setSanctioningAuthor(String sanctioningAuthor) {
    this.sanctioningAuthor = sanctioningAuthor;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank == null ? Rank.UNRANKED : rank;
  }

  public NomCode getCode() {
    return code;
  }

  public void setCode(NomCode code) {
    this.code = code;
  }

  public String getUninomial() {
    return uninomial;
  }

  private boolean setNothoIfHybrid(String x, NamePart part) {
    boolean isHybrid = x != null && !x.isEmpty() && x.charAt(0) == HYBRID_MARKER;
    if (isHybrid) {
      notho = part;
    }
    return isHybrid;
  }

  public void setUninomial(String uni) {
    if (setNothoIfHybrid(uni, NamePart.GENERIC)) {
      this.uninomial = uni.substring(1);
    } else {
      this.uninomial = uni;
    }
  }

  public String getGenus() {
    return genus;
  }

  public void setGenus(String genus) {
    if (setNothoIfHybrid(genus, NamePart.GENERIC)) {
      this.genus = genus.substring(1);
    } else {
      this.genus = genus;
    }
  }

  public String getInfragenericEpithet() {
    return infragenericEpithet;
  }

  public void setInfragenericEpithet(String infraGeneric) {
    if (setNothoIfHybrid(infraGeneric, NamePart.INFRAGENERIC)) {
      this.infragenericEpithet = infraGeneric.substring(1);
    } else {
      this.infragenericEpithet = infraGeneric;
    }
  }

  public String getSpecificEpithet() {
    return specificEpithet;
  }

  public void setSpecificEpithet(String species) {
    if (setNothoIfHybrid(species, NamePart.SPECIFIC)) {
      specificEpithet = species.substring(1);
    } else {
      specificEpithet = species;
    }
  }

  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }

  public void setInfraspecificEpithet(String infraSpecies) {
    if (setNothoIfHybrid(infraSpecies, NamePart.INFRASPECIFIC)) {
      this.infraspecificEpithet = infraSpecies.substring(1);
    } else {
      this.infraspecificEpithet = infraSpecies;
    }
  }

  public String getCultivarEpithet() {
    return cultivarEpithet;
  }

  public void setCultivarEpithet(String cultivarEpithet) {
    this.cultivarEpithet = cultivarEpithet;
  }

  public String getStrain() {
    return strain;
  }

  public void setStrain(String strain) {
    this.strain = strain;
  }

  public boolean isCandidatus() {
    return candidatus;
  }

  public void setCandidatus(boolean candidatus) {
    this.candidatus = candidatus;
  }

  public NamePart getNotho() {
    return notho;
  }

  public void setNotho(NamePart notho) {
    this.notho = notho;
  }

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public void addRemark(String remark) {
    if (!StringUtils.isBlank(remark)) {
      this.remarks = remarks == null ? remark.trim() : remarks + "; " + remark.trim();
    }
  }

  public NameType getType() {
    return type;
  }

  public void setType(NameType type) {
    this.type = type;
  }

  /**
   * @return the terminal epithet. Infraspecific epithet if existing, the species epithet or null
   */
  @JsonIgnore
  public String getTerminalEpithet() {
    return infraspecificEpithet == null ? specificEpithet : infraspecificEpithet;
  }

  /**
   * @return true if any kind of authorship exists
   */
  @JsonIgnore
  public boolean hasAuthorship() {
    return combinationAuthorship.exists() || basionymAuthorship.exists();
  }

  @JsonIgnore
  public boolean isAutonym() {
    return specificEpithet != null && infraspecificEpithet != null && specificEpithet.equals(infraspecificEpithet);
  }

  /**
   * @return true if the name is a bi- or trinomial with at least a genus and species epithet given.
   */
  @JsonIgnore
  public boolean isBinomial() {
    return genus != null && specificEpithet != null;
  }

  /**
   * @return true if the name is a trinomial with at least a genus, species and infraspecific epithet given.
   */
  @JsonIgnore
  public boolean isTrinomial() {
    return isBinomial() && infraspecificEpithet!= null;
  }

  @JsonIgnore
  public boolean isIndetermined() {
    return rank.isInfragenericStrictly() && infragenericEpithet == null && specificEpithet == null
        || rank.isSpeciesOrBelow() && specificEpithet == null
        || rank.isCultivarRank() && cultivarEpithet == null
        || rank.isInfraspecific() && !rank.isCultivarRank() && infraspecificEpithet == null;
  }

  /**
   * Validates consistency of name properties. This method checks if the given
   * rank matches populated properties and available properties make sense
   * together.
   */
  @JsonIgnore
  public boolean isConsistent() {
    if (specificEpithet != null && genus == null) {
      return false;

    } else if (infraspecificEpithet != null && specificEpithet == null) {
      return false;

    } else if (infragenericEpithet != null && specificEpithet != null) {
      return false;

    }
    // verify ranks
    if (rank.notOtherOrUnranked()) {
      if (rank.isGenusOrSuprageneric()) {
        if (genus != null || uninomial == null)
          return false;

      } else if (rank.isInfrageneric() && rank.isSupraspecific()) {
        if (infragenericEpithet == null)
          return false;

      } else if (rank.isSpeciesOrBelow()) {
        if (specificEpithet == null)
          return false;
        if (!rank.isInfraspecific() && infraspecificEpithet != null)
          return false;
      }

      if (rank.isInfraspecific()) {
        if (infraspecificEpithet == null)
          return false;
      }
    }
    return true;
  }

  /**
   * @See NameFormatter.canonical()
   */
  public String canonicalName() {
    return NameFormatter.canonical(toParsedName(this));
  }

  /**
   * @See NameFormatter.canonicalNameWithoutAuthorship()
   */
  public String canonicalNameWithoutAuthorship() {
    return NameFormatter.canonicalWithoutAuthorship(toParsedName(this));
  }

  /**
   * @See NameFormatter.canonicalMinimal()
   */
  public String canonicalNameMinimal() {
    return NameFormatter.canonicalMinimal(toParsedName(this));
  }

  /**
   * @See NameFormatter.canonicalComplete()
   */
  public String canonicalNameComplete() {
    return NameFormatter.canonicalComplete(toParsedName(this));
  }

  /**
   * @See NameFormatter.authorshipComplete()
   */
  public String authorshipComplete() {
    return NameFormatter.authorshipComplete(toParsedName(this));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Name name = (Name) o;
    return candidatus == name.candidatus &&
        Objects.equals(key, name.key) &&
        Objects.equals(id, name.id) &&
        Objects.equals(datasetKey, name.datasetKey) &&
        Objects.equals(basionymKey, name.basionymKey) &&
        Objects.equals(scientificNameID, name.scientificNameID) &&
        Objects.equals(scientificName, name.scientificName) &&
        rank == name.rank &&
        Objects.equals(uninomial, name.uninomial) &&
        Objects.equals(genus, name.genus) &&
        Objects.equals(infragenericEpithet, name.infragenericEpithet) &&
        Objects.equals(specificEpithet, name.specificEpithet) &&
        Objects.equals(infraspecificEpithet, name.infraspecificEpithet) &&
        Objects.equals(cultivarEpithet, name.cultivarEpithet) &&
        Objects.equals(strain, name.strain) &&
        notho == name.notho &&
        Objects.equals(combinationAuthorship, name.combinationAuthorship) &&
        Objects.equals(basionymAuthorship, name.basionymAuthorship) &&
        Objects.equals(sanctioningAuthor, name.sanctioningAuthor) &&
        code == name.code &&
        type == name.type &&
        origin == name.origin &&
        Objects.equals(sourceUrl, name.sourceUrl) &&
        Objects.equals(fossil, name.fossil) &&
        status == name.status &&
        Objects.equals(remarks, name.remarks) &&
        Objects.equals(issues, name.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, datasetKey, basionymKey, scientificNameID, scientificName, rank, uninomial, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, cultivarEpithet, strain, candidatus, notho, combinationAuthorship, basionymAuthorship, sanctioningAuthor, code, type, origin, sourceUrl, fossil, status, remarks, issues);
  }

  @Override
	public String toString() {
		return key + "[" + id + "] " + canonicalNameComplete();
	}

}

package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.exception.InvalidNameException;
import org.col.api.vocab.*;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
public class Name {
  private static final Character HYBRID_MARKER = '×';

	/**
	 * Internal surrogate key of the name as provided by postgres. This key is
	 * unique across all datasets but not exposed in the API.
	 */
	@JsonIgnore
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
	 * Entire canonical name string with a rank marker for infragenerics and
	 * infraspecfics, but excluding the authorship. For uninomials, e.g. families or
	 * names at higher ranks, this is just the uninomial.
	 */
	private String scientificName;

	/**
	 * Authorship with years of the name, but excluding any basionym authorship.
   * For binomials the combination authors.
	 */
	private Authorship authorship = new Authorship();

  /**
   * Basionym authorship with years of the name
   */
  private Authorship basionymAuthorship = new Authorship();

  /**
   * The sanctioning author for sanctioned fungal names.
   * Fr. or Pers.
   */
  private String sanctioningAuthor;

	/**
	 * rank of the name from enumeration above
	 */
	// @JsonProperty("rankMarker")
	// @JsonSerialize(using=RankSerde.RankJsonSerializer.class)
	// @JsonDeserialize(using=RankSerde.RankJsonDeserializer.class)
	private Rank rank = Rank.UNRANKED;

	private Origin origin;

	private NomCode nomenclaturalCode;

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
	 * The part of the name which is considered a hybrid; see
	 * [GBIF](https://github.com/gbif/gbif-api/blob/master/src/main/java/org/gbif/api/vocabulary/NamePart.java#L24)
	 */
	private NamePart notho;

	/**
	 * Link to the original combination. In case of [replacement
	 * names](https://en.wikipedia.org/wiki/Nomen_novum) it points back to the
	 * replaced synonym.
	 */
	private Integer basionymKey;

	/**
	 * true if the type specimen of the name is a fossil
	 */
	private Boolean fossil;

	/**
	 * Current nomenclatural status of the name taking into account all known
	 * nomenclatural acts.
	 */
	private NomStatus status;

	/**
	 * The kind of name classified in broad catagories based on their syntactical
	 * structure
	 */
	private NameType type;

	private URI sourceUrl;

	/**
	 * notes for general remarks on the name, i.e. its nomenclature
	 */
	private String remarks;

	/**
	 * Issues related to this name with potential values in the map
	 */
	private Map<Issue, String> issues = new EnumMap<>(Issue.class);

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

  public NamePart getNotho() {
		return notho;
	}

	public void setNotho(NamePart notho) {
		this.notho = notho;
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

	public Map<Issue, String> getIssues() {
		return issues;
	}

	public void setIssues(Map<Issue, String> issues) {
		this.issues = issues;
	}

	public void addIssue(Issue issue) {
		issues.put(issue, null);
	}

	public void addIssue(Issue issue, Object value) {
		issues.put(issue, value.toString());
	}

  /**
   * @return the full authorship string as used in scientific names
   */
  public String fullAuthorship() {
    if (authorship.exists() || basionymAuthorship.exists()) {
      StringBuilder sb = new StringBuilder();
      if (basionymAuthorship.exists()) {
        sb.append("(");
        sb.append(basionymAuthorship.toString());
        sb.append(") ");
      }
      if (authorship.exists()) {
        sb.append(authorship.toString());
        //TODO: remove in default rendering according to Paul Kirk!
        if (sanctioningAuthor != null) {
          sb.append(" : ");
          sb.append(sanctioningAuthor);
        }
      }
      return sb.toString();
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Name name = (Name) o;
    return Objects.equals(key, name.key) &&
        Objects.equals(id, name.id) &&
        Objects.equals(datasetKey, name.datasetKey) &&
        Objects.equals(scientificName, name.scientificName) &&
        Objects.equals(authorship, name.authorship) &&
        Objects.equals(basionymAuthorship, name.basionymAuthorship) &&
        Objects.equals(sanctioningAuthor, name.sanctioningAuthor) &&
        rank == name.rank &&
        origin == name.origin &&
        nomenclaturalCode == name.nomenclaturalCode &&
        Objects.equals(genus, name.genus) &&
        Objects.equals(infragenericEpithet, name.infragenericEpithet) &&
        Objects.equals(specificEpithet, name.specificEpithet) &&
        Objects.equals(infraspecificEpithet, name.infraspecificEpithet) &&
        notho == name.notho &&
        Objects.equals(basionymKey, name.basionymKey) &&
        Objects.equals(fossil, name.fossil) &&
        status == name.status &&
        type == name.type &&
        Objects.equals(sourceUrl, name.sourceUrl) &&
        Objects.equals(remarks, name.remarks) &&
        Objects.equals(issues, name.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, datasetKey, scientificName, authorship, basionymAuthorship, sanctioningAuthor, rank, origin, nomenclaturalCode, genus, infragenericEpithet, specificEpithet, infraspecificEpithet, notho, basionymKey, fossil, status, type, sourceUrl, remarks, issues);
  }

  @Override
  public String toString() {
    return key + "[" + id + "] " + buildScientificName() + " " + fullAuthorship();
  }

	/**
	 * Validates consistency of name properties. This method checks if the given
	 * rank matches populated properties and available properties make sense
	 * together.
	 */
	public boolean isConsistent() {
		if (specificEpithet != null && genus == null) {
			return false;

		} else if (infraspecificEpithet != null && specificEpithet == null) {
			return false;

		} else if (infragenericEpithet != null && specificEpithet != null) {
			return false;

		}
		// verify ranks
		if (rank != null && rank.notOtherOrUnranked()) {
			if (rank.isGenusOrSuprageneric()) {
				if (genus != null || scientificName == null)
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
	 * Builds a scientific name without authorship from the individual properties
	 */
	public String buildScientificName() throws InvalidNameException {
		if (infragenericEpithet != null) {
			// an infrageneric with or without a genus given?
			StringBuilder sb = new StringBuilder();
			if (genus != null) {
			  if (NamePart.GENERIC == notho) {
			    sb.append(HYBRID_MARKER);
          sb.append(" ");
        }
				sb.append(genus);
				sb.append(" ");
			}

      if (rank.isInfrageneric()) {
        sb.append(rank.getMarker());
        sb.append(" ");
      }
      if (NamePart.INFRAGENERIC == notho) {
        sb.append(HYBRID_MARKER);
        sb.append(" ");
      }
      sb.append(infragenericEpithet);

			return sb.toString();

		} else if (genus == null) {
			// this is a uninomial, e.g. genus or a higher rank
      if (NamePart.GENERIC == notho) {
        return HYBRID_MARKER + " " + scientificName;
      }
      return scientificName;

		} else if (specificEpithet != null) {
			// species at least, maybe infraspecific
			StringBuilder sb = new StringBuilder();
      if (NamePart.GENERIC == notho) {
        sb.append(HYBRID_MARKER);
        sb.append(" ");
      }
			sb.append(genus);
			sb.append(" ");
      if (NamePart.SPECIFIC == notho) {
        sb.append(HYBRID_MARKER);
        sb.append(" ");
      }
			sb.append(specificEpithet);
			if (infraspecificEpithet != null) {
				sb.append(" ");
				if (rank.isInfraspecific() && !rank.isUncomparable()) {
					sb.append(rank.getMarker());
					sb.append(" ");
				}
        if (NamePart.INFRASPECIFIC == notho) {
          sb.append(HYBRID_MARKER);
          sb.append(" ");
        }
				sb.append(infraspecificEpithet);
			}
			return sb.toString();

		} else {
			throw new InvalidNameException("Name without species epithet but not an infrageneric", this);
		}
	}

}

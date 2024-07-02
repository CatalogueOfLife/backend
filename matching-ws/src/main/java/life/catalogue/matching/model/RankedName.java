package life.catalogue.matching.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import io.swagger.v3.oas.annotations.media.Schema;
import org.gbif.nameparser.api.Rank;

/**
 * A name with an identifier and a taxonomic rank.
 */
@Schema(description = "A name with an identifier and a taxonomic rank", title = "RankedName", type = "object")
public class RankedName {
  @Schema(description = "The identifier for the name usage")
  private String key;
  @Schema(description = "The name usage")
  private String name;
  @JsonIgnore private String canonicalName;
  @Schema(description = "The taxonomic rank for the name usage")
  private Rank rank;

  public RankedName() {}

  public RankedName(String key, String name, Rank rank) {
    this.key = key;
    this.name = name;
    this.rank = rank;
  }

  public RankedName(String key, String name, Rank rank, String canonicalName) {
    this.key = key;
    this.name = name;
    this.rank = rank;
    this.canonicalName = canonicalName;
  }

  public String getKey() {
    return this.key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Rank getRank() {
    return this.rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  @JsonIgnore
  public String getCanonicalName() {
    return this.canonicalName;
  }

  @JsonIgnore
  public void setCanonicalName(String canonicalName) {
    this.canonicalName = canonicalName;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o != null && this.getClass() == o.getClass()) {
      RankedName u = (RankedName) o;
      return Objects.equals(this.key, u.key)
          && Objects.equals(this.name, u.name)
          && this.rank == u.rank;
    } else {
      return false;
    }
  }

  public int hashCode() {
    return Objects.hash(new Object[] {this.key, this.name, this.rank});
  }

  public String toString() {
    return this.name + " [" + this.key + ',' + this.rank + ']';
  }
}

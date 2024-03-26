package life.catalogue.matching;

import java.util.Objects;
import org.gbif.api.vocabulary.Rank;

public class RankedName {
  private String key;
  private String name;
  private Rank rank;

  public RankedName() {}

  public RankedName(String key, String name, Rank rank) {
    this.key = key;
    this.name = name;
    this.rank = rank;
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

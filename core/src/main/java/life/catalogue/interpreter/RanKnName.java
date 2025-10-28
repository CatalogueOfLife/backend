package life.catalogue.interpreter;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;

public class RanKnName {
  public final Rank rank;
  public final String name;

  public RanKnName(Rank rank, String name) {
    this.rank = rank;
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RanKnName ranKnName = (RanKnName) o;
    return rank == ranKnName.rank &&
      Objects.equals(name, ranKnName.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rank, name);
  }
}

package life.catalogue.matching;

public class IntRange {
  final Integer min;
  final Integer max;

  public IntRange(Integer min, Integer max) {
    this.min = min;
    this.max = max;
  }

  public boolean contains(int x) {
    return (min == null || x >= min) && (max == null || x <= max);
  }
}

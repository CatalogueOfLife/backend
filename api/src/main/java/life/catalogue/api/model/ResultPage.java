package life.catalogue.api.model;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * A generic paging response wrapping a list payload
 *
 * @param <T> the type of the paging content
 */
public class ResultPage<T> extends Page implements Iterable<T> {
  private int total;
  private List<T> result;
  
  public ResultPage() {
  }
  
  public ResultPage(Page page, int total, List<T> result) {
    this.setOffset(page.getOffset());
    this.setLimit(page.getLimit());
    this.total = total;
    this.result = result;
  }
  
  public ResultPage(Page page, List<T> result, Supplier<Integer> count) {
    this.setOffset(page.getOffset());
    this.setLimit(page.getLimit());
    this.total = result.size() == page.getLimit() ? count.get() : page.getOffset() + result.size();
    this.result = result;
  }

  public static <T>  ResultPage<T> empty(){
    return new ResultPage<T>(new Page(0,10), 0, Collections.emptyList());
  }

  @Nullable
  public int getTotal() {
    return total;
  }
  
  public List<T> getResult() {
    return result;
  }
  
  /**
   * @return true if this is the last page and there are no more pages with content if the offset is
   * increased.
   */
  public boolean isLast() {
    return total <= getOffset() + getLimit();
  }
  
  public int size() {
    return result == null ? 0 : result.size();
  }
  
  public boolean isEmpty() {
    return result == null || result.isEmpty();
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ResultPage<?> other = (ResultPage<?>) o;
    return total == other.total && Objects.equals(result, other.result);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(total, result);
  }
  
  @Override
  public Iterator<T> iterator() {
    return result.iterator();
  }
}

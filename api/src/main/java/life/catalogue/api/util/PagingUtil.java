package life.catalogue.api.util;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;

import java.util.Iterator;
import java.util.function.Function;

public class PagingUtil {
  
  public static <T> Iterator<T> pageAll(Function<Page, ResultPage<T>> func) {
    return new ResultPageIterator<T>(func);
  }

  public static <T> Iterator<T> pageAll(Function<Page, ResultPage<T>> func, int pageSize) {
    return new ResultPageIterator<T>(func, pageSize);
  }

  static class ResultPageIterator<T> implements Iterator<T> {
    private final Page page;
    private final Function<Page, ResultPage<T>> func;
    private ResultPage<T> result = null;
    private Iterator<T> curr = null;
    
    ResultPageIterator(Function<Page, ResultPage<T>> func) {
      this(func, Page.DEFAULT_LIMIT);
    }

    ResultPageIterator(Function<Page, ResultPage<T>> func, int pageSize) {
      this.func = func;
      this.page = new Page(pageSize);
      nextPage();
    }

    private void nextPage() {
      if (result == null || !result.isLast()) {
        result = func.apply(page);
        curr = result.getResult().iterator();
        page.next();
      } else {
        curr = null;
      }
    }
    
    @Override
    public boolean hasNext() {
      return curr != null && (curr.hasNext() || !result.isLast());
    }
    
    @Override
    public T next() {
      if (!curr.hasNext()) {
        nextPage();
      }
      return curr.next();
    }
  }
}

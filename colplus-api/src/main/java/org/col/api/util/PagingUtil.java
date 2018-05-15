package org.col.api.util;

import java.util.Iterator;
import java.util.function.Function;

import org.col.api.model.Page;
import org.col.api.model.ResultPage;

public class PagingUtil {

  public static <T> Iterator<T> pageAll(Function<Page, ResultPage<T>> func) {
    return new ResultPageIterator<T>(func);
  }

  static class ResultPageIterator<T> implements Iterator<T> {
    private final Page page = new Page();
    private final Function<Page, ResultPage<T>> func;
    private ResultPage<T> result = null;
    private Iterator<T> curr = null;

    ResultPageIterator(Function<Page, ResultPage<T>> func) {
      this.func = func;
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

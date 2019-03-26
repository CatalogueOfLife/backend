package org.col.dao;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.search.DatasetSearchRequest;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterator over entities from paging responses that optionally filters out deleted entities.
 */
public class Pager<T> implements Iterable<T> {
  private static final Logger LOG = LoggerFactory.getLogger(Pager.class);
  private final int pageSize;
  private final Function<Page, List<T>> nextPageFunc;
  
  public static Iterable<Dataset> datasets(final SqlSessionFactory factory) {
    return datasets(factory, null);
  }
  
  public static Iterable<Dataset> datasets(final SqlSessionFactory factory, Integer contributesTo) {
    final DatasetSearchRequest req;
    if (contributesTo != null) {
      req = new DatasetSearchRequest();
      req.setContributesTo(contributesTo);
    } else {
      req = null;
    }
    
    return new Pager<Dataset>(100, new Function<Page, List<Dataset>>() {
      @Override
      public List<Dataset> apply(Page page) {
        try (SqlSession session = factory.openSession()) {
          if (req != null) {
            return session.getMapper(DatasetMapper.class).search(req, page);
          }
          return session.getMapper(DatasetMapper.class).list(page);
        }
      }
    });
  }
  
  /**
   * @param pageSize to use when talking to the registry
   */
  private Pager(int pageSize, Function<Page, List<T>> nextPageFunc) {
    Preconditions.checkArgument(pageSize > 0, "pageSize must at least be 1");
    this.pageSize = pageSize;
    this.nextPageFunc = nextPageFunc;
  }
  
  class PageResultIterator implements Iterator<T> {
    private final Page page = new Page(0, pageSize);
    private boolean hasMore = true;
    private Iterator<T> iter;
    private T next;
    
    public PageResultIterator() {
      loadPage();
      next = nextObj();
    }
    
    @Override
    public boolean hasNext() {
      return next != null;
    }
    
    @Override
    public T next() {
      T entity = next;
      next = nextObj();
      return entity;
    }
    
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
    
    private T nextObj() {
      while (true) {
        if (!iter.hasNext()) {
          if (hasMore) {
            loadPage();
          } else {
            // no more records to load, stop!
            return null;
          }
        }
        T obj = iter.next();
        return obj;
      }
    }
    
    private void loadPage() {
      LOG.debug("Loading page {}-{}", page.getOffset(), page.getOffset() + page.getLimit());
      List<T> resp = nextPageFunc.apply(page);
      iter = resp.iterator();
      hasMore = resp.size() == pageSize;
      page.next();
    }
  }
  
  @Override
  public Iterator<T> iterator() {
    return new PageResultIterator();
  }
  
}

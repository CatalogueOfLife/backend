package org.col.dao;

import com.google.common.base.Preconditions;
import org.col.api.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * Iterator over entities from paging responses that optionally filters out deleted entities.
 */
public class Pager<T> implements Iterable<T> {
    private static final Logger LOG = LoggerFactory.getLogger(Pager.class);
    private final int pageSize;
  private final Function<Page, List<T>> nextPageFunc;

    /**
     * @param pageSize to use when talking to the registry
     */
    public Pager(int pageSize, Function<Page, List<T>> nextPageFunc) {
        Preconditions.checkArgument(pageSize > 0, "pageSize must at least be 1");
        this.pageSize = pageSize;
        this.nextPageFunc = nextPageFunc;
    }

    class ResponseIterator implements Iterator<T>{
        private final Page page = new Page(0, pageSize);
        private boolean hasMore = true;
        private Iterator<T> iter;
        private T next;

        public ResponseIterator() {
            loadPage();
            next = nextEntity();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            T entity = next;
            next = nextEntity();
            return entity;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private T nextEntity() {
            while (true) {
                if (!iter.hasNext()) {
                    if (hasMore) {
                        loadPage();
                    } else {
                        // no more records to load, stop!
                        return null;
                    }
                }
                T entity = iter.next();
                if (!exclude(entity)) {
                    return entity;
                }
            }
        }

        private void loadPage() {
            LOG.debug("Loading page {}-{}", page.getOffset(), page.getOffset()+page.getLimit());
            List<T> resp = nextPageFunc.apply(page);
            iter = resp.iterator();
            hasMore = resp.size() == pageSize;
            page.next();
        }
    }

    /**
     * Override this method to implement other exclusion filters.
     */
    protected boolean exclude(T entity) {
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return new ResponseIterator();
    }

}

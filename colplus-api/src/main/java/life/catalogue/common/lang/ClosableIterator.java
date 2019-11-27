package life.catalogue.common.lang;

import java.util.Iterator;

/**
 * An iterator that needs to be explicitly closed when it is not used anymore.
 */
public interface ClosableIterator<T> extends Iterator<T>, AutoCloseable {

}

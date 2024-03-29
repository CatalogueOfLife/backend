package life.catalogue.parser;

import java.util.Optional;

/**
 * Generic parsing interface for a specific type.
 * Implementations should not throw any RuntimeExceptions but wrap them always in an UnparsableException
 */
public interface Parser<T> {
  
  Optional<? extends T> parse(String value) throws UnparsableException;
  
}

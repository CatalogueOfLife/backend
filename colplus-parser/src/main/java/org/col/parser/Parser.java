package org.col.parser;

import java.util.Optional;

/**
 * Generic parsing interface for a specific type.
 * Implementations should not throw any RuntimeExceptions but wrap them always in an UnparsableException
 */
public interface Parser<T> {

  Optional<T> parse(String value) throws UnparsableException;

}

package org.col.parser;

import java.util.Optional;

/**
 *
 */
public interface Parser<T> {

  Optional<T> parse(String value) throws UnparsableException;

}

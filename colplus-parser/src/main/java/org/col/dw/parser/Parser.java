package org.col.dw.parser;

import java.util.Optional;

/**
 *
 */
public interface Parser<T> {

  Optional<T> parse(String value) throws UnparsableException;

}

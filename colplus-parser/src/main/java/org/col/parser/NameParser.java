package org.col.parser;

import org.col.api.Name;
import org.col.api.vocab.Rank;

import java.util.Optional;

/**
 *
 */
public interface NameParser extends Parser<Name> {

  Optional<Name> parse(String value, Rank rank) throws UnparsableException;

}

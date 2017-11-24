package org.col.parser;

import org.col.api.Name;

/**
 * Name parser that throws UnparsableException only in unexpected cases.
 * Virus names, hybrid formulas and clearly no names are returned as Name instances with
 * the appropriate type and verbatim string as scientificName.
 */
public interface NameParser extends Parser<Name> {

}

package org.col.parser;

import org.col.api.vocab.NomRelType;

/**
 *
 */
public class NomRelTypeParser extends EnumParser<NomRelType> {
  public static final NomRelTypeParser PARSER = new NomRelTypeParser();

  public NomRelTypeParser() {
    super("nomreltype.csv", NomRelType.class);
  }

}

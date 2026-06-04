package life.catalogue.db.type2;

import life.catalogue.db.type.BaseEnumSetTypeHandler;

import org.gbif.nameparser.api.NamePart;

/**
 * Maps a {@code NAMEPART[]} postgres column to a {@code Set<NamePart>}.
 */
public class NamePartSetTypeHandler extends BaseEnumSetTypeHandler<NamePart> {

  public NamePartSetTypeHandler() {
    super(NamePart.class, true);
  }

}

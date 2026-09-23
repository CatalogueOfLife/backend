package life.catalogue.matching.person;

import org.gbif.nameparser.api.NomCode;

import javax.annotation.Nullable;

/**
 * The names a form is used in, with the meaning the codes have in authormap.txt: a botanical standard form such as
 * "Sw." (Swartz) is no citation of anybody in zoology.
 */
public enum FormCode {
  BOT, ZOO, ANY;

  /**
   * Names of the zoological code use ZOO and ANY forms, all others BOT and ANY, as the author map does.
   */
  public boolean appliesTo(@Nullable NomCode code) {
    return this == ANY || (code == NomCode.ZOOLOGICAL ? this == ZOO : this == BOT);
  }
}

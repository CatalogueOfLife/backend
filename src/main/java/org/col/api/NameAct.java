package org.col.api;

import org.col.api.vocab.NomenclaturalActType;
import org.col.api.vocab.NomenclaturalStatus;

/**
 * A nomenclatural act such as a species description, type designation or conservation of a name.
 */
public class NameAct {
  private Integer key;
  private NomenclaturalActType type;

  /**
   * The new status established through this act.
   */
  private NomenclaturalStatus status;

  private Name name;
  private Name relatedName;
  private Reference reference;

}

package life.catalogue.api.model;

import life.catalogue.api.vocab.Users;

import javax.annotation.Nullable;

public interface ExtensionEntity extends SectorScopedEntity<Integer>, Referenced, VerbatimEntity, VerbatimSourceEntity, Remarkable {

  @Nullable
  @Override
  default Boolean isMerged() {
    if (getCreatedBy() != null && getCreatedBy() == Users.HOMOTYPIC_GROUPER) {
      return true;
    }
    return SectorScopedEntity.super.isMerged();
  }

}

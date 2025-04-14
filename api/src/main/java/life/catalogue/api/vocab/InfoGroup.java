package life.catalogue.api.vocab;

/**
 * Information group usually as smaller units of an EntityType.
 * Some associated entities to usages are also marked like the holotype.
 */
public enum InfoGroup {
  AUTHORSHIP(EntityType.NAME),
  PUBLISHED_IN(EntityType.NAME),
  PARENT(EntityType.NAME_USAGE),
  BASIONYM(EntityType.NAME),
  EXTINCT(EntityType.NAME_USAGE),
  TEMPORAL_RANGE(EntityType.NAME_USAGE),
  RANK(EntityType.NAME),
  HOLOTYPE(EntityType.NAME);

  private final EntityType entity;

  InfoGroup(EntityType entity) {
    this.entity = entity;
  }

  public EntityType getEntity() {
    return entity;
  }
}

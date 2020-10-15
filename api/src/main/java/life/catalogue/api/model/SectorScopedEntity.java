package life.catalogue.api.model;

public interface SectorScopedEntity<K> extends DSID<K>, SectorEntity, Entity<DSID<K>>, UserManaged {
}

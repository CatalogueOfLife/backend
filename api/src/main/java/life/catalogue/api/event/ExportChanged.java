package life.catalogue.api.event;

import life.catalogue.api.model.DatasetExport;

import java.util.UUID;

public class ExportChanged extends EntityChanged<UUID, DatasetExport> {

  private ExportChanged(EventType type, UUID key, DatasetExport obj, DatasetExport old) {
    super(type, key, obj, old, DatasetExport.class);
  }

  public static ExportChanged deleted(DatasetExport export){
    return new ExportChanged(EventType.DELETE, export.getKey(), null, export);
  }
}

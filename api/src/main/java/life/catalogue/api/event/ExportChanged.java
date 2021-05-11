package life.catalogue.api.event;

import life.catalogue.api.model.DatasetExport;

import java.util.UUID;

public class ExportChanged extends EntityChanged<UUID, DatasetExport> {

  private ExportChanged(UUID key, boolean created, DatasetExport obj, DatasetExport old) {
    super(key, obj, old, created, DatasetExport.class);
  }

  public static ExportChanged delete(DatasetExport export){
    return new ExportChanged(export.getKey(), false, null, export);
  }
}

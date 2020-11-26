package life.catalogue.exporter;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.VernacularName;
import life.catalogue.common.concurrent.JobPriority;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;

public class DwcaExporter extends ArchiveExporter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaExporter.class);

  DwcaExporter(File archive, int datasetKey, @Nullable String startID, @Nullable Rank lowestRank, boolean synonyms, Set<String> exclusions, int userKey, JobPriority priority, SqlSessionFactory factory) {
    super(archive, datasetKey, startID, lowestRank, synonyms, exclusions, userKey, priority, factory);
  }

  public static DwcaExporter dataset(int datasetKey, int userKey) {
    return null; //new DwcaExporter(datasetKey, userKey, JobPriority.MEDIUM);
  }


  void write(NameUsageBase u) {

  }

  void write(String id, VernacularName vn) {

  }

}

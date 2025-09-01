package life.catalogue.dw.tasks;

import io.dropwizard.servlets.tasks.Task;

import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.auth.AuthBundle;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class DeleteTmpDatasetsTask extends Task {
  private final DatasetDao dao;

  public DeleteTmpDatasetsTask(DatasetDao dao) {
    super("del-tmp-datasets");
    this.dao = dao;
  }

  @Override
  public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
    int num = dao.deleteTempDatasets();
    output.println("Deleted "+num+" temporary datasets.");
    output.flush();
  }
}
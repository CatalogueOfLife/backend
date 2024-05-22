package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.config.ReleaseConfig;

import life.catalogue.matching.NameIndex;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * ID generator for the extended release based on the IdProvider that looks at
 * previous releases and the deleted usage archive to know about historical identifiers that can be reused.
 */
public class XIdProvider extends IdProvider implements UsageIdGen, AutoCloseable {
  private final NameIndex nidx;
  private final Writer nomatchWriter;

  public XIdProvider(int projectKey, int attempt, int releaseDatasetKey, ReleaseConfig cfg, NameIndex nidx, SqlSessionFactory factory) throws IOException {
    super(projectKey, DatasetOrigin.XRELEASE, attempt, releaseDatasetKey, cfg, factory);
    this.nidx = nidx;
    nomatchWriter = buildNomatchWriter();
  }

  @Override
  public String issue(SimpleNameWithNidx usage) {
    try {
      issueIDs(usage.getNamesIndexId(), List.of(usage), nomatchWriter, false);
      return encode(usage.getCanonicalId());

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Integer nidx2canonical(Integer nidx) {
    return this.nidx.getCanonical(nidx);
  }

  @Override
  public void close() throws IOException {
    nomatchWriter.close();
  }
}

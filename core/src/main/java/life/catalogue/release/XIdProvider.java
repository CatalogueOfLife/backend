package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.config.ReleaseConfig;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * ID generator for the extended release based on the IdProvider that looks at
 * previous releases and the deleted usage archive to know about historical identifiers that can be reused.
 */
public class XIdProvider extends IdProvider implements UsageIdGen, AutoCloseable {
  private final Writer nomatchWriter;

  public XIdProvider(int projectKey, int mappedDatasetKey, int attempt, int releaseDatasetKey, ReleaseConfig cfg, ProjectReleaseConfig prCfg,
                     SqlSessionFactory factory
  ) throws IOException {
    super(projectKey, mappedDatasetKey, DatasetOrigin.XRELEASE, attempt, releaseDatasetKey, cfg, prCfg, factory);
    nomatchWriter = buildNomatchWriter();
  }

  @Override
  public String issue(SimpleNameWithNidx usage) {
    // OTU names (UNITE/BOLD) use their code verbatim as the stable id (see IdProvider.otuId)
    final String otu = otuId(usage);
    if (otu != null) {
      return otu;
    }
    try {
      if (usage.hasAuthorship()) {
        // remember real canonical ID as we use the property to encode the new id internally
        final var canonID = usage.getCanonicalId();
        issueIDs(usage.getCanonicalId(), List.of(usage), nomatchWriter, false);
        var id = encode(usage.getCanonicalId());
        usage.setCanonicalId(canonID);
        return id;
      } else {
        // for new canonical names we issue a temp id for now, so we can update the authorship later
        // and assign new ids without wasting stable IDs

        return ShortUUID.ID_GEN.get();
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Integer nidx2canonical(Integer nidx) {
    // single-tier canonical-only index: a nidx is its own canonical, so no lookup is needed
    return nidx;
  }

  @Override
  public void close() throws IOException {
    nomatchWriter.close();
  }
}

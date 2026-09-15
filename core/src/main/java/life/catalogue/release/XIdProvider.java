package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.config.ReleaseConfig;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * ID generator for the extended release based on the IdProvider that looks at
 * previous releases and the deleted usage archive to know about historical identifiers that can be reused.
 *
 * Merged usages only ever get a temporary id here. The stable ids are issued by a single
 * {@link IdProvider#mapTempIds()} pass that {@link XRelease} runs once all sectors are merged and the tree has been
 * cleaned, see XRelease#mapTmpIDs. Issuing per usage during the merge would score each usage against the archive on
 * its own, so the first usage of a canonical group to be merged took the best released id and a later, better fitting
 * one got whatever was left - making the assignment depend on the order sectors happen to be merged in. The batch pass
 * sees the whole canonical group at once, and it runs after the orphan removal, so no stable id is burnt on a usage
 * that is dropped again in the same run.
 */
public class XIdProvider extends IdProvider implements UsageIdGen {

  public XIdProvider(int projectKey, int mappedDatasetKey, int attempt, int releaseDatasetKey, ReleaseConfig cfg, ProjectReleaseConfig prCfg,
                     SqlSessionFactory factory
  ) {
    super(projectKey, mappedDatasetKey, DatasetOrigin.XRELEASE, attempt, releaseDatasetKey, cfg, prCfg, factory);
  }

  @Override
  public String issue(SimpleNameWithNidx usage) {
    // OTU names (UNITE/BOLD) use their code verbatim as the stable id (see IdProvider.otuId)
    final String otu = otuId(usage);
    return otu != null ? otu : ShortUUID.ID_GEN.get();
  }

  @Override
  public Integer nidx2canonical(Integer nidx) {
    // single-tier canonical-only index: a nidx is its own canonical, so no lookup is needed
    return nidx;
  }

}

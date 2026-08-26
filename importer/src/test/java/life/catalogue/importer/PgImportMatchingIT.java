package life.catalogue.importer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Identifier;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.config.MatchingConfig;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross dataset matching during an import, i.e. {@link Setting#ADD_IDENTIFIERS_FROM}.
 */
public class PgImportMatchingIT extends PgImportITBase {

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1565
   *
   * The target holds two Ricinus genus homonyms, a plant and a chewing louse. The source carries the bare
   * canonical name without authorship, so the two homonyms can only be told apart by the classification -
   * which places the source name in Plantae. The match must therefore not be ambiguous.
   */
  @Test
  public void ambiguousGenusResolvedByClassification() throws Exception {
    // the match target with both Ricinus homonyms
    normalizeAndImport(DataFormat.TEXT_TREE, 6);
    final int targetKey = dataset.getKey();

    // the source, matched against the target above
    dataset = newDataset();
    dataset.put(Setting.ADD_IDENTIFIERS_FROM, String.valueOf(targetKey));
    matcherFactory = postgresMatcherFactory();
    normalizeAndImport(DataFormat.TEXT_TREE, 7);
    final int sourceKey = dataset.getKey();

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      vMapper = session.getMapper(VerbatimRecordMapper.class);
      var tm = session.getMapper(TaxonMapper.class);

      var ricinus = tm.get(DSID.of(sourceKey, "gen"));
      assertNoIssue(ricinus, Issue.MATCHING_AMBIGUOUS);
      assertEquals(List.of(new Identifier("clb" + targetKey, "ricinus-plant")), ricinus.getIdentifier());
    }
  }

  /**
   * A factory handing out matchers reading the target dataset live from postgres,
   * so tests do not need to build a persistent matcher store on disk.
   */
  private UsageMatcherFactory postgresMatcherFactory() {
    var ni = NameIndexFactory.build(NamesIndexConfig.memory(1024), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE).started();
    return new UsageMatcherFactory(new MatchingConfig(), ni, SqlSessionFactoryRule.getSqlSessionFactory(), null) {
      @Override
      public UsageMatcher get(int datasetKey) {
        return postgres(datasetKey);
      }
    };
  }
}

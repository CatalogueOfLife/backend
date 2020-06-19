package life.catalogue.es;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import life.catalogue.es.ddl.MappingFactoryTest;
import life.catalogue.es.ddl.MappingUtilTest;
import life.catalogue.es.nu.ClassificationUpdaterTest;
import life.catalogue.es.nu.EsNameUsageSerde;
import life.catalogue.es.nu.Issue333;
import life.catalogue.es.nu.MultiValuedMapTest;
import life.catalogue.es.nu.NameUsageIndexServiceIT;
import life.catalogue.es.nu.NameUsageResponseConverterTest;
import life.catalogue.es.nu.NameUsageWrapperConverterTest;
import life.catalogue.es.nu.QTranslationUtilsTest;
import life.catalogue.es.nu.search.*;
import life.catalogue.es.nu.suggest.NameUsageSuggestionServiceTest;
import life.catalogue.es.query.CollapsibleListTest;
import life.catalogue.es.query.PrefixQueryTest;
import life.catalogue.es.query.QueryTest;
import life.catalogue.es.query.RangeQueryTest;
import life.catalogue.es.query.TermQueryTest;

@RunWith(Suite.class)
@SuiteClasses({
    ClassificationUpdaterTest.class,
    CollapsibleListTest.class,
    DecisionQueriesTest.class,
    EsClientFactoryTest.class,
    EsNameUsageSerde.class,
    EsUtilTest.class,
    FacetsTranslatorTest.class,
    Issue333.class,
    MappingFactoryTest.class,
    MappingUtilTest.class,
    MinRankMaxRankTest.class,
    Misc.class,
    MultiValuedMapTest.class,
    NameUsageSearchHighlighterTest.class,
    NameUsageResponseConverterTest.class,
    NameUsageSearchHighlighterTest.class,
    NameUsageSearchParameterTest.class,
    NameUsageIndexServiceIT.class,
    NameUsageResponseConverterTest.class,
    NameUsageSearchServiceTest.class,
    NameUsageSearchServiceFacetTest.class,
    NameUsageSuggestionServiceTest.class,
    NameUsageWrapperConverterTest.class,
    PrefixQueryTest.class,
    QSearchTests.class,
    QTranslationUtilsTest.class,
    QueryTest.class,
    RangeQueryTest.class,
    RequestTranslatorTest.class,
    SortingTest.class,
    TermQueryTest.class
})
// @SuppressWarnings("unused")
public class EsTestSuite {

}

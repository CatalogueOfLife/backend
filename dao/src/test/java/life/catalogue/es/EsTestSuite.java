package life.catalogue.es;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import life.catalogue.es.mapping.MappingFactoryTest;
import life.catalogue.es.nu.ClassificationUpdaterTest;
import life.catalogue.es.nu.EsNameUsageSerde;
import life.catalogue.es.nu.Issue333;
import life.catalogue.es.nu.MultiValuedMapTest;
import life.catalogue.es.nu.NameUsageIndexServiceIT;
import life.catalogue.es.nu.NameUsageResponseConverterTest;
import life.catalogue.es.nu.NameUsageWrapperConverterTest;
import life.catalogue.es.nu.search.DecisionQueriesTest;
import life.catalogue.es.nu.search.FacetsTranslatorTest;
import life.catalogue.es.nu.search.NameSearchHighlighterTest;
import life.catalogue.es.nu.search.NameSearchServiceFacetTest;
import life.catalogue.es.nu.search.NameSearchServiceTest;
import life.catalogue.es.nu.search.NameSearchTestAllParamsTest;
import life.catalogue.es.nu.search.QSearchTests;
import life.catalogue.es.nu.search.SortingTest;
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
    Misc.class,
    MultiValuedMapTest.class,
    NameSearchHighlighterTest.class,
    NameUsageResponseConverterTest.class,
    NameSearchHighlighterTest.class,
    NameSearchTestAllParamsTest.class,
    NameUsageIndexServiceIT.class,
    NameSearchServiceTest.class,
    NameSearchServiceFacetTest.class,
    NameUsageSuggestionServiceTest.class,
    NameUsageWrapperConverterTest.class,
    PrefixQueryTest.class,
    QSearchTests.class,
    QueryTest.class,
    RangeQueryTest.class,
    SortingTest.class,
    TermQueryTest.class
})
// @SuppressWarnings("unused")
public class EsTestSuite {

}

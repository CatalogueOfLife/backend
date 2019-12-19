package life.catalogue.es;

import life.catalogue.es.mapping.MappingFactoryTest;
import life.catalogue.es.name.EsNameUsageSerde;
import life.catalogue.es.name.Issue333;
import life.catalogue.es.name.MultiValuedMapTest;
import life.catalogue.es.name.NameUsageIndexServiceIT;
import life.catalogue.es.name.NameUsageResponseConverterTest;
import life.catalogue.es.name.NameUsageWrapperConverterTest;
import life.catalogue.es.name.index.ClassificationUpdaterTest;
import life.catalogue.es.name.search.DecisionQueriesTest;
import life.catalogue.es.name.search.FacetsTranslatorTest;
import life.catalogue.es.name.search.NameSearchHighlighterTest;
import life.catalogue.es.name.search.NameSearchServiceFacetTest;
import life.catalogue.es.name.search.NameSearchTestAllParamsTest;
import life.catalogue.es.name.search.NameSearchServiceTest;
import life.catalogue.es.name.search.QSearchTests;
import life.catalogue.es.name.search.RequestTranslatorTest;
import life.catalogue.es.name.search.SortingTest;
import life.catalogue.es.name.suggest.NameUsageSuggestionServiceTest;
import life.catalogue.es.query.CollapsibleListTest;
import life.catalogue.es.query.PrefixQueryTest;
import life.catalogue.es.query.QueryTest;
import life.catalogue.es.query.RangeQueryTest;
import life.catalogue.es.query.TermQueryTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

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
    RequestTranslatorTest.class,
    SortingTest.class,
    TermQueryTest.class
})
// @SuppressWarnings("unused")
public class EsTestSuite {

}

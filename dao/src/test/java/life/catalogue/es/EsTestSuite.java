package life.catalogue.es;

import life.catalogue.es.ddl.MappingFactoryTest;
import life.catalogue.es.ddl.MappingUtilTest;
import life.catalogue.es.nu.*;
import life.catalogue.es.nu.search.*;
import life.catalogue.es.nu.suggest.NameUsageSuggestionServiceEsTest;
import life.catalogue.es.query.*;

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
    Issue541_SearchForUnparsedNames.class,
    MappingFactoryTest.class,
    MappingUtilTest.class,
    MinRankMaxRankTest.class,
    Misc.class,
    MultiValuedMapTest.class,
    NameUsageFieldLookupTest.class,
    NameUsageSearchHighlighterTest.class,
    NameUsageResponseConverterTest.class,
    NameUsageSearchHighlighterTest.class,
    NameUsageSearchParameterTest.class,
    NameUsageIndexServiceIT.class,
    NameUsageResponseConverterTest.class,
    NameUsageSearchServiceTest.class,
    NameUsageSearchServiceFacetTest.class,
    NameUsageSuggestionServiceEsTest.class,
    NameUsageWrapperConverterTest.class,
    PrefixQueryTest.class,
    QSearchTests.class,
    QMatchingTest.class,
    QueryTest.class,
    RangeQueryTest.class,
    RequestTranslatorTest.class,
    SortingTest.class,
    TermQueryTest.class
})
public class EsTestSuite {

}

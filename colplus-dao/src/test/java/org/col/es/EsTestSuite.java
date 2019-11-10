package org.col.es;

import org.col.es.mapping.MappingFactoryTest;
import org.col.es.name.EsNameUsageSerde;
import org.col.es.name.Issue333;
import org.col.es.name.MultiValuedMapTest;
import org.col.es.name.NameUsageIndexServiceIT;
import org.col.es.name.NameUsageResponseConverterTest;
import org.col.es.name.NameUsageWrapperConverterTest;
import org.col.es.name.index.ClassificationUpdaterTest;
import org.col.es.name.search.FacetsTranslatorTest;
import org.col.es.name.search.NameSearchHighlighterTest;
import org.col.es.name.search.NameSearchServiceFacetTest;
import org.col.es.name.search.NameSearchTestAllParamsTest;
import org.col.es.name.search.NameSearchServiceTest;
import org.col.es.name.search.QSearchTests;
import org.col.es.name.search.RequestTranslatorTest;
import org.col.es.name.search.SortingTest;
import org.col.es.name.suggest.NameUsageSuggestionServiceTest;
import org.col.es.query.CollapsibleListTest;
import org.col.es.query.PrefixQueryTest;
import org.col.es.query.QueryTest;
import org.col.es.query.RangeQueryTest;
import org.col.es.query.TermQueryTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    ClassificationUpdaterTest.class,
    CollapsibleListTest.class,
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

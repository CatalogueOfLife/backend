package org.col.es;

import org.col.es.ddl.MappingFactoryTest;
import org.col.es.name.EsNameUsageSerde;
import org.col.es.name.Issue333;
import org.col.es.name.MultiValuedMapTest;
import org.col.es.name.NameUsageIndexServiceIT;
import org.col.es.name.NameUsageResponseConverterTest;
import org.col.es.name.NameUsageWrapperConverterTest;
import org.col.es.name.index.ClassificationUpdaterTest;
import org.col.es.name.search.FacetsTranslatorTest;
import org.col.es.name.search.NameSearchHighlighterTest;
import org.col.es.name.search.NameSearchTestAllParamsTest;
import org.col.es.name.search.NameUsageSearchServiceTest;
import org.col.es.name.search.RequestTranslatorTest;
import org.col.es.name.search.SortingTest;
import org.col.es.query.CollapsibleListTest;
import org.col.es.query.PrefixQueryTest;
import org.col.es.query.QueryTest;
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
    MultiValuedMapTest.class,
    NameSearchHighlighterTest.class,
    NameUsageResponseConverterTest.class,
    NameSearchTestAllParamsTest.class,
    NameUsageIndexServiceIT.class,
    NameUsageSearchServiceTest.class,
    // NameUsageSearchServiceFacetTest.class,
    NameUsageWrapperConverterTest.class,
    PrefixQueryTest.class,
    QueryTest.class,
    RequestTranslatorTest.class,
    SortingTest.class,
    TermQueryTest.class
})
@SuppressWarnings("unused") // Allow for manual exclusiongs without getting unused import warnings
public class EsTestSuite {

}

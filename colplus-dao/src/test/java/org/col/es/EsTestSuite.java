package org.col.es;

import org.col.es.ddl.MappingFactoryTest;
import org.col.es.name.EsNameUsageSerde;
import org.col.es.name.Issue333;
import org.col.es.name.MultiValuedMapTest;
import org.col.es.name.NameSearchResponseTransferTest;
import org.col.es.name.NameUsageIndexServiceIT;
import org.col.es.name.NameUsageSearchServiceFacetTest;
import org.col.es.name.NameUsageTransferTest;
import org.col.es.name.index.ClassificationUpdaterTest;
import org.col.es.name.search.FacetsTranslatorTest;
import org.col.es.name.search.NameSearchHighlighterTest;
import org.col.es.name.search.NameSearchTestAllParamsTest;
import org.col.es.name.search.NameUsageSearchServiceTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    ClassificationUpdaterTest.class,
    EsClientFactoryTest.class,
    EsNameUsageSerde.class,
    EsUtilTest.class,
    FacetsTranslatorTest.class,
    Issue333.class,
    MappingFactoryTest.class,
    MultiValuedMapTest.class,
    NameSearchHighlighterTest.class,
    NameSearchResponseTransferTest.class,
    NameSearchTestAllParamsTest.class,
    NameUsageIndexServiceIT.class,
    NameUsageSearchServiceTest.class,
    // NameUsageSearchServiceFacetTest.class,
    NameUsageTransferTest.class,
})
@SuppressWarnings("unused") // Allow for manual exclusiongs without getting unused import warnings
public class EsTestSuite {

}

package life.catalogue.importer;

import com.google.common.io.Files;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.img.ImageService;
import life.catalogue.importer.store.ImportStoreFactory;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.NameUsageData;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.parser.NameParser;
import org.apache.commons.io.FileUtils;
import org.gbif.nameparser.api.Rank;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NormalizerTest {

    @Test
    void findParentCycle() throws InterruptedException {
        var cfg = new NormalizerConfig();
        cfg.archiveDir = Files.createTempDir();
        cfg.scratchDir = Files.createTempDir();
        final int attempt = 1;
        try {
            var isf = new ImportStoreFactory(cfg);
            DatasetWithSettings d = new DatasetWithSettings();
            d.setKey(1);
            d.setDataFormat(DataFormat.DWCA);
            Path sourceDir = null;
            var nidx = NameIndexFactory.passThru();
            var store = isf.create(1, attempt);
            var norm = new Normalizer(d, store, sourceDir, nidx, ImageService.passThru(), Validation.buildDefaultValidatorFactory().getValidator(), null);

            int idGen = 1;
            for (final int size : List.of(1, 2, 3, 10)) {
                int x = size;
                UsageData first = null;
                UsageData last = null;
                UsageData ud = null;
                while (x > 0) {
                    ud = build(idGen++);
                    x--;
                    if (last != null) {
                        ud.usage.asUsageBase().setParentId(last.getId());
                    } else {
                        first = ud;
                    }
                    var nd = new NameData(ud.usage.getName());
                    var nud = new NameUsageData(nd, ud);
                    store.createNameAndUsage(nud);
                    last = ud;
                }
                first.usage.asUsageBase().setParentId(last.getId());
                store.usages().update(first);

                var cycle = norm.findParentCycle(ud, new HashSet<>());
                assertNotNull(cycle);
                assertEquals(size, cycle.size());
            }

        } finally {
            FileUtils.deleteQuietly(cfg.scratchDir);
        }
    }

    /**
     * https://github.com/CatalogueOfLife/backend/issues/1662
     */
    @Test
    void isPlausibleSpellingCorrection() throws Exception {
        assertPlausible("Coryphaena hippurus Linnaeus, 1758", "Coriphaena hippurus Linnaeus, 1758", true);
        assertPlausible("Coryphaena scomberoides Valenciennes, 1833", "Coryphaena scomberoïdes Valenciennes, 1833", true);
        assertPlausible("Bembidion pallideguttula Jensen-Haarup, 1910", "Bembidium pallideguttula Jensen-Haarup, 1910", true);
        assertPlausible("Aus smithi Miller", "Aus smithii Miller", true);
        assertPlausible("Trachelosiphon colombianum Schltr.", "Trachelosiphon columbianum Schltr.", true);
        // WoRMS relating an accepted name to the misspelling of one of its synonyms
        assertPlausible("Coryphaena hippurus Linnaeus, 1758", "Coryphaena scomberoïdes Valenciennes, 1833", false);
        assertPlausible("Coryphaena hippurus Linnaeus, 1758", "Coryphaena equiselis Linnaeus, 1758", false);
    }

    static void assertPlausible(String n1, String n2, boolean expected) throws Exception {
        var name1 = NameParser.PARSER.parse(n1, Rank.SPECIES, null, VerbatimRecord.VOID).get().getName();
        var name2 = NameParser.PARSER.parse(n2, Rank.SPECIES, null, VerbatimRecord.VOID).get().getName();
        assertEquals(expected, Normalizer.isPlausibleSpellingCorrection(name1, name2), n1 + " vs " + n2);
        assertEquals(expected, Normalizer.isPlausibleSpellingCorrection(name2, name1), n2 + " vs " + n1);
    }

    UsageData build(int id) {
        UsageData ud = UsageData.buildTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);
        var n = new Name();
        ud.usage.setName(n);
        ud.setId("U" + id);
        n.setId("N" + id);
        ud.nameID = n.getId();
        return ud;
    }
}
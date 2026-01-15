package life.catalogue.importer;

import com.google.common.io.Files;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Name;
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
import org.apache.commons.io.FileUtils;
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
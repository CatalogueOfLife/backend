package org.col.admin.task.importer.dwca;

import com.google.common.collect.*;
import org.col.api.model.VerbatimRecord;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwca.record.Record;
import org.gbif.dwca.record.StarRecord;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class VerbatimRecordFactoryTest {
  private final String id = "core1";

  class Rec implements Record {
    private final String id;
    private final Term rowType;

    Rec(String id, Term rowType) {
      this.id = id;
      this.rowType = rowType;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public Term rowType() {
      return null;
    }

    @Override
    public String value(Term term) {
      return "value for " + term.simpleName();
    }

    @Override
    public String column(int index) {
      return "value c"+index;
    }

    @Override
    public Set<Term> terms() {
      return Sets.newHashSet();
    }
  }

  class StarRec implements StarRecord {
    Record core = new Rec(id, DwcTerm.Taxon);
    List<Record> verns = Lists.newArrayList(new Rec(id, GbifTerm.VernacularName));

    @Override
    public Record core() {
      return core;
    }

    @Override
    public boolean hasExtension(Term rowType) {
      return GbifTerm.VernacularName.equals(rowType);
    }

    @Override
    public Map<Term, List<Record>> extensions() {
      return ImmutableMap.of(GbifTerm.VernacularName, verns);
    }

    @Override
    public List<Record> extension(Term rowType) {
      return rowType.equals(GbifTerm.VernacularName) ? verns : Lists.newArrayList();
    }

    @Override
    public Set<Term> rowTypes() {
      return Sets.newHashSet(GbifTerm.VernacularName);
    }

    @Override
    public int size() {
      return 1;
    }

    @NotNull
    @Override
    public Iterator<Record> iterator() {
      return Lists.newArrayList(core).iterator();
    }
  }

  @Test
  public void build() throws Exception {
    StarRecord star = new StarRec();
    VerbatimRecord v = VerbatimRecordFactory.build(star);
    assertEquals(id, v.getId());
    assertEquals(1, v.getExtensionRecords(GbifTerm.VernacularName).size());
    assertTrue(v.getExtensionRecords(GbifTerm.VernacularName).get(0).isEmpty());
  }

}
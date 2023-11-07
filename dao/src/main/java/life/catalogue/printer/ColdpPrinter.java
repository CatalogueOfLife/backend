package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;

public class ColdpPrinter extends RowTermPrinter{
  public ColdpPrinter(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) throws IOException {
    super(params, ranks, countRank, taxonCounter, factory, writer, ColdpTerm.NameUsage, List.of(
      ColdpTerm.ID,
      ColdpTerm.parentID,
      ColdpTerm.status,
      ColdpTerm.rank,
      ColdpTerm.scientificName,
      ColdpTerm.authorship
    ));
  }

  @Override
  void write(SimpleName sn) throws IOException {
    tw.set(ColdpTerm.ID, sn.getId());
    tw.set(ColdpTerm.parentID, sn.getParentId());
    tw.set(ColdpTerm.status, sn.getStatus());
    tw.set(ColdpTerm.rank, sn.getRank());
    tw.set(ColdpTerm.scientificName, sn.getName());
    tw.set(ColdpTerm.authorship, sn.getAuthorship());
  }
}

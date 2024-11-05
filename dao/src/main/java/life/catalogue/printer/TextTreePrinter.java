package life.catalogue.printer;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.dao.TaxonCounter;

import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonExtensionMapper;
import life.catalogue.db.mapper.VernacularNameMapper;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.txtree.Tree;

import javax.annotation.Nullable;

/**
 * Print an entire dataset in the indented text format used by TxtPrinter.
 * Synonyms are prefixed with an asterisk *,
 * Pro parte synoynms with a double asterisk **,
 * basionyms are prefixed by a $ and listed first in the synonymy.
 * <p>
 * Ranks are given in brackets after the scientific name
 * <p>
 * A basic example tree would look like this:
 * <pre>
 * Plantae [kingdom]
 * Compositae Giseke [family]
 * Asteraceae [family]
 * Artemisia L. [genus]
 * Artemisia elatior (Torr. & A. Gray) Rydb.
 * $Artemisia tilesii var. elatior Torr. & A. Gray
 * $Artemisia rupestre Schrank L. [species]
 * Absinthium rupestre (L.) Schrank [species]
 * Absinthium viridifolium var. rupestre (L.) Besser
 * </pre>
 */
public class TextTreePrinter extends AbstractTreePrinter {
  private static final int indentation = 2;
  private boolean showIDs;
  private boolean extended;
  private final DSID<String> key;

  public TextTreePrinter(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct,
                         @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                         SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
    key = DSID.root(params.getDatasetKey());
  }


  public void showIDs() {
    this.showIDs = true;
  }

  public void showExtendedInfos() {
    extended = true;
  }

  protected void start(SimpleName u) throws IOException {
    writer.write(StringUtils.repeat(' ', level * indentation));
    if (u.getStatus() != null && u.getStatus().isSynonym()) {
      writer.write(Tree.SYNONYM_SYMBOL);
    }
    //TODO: flag basionyms
    if (u.isExtinct()) {
      writer.write(Tree.EXTINCT_SYMBOL);
    }
    if (u.getStatus() == TaxonomicStatus.PROVISIONALLY_ACCEPTED) {
      writer.write(Tree.PROVISIONAL_SYMBOL);
    }
    writer.write(u.getName());
    if (u.getAuthorship() != null) {
      writer.write(" ");
      writer.write(u.getAuthorship());
    }
    writer.write(" [");
    Rank r = ObjectUtils.coalesce(u.getRank(), Rank.UNRANKED);
    writer.write(r.name().toLowerCase());
    writer.write("]");

    var infos = infos(u);
    if (!infos.isEmpty()) {
      writer.write(" {");
      writer.write(String.join(" ", infos));
      writer.write("}");
    }

    writer.write('\n');
  }

  protected void end(SimpleName u) {
    //nothing
  }

  private StringBuilder infoBuilder(TxtTreeTerm type) {
    StringBuilder sb = new StringBuilder();
    sb.append(type.name())
      .append("=");
    return sb;
  }

  /**
   * @return list of infos to be appended in brackets after the name
   * @param u
   */
  private List<String> infos(SimpleName u){
    List<String> infos = new ArrayList<>();
    if (showIDs) {
      infos.add("ID=" + u.getId());
    }
    if (extended) {
      key.id(u.getId());
      addInfos(TxtTreeTerm.CODE, u.getCode(), infos);
      var nu = session.getMapper(NameUsageMapper.class).get(key);
      if (nu != null) {
        addInfos(TxtTreeTerm.PUB, escape(nu.getName().getPublishedInId()), infos);
        if (nu.isTaxon()) {
          Taxon t = nu.asTaxon();
          addInfos(TxtTreeTerm.REF, joinStr(t.getReferenceIds()), infos);
          addInfos(TxtTreeTerm.ENV, joinEnum(t.getEnvironments()), infos);
          if (t.getTemporalRangeStart() != null || t.getTemporalRangeEnd() != null) {
            addInfos(TxtTreeTerm.CHRONO, str(t.getTemporalRangeStart()) + "-" + str(t.getTemporalRangeEnd()), infos);
          }
        }
        addInfos(TxtTreeTerm.LINK, nu.getLink(), infos);
        addInfos(TxtTreeTerm.REMARKS, escape(nu.getRemarks()), infos);
      }
      addInfos(TxtTreeTerm.VERN, VernacularNameMapper.class, this::encode, infos);
    }
    if (countRank != null) {
      infos.add("NUM_"+countRank.name() + "=" + taxonCount);
    }
    return infos;
  }
  private static String str(String x) {
    return x == null ? "" : escape(x);
  }

  /**
   * Escapes commas with double commas
   * @param x
   * @return
   */
  private static String escape(String x) {
    return x == null ? null : x.replaceAll(",", ",,");
  }
  private String encode(VernacularName vn) {
    if (vn != null && vn.getName() != null) {
      if (vn.getLanguage() != null) {
        return vn.getLanguage() + ":" + escape(vn.getName().trim());
      }
      return escape(vn.getName().trim());
    }
    return null;
  }

  private void addInfos(TxtTreeTerm type, String value, List<String> infos) {
    if (value != null) {
      StringBuilder sb = infoBuilder(type);
      sb.append(value);
      infos.add(sb.toString());
    }
  }
  private void addInfos(TxtTreeTerm type, Enum value, List<String> infos) {
    addInfos(type, value == null ? null : value.name(), infos);
  }
  private void addInfos(TxtTreeTerm type, Object value, List<String> infos) {
    addInfos(type, value == null ? null : value.toString(), infos);
  }
  private static String joinStr(Collection<String> values) {
    if (values != null && !values.isEmpty()) {
      return String.join(",", values.stream().filter(Objects::nonNull).map(x -> escape(x)).collect(Collectors.toList()));
    }
    return null;
  }
  private static String joinEnum(Collection<? extends Enum<?>> values) {
    if (values != null && !values.isEmpty()) {
      return String.join(",", values.stream().filter(Objects::nonNull).map(Enum::name).collect(Collectors.toList()));
    }
    return null;
  }

  private <T extends ExtensionEntity> void addInfos(TxtTreeTerm type, Class<? extends TaxonExtensionMapper<T>> mapperClass, Function<T, String> encoder, List<String> infos) {
    StringBuilder sb = infoBuilder(type);
    var m = session.getMapper(mapperClass);
    boolean first = true;
    for (var obj : m.listByTaxon(key)) {
      String val = encoder.apply(obj);
      if (val != null) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        sb.append(val);
      }
    }
    if (!first){
      infos.add(sb.toString());
    }
  }

}

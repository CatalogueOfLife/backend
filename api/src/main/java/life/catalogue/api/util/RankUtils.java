package life.catalogue.api.util;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

public class RankUtils {
  public static List<ColdpTerm> CL_TERMS_COLDP = Arrays.asList(ColdpTerm.DENORMALIZED_RANKS);

  public static BiMap<Rank, ColdpTerm> RANK2COLDP = ImmutableBiMap.copyOf(Map.ofEntries(
    Map.entry(Rank.KINGDOM, ColdpTerm.kingdom),
    Map.entry(Rank.PHYLUM, ColdpTerm.phylum),
    Map.entry(Rank.SUBPHYLUM, ColdpTerm.subphylum),
    Map.entry(Rank.CLASS, ColdpTerm.class_),
    Map.entry(Rank.SUBCLASS, ColdpTerm.subclass),
    Map.entry(Rank.ORDER, ColdpTerm.order),
    Map.entry(Rank.SUBORDER, ColdpTerm.suborder),
    Map.entry(Rank.SUPERFAMILY, ColdpTerm.superfamily),
    Map.entry(Rank.FAMILY, ColdpTerm.family),
    Map.entry(Rank.SUBFAMILY, ColdpTerm.subfamily),
    Map.entry(Rank.TRIBE, ColdpTerm.tribe),
    Map.entry(Rank.SUBTRIBE, ColdpTerm.subtribe),
    Map.entry(Rank.GENUS, ColdpTerm.genus),
    Map.entry(Rank.SUBGENUS, ColdpTerm.subgenus),
    Map.entry(Rank.SECTION_BOTANY, ColdpTerm.section),
    Map.entry(Rank.SPECIES, ColdpTerm.species)
  ));

  public static List<DwcTerm> CL_TERMS_DWC = List.of(
    DwcTerm.kingdom,
    DwcTerm.phylum,
    DwcTerm.class_,
    DwcTerm.order,
    DwcTerm.superfamily,
    DwcTerm.family,
    DwcTerm.subfamily,
    DwcTerm.tribe,
    DwcTerm.subtribe,
    DwcTerm.genus,
    DwcTerm.subgenus
  );

  public static BiMap<Rank, DwcTerm> RANK2DWC = ImmutableBiMap.copyOf(Map.ofEntries(
    Map.entry(Rank.KINGDOM, DwcTerm.kingdom),
    Map.entry(Rank.PHYLUM, DwcTerm.phylum),
    Map.entry(Rank.CLASS, DwcTerm.class_),
    Map.entry(Rank.ORDER, DwcTerm.order),
    Map.entry(Rank.SUPERFAMILY, DwcTerm.superfamily),
    Map.entry(Rank.FAMILY, DwcTerm.family),
    Map.entry(Rank.SUBFAMILY, DwcTerm.subfamily),
    Map.entry(Rank.TRIBE, DwcTerm.tribe),
    Map.entry(Rank.SUBTRIBE, DwcTerm.subtribe),
    Map.entry(Rank.GENUS, DwcTerm.genus),
    Map.entry(Rank.SUBGENUS, DwcTerm.subgenus)
  ));

}

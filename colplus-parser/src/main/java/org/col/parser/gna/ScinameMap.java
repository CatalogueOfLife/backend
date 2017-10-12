package org.col.parser.gna;

import org.globalnames.parser.ScientificNameParser;
import scala.Option;
import scala.collection.Map;

/**
 *
 */
public class ScinameMap {
    private final Map<String, Object> map;


    public static ScinameMap create(ScientificNameParser.Result result) {
        return new ScinameMap((scala.collection.immutable.Map)
                ((org.json4s.JsonAST.JArray)result.detailed()).values().iterator().next()
        );
    }

    private ScinameMap(Map map) {
        this.map = map;
    }

    public Option<Epithet> uninomial() {
        return epithet("uninomial");
    }

    public Option<Epithet> genus() {
        return epithet("genus");
    }

    public Option<Epithet> infraGeneric() {
        return epithet("infrageneric_epithet");
    }

    public Option<Epithet> specificEpithet() {
        return epithet("specific_epithet");
    }

    public Option<Epithet> infraSpecificEpithet() {
        Option opt = ScalaUtils.unwrap(map.get("infraspecific_epithets"));
        if (opt.isDefined()) {
            scala.collection.immutable.List list = (scala.collection.immutable.List) opt.get();
            if (list.isEmpty()) {
                return Option.empty();
            }
            return Option.apply(new Epithet((Map) list.last()));
        }
        return Option.empty();
    }

    Option<Object> annotation() {
        return map.get("annotation_identification");
    }


    private Option<Epithet> epithet(String key) {
        Option val = ScalaUtils.unwrap(map.get(key));
        if (val.isDefined()) {
          return Option.apply(new Epithet((Map) val.get()));
        }
        return Option.empty();
    }

}

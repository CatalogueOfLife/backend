/*
 * Copyright 2013 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyTaxon of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.db.type;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import life.catalogue.db.type2.AbstractArrayTypeHandler;

import java.sql.Array;
import java.sql.SQLException;

/**
 * Stores primitive fastutils IntSet instances as non null arrays in postgres, avoiding nulls and uses empty sets instead.
 */
public class IntSetTypeHandler extends AbstractArrayTypeHandler<IntSet> {

  public IntSetTypeHandler() {
    super("integer", IntSets.EMPTY_SET);
  }

  @Override
  public Object[] toArray(IntSet obj) throws SQLException {
    return obj.toArray();
  }

  @Override
  public IntSet toObj(Array pgArray) throws SQLException {
    if (pgArray == null) return new IntOpenHashSet();

    Integer[] values = (Integer[]) pgArray.getArray();
    IntOpenHashSet set = new IntOpenHashSet(values.length);
    for (Integer i : values) {
      if (i != null) {
        set.add((int)i);
      }
    }
    return set;
  }

}

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
package life.catalogue.db.type2;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * PG array type handler that binds a primitive int[] as a postgres int4[] parameter, e.g. to join it
 * against a table via unnest() in a batch operation.
 *
 * This is deliberately a separate class from {@link IntArrayTypeHandler}: that one only extracts an
 * int[] result from a java.sql.Array column (its setNonNullParameter is inherited, unmodified, from
 * org.apache.ibatis.type.ArrayTypeHandler, which casts the parameter to Object[] and so cannot accept a
 * primitive int[] - it would throw a ClassCastException at bind time).
 */
public class PgIntArrayTypeHandler extends AbstractArrayTypeHandler<int[]> {

  public PgIntArrayTypeHandler() {
    super("int4", new int[0]);
  }

  @Override
  public Object[] toArray(int[] obj) throws SQLException {
    Integer[] boxed = new Integer[obj.length];
    Arrays.setAll(boxed, i -> obj[i]);
    return boxed;
  }

  @Override
  public int[] toObj(Array pgArray) throws SQLException {
    if (pgArray == null) return new int[0];
    Integer[] src = (Integer[]) pgArray.getArray();
    int[] dest = new int[src.length];
    Arrays.setAll(dest, i -> src[i]);
    return dest;
  }
}

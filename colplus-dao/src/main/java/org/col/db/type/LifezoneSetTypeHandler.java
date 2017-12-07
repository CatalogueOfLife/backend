package org.col.db.type;

import org.col.api.vocab.Lifezone;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class LifezoneSetTypeHandler extends EnumOrdinalSetTypeHandler<Lifezone> {

	@Override
	protected Class<Lifezone> getEnumClass() {
		return Lifezone.class;
	}

}

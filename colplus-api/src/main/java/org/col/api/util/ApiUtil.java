package org.col.api.util;

import org.col.api.Dataset;
import org.col.api.Name;
import org.col.api.Reference;
import org.col.api.Taxon;

import java.util.Objects;

class ApiUtil {

	static boolean equalsShallow(Dataset obj1, Dataset obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	static boolean equalsShallow(Taxon obj1, Taxon obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	static boolean equalsShallow(Reference obj1, Reference obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	static boolean equalsShallow(Name obj1, Name obj2) {
		if (obj1 == null) {
			return obj2 == null;
		}
		if (obj2 == null) {
			return false;
		}
		return Objects.equals(obj1.getKey(), obj2.getKey());
	}

	private ApiUtil() {
	}

}

package com.bladecoder.ink.runtime;

import java.util.HashMap;
import java.util.Map;

//Confusingly from a C# point of view, a LIST in ink is actually
// modelled using a C# Dictionary!
@SuppressWarnings("serial")
public class RawList extends HashMap<String, Integer> {
	public RawList() {

	}

	public RawList(Entry<String, Integer> singleElement) {
		put(singleElement.getKey(), singleElement.getValue());
	}

	public RawList(HashMap<String, Integer> otherList) {
		super(otherList);
	}

	public RawList union(RawList otherList) {
		RawList union = new RawList(this);
		for (String key : otherList.keySet())
			union.put(key, otherList.get(key));

		return union;
	}

	public RawList without(RawList setToRemove) {
		RawList result = new RawList(this);
		for (String kv : setToRemove.keySet())
			result.remove(kv);

		return result;
	}

	public RawList intersect(RawList otherList) {
		RawList intersection = new RawList();
		for (Entry<String, Integer> kv : this.entrySet()) {
			if (otherList.containsKey(kv.getKey()))
				intersection.put(kv.getKey(), kv.getValue());
		}
		return intersection;
	}

	public Entry<String, Integer> getMaxItem() {
		CustomEntry max = new CustomEntry(null, 0);

		for (Entry<String, Integer> kv : this.entrySet()) {
			if (max.getKey() == null || kv.getValue() > max.getValue()) {
				max.set(kv);
			}
		}

		return max;
	}

	public Entry<String, Integer> getMinItem() {
		CustomEntry min = new CustomEntry(null, 0);

		for (Entry<String, Integer> kv : this.entrySet()) {
			if (min.getKey() == null || kv.getValue() < min.getValue())
				min.set(kv);
		}

		return min;
	}

	public boolean contains(RawList otherSet) {
		for (Entry<String, Integer> kv : otherSet.entrySet()) {
			if (!this.containsKey(kv.getKey()))
				return false;
		}

		return true;
	}

	public boolean greaterThan(RawList otherSet) {
		if (size() == 0)
			return false;
		if (otherSet.size() == 0)
			return true;

		// All greater
		return getMinItem().getValue() > otherSet.getMaxItem().getValue();
	}
	
	public boolean greaterThanOrEquals(RawList otherSet) {
		if (size() == 0)
			return false;
		if (otherSet.size() == 0)
			return true;

		// All greater
		return getMinItem().getValue() >= otherSet.getMinItem().getValue() &&
				getMaxItem().getValue() >= otherSet.getMaxItem().getValue();
	}
	
	public boolean lessThan(RawList otherSet) {
		if (otherSet.size() == 0)
			return false;
		if (size() == 0)
			return true;

		return getMaxItem().getValue() < otherSet.getMinItem().getValue();
	}
	
	public boolean lessThanOrEquals(RawList otherSet) {
		if (otherSet.size() == 0)
			return false;
		if (size() == 0)
			return true;

		return getMaxItem().getValue() <= otherSet.getMaxItem().getValue() &&
				getMinItem().getValue() <= otherSet.getMinItem().getValue();
	}

	public RawList maxAsSet() {
		if (size() > 0)
			return new RawList(getMaxItem());
		else
			return new RawList();
	}

	public RawList minAsSet() {
		if (size() > 0)
			return new RawList(getMinItem());
		else
			return new RawList();
	}

	@Override
	public boolean equals(Object other) {
		RawList otherSetRawList = null;

		if (other instanceof RawList)
			otherSetRawList = (RawList) other;

		if (otherSetRawList == null)
			return false;
		if (otherSetRawList.size() != size())
			return false;

		for (String key : keySet()) {
			if (!otherSetRawList.containsKey(key))
				return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int ownHash = 0;

		for (String key : keySet())
			ownHash += key.hashCode();

		return ownHash;
	}

	public class CustomEntry implements Map.Entry<String, Integer> {

		private String key;
		private Integer value;

		CustomEntry(String key, Integer value) {
			set(key, value);
		}

		public void set(String key, Integer value) {
			this.key = key;
			this.value = value;
		}

		public void set(Map.Entry<String, Integer> e) {
			key = e.getKey();
			value = e.getValue();
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public Integer getValue() {
			return value;
		}

		@Override
		public Integer setValue(Integer value) {
			Integer old = this.value;
			this.value = value;

			return old;
		}

		public void setKey(String key) {
			this.key = key;
		}
	}
}

package ezvcard.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ezvcard.VCardVersion;
import ezvcard.io.CompatibilityMode;
import ezvcard.util.JCardValue;
import ezvcard.util.VCardStringUtils;
import ezvcard.util.XCardElement;

/*
 Copyright (c) 2013, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 The views and conclusions contained in the software and documentation are those
 of the authors and should not be interpreted as representing official policies, 
 either expressed or implied, of the FreeBSD Project.
 */

/**
 * Represents a type whose value is a list of values.
 * @author Michael Angstadt
 */
public class TextListType extends VCardType {
	protected List<String> values = new ArrayList<String>();
	private final char separator;

	/**
	 * @param name the type name (e.g. "NICKNAME")
	 * @param separator the delimiter to use (e.g. ",")
	 */
	public TextListType(String name, char separator) {
		super(name);
		this.separator = separator;
	}

	/**
	 * Gest the list of values.
	 * @return the list of values
	 */
	public List<String> getValues() {
		return values;
	}

	/**
	 * Adds a value to the list.
	 * @param value the value to add
	 */
	public void addValue(String value) {
		values.add(value);
	}

	/**
	 * Removes a value from the list.
	 * @param value the value to remove
	 */
	public void removeValue(String value) {
		values.remove(value);
	}

	@Override
	protected void doMarshalText(StringBuilder sb, VCardVersion version, List<String> warnings, CompatibilityMode compatibilityMode) {
		if (!values.isEmpty()) {
			for (String value : values) {
				value = VCardStringUtils.escape(value);
				sb.append(value);
				sb.append(separator);
			}
			sb.deleteCharAt(sb.length() - 1);
		}
	}

	@Override
	protected void doUnmarshalText(String value, VCardVersion version, List<String> warnings, CompatibilityMode compatibilityMode) {
		String split[] = VCardStringUtils.splitBy(value, separator, true, true);
		values = new ArrayList<String>(Arrays.asList(split));
	}

	@Override
	protected void doMarshalXml(XCardElement parent, List<String> warnings, CompatibilityMode compatibilityMode) {
		parent.append("text", values);
	}

	@Override
	protected void doUnmarshalXml(XCardElement element, List<String> warnings, CompatibilityMode compatibilityMode) {
		values.clear();
		values.addAll(element.getAll("text"));
	}

	@Override
	protected JCardValue doMarshalJson(VCardVersion version, List<String> warnings) {
		return JCardValue.text(values.toArray(new String[0]));
	}

	@Override
	protected void doUnmarshalJson(JCardValue value, VCardVersion version, List<String> warnings) {
		values.clear();
		for (List<String> valueStr : value.getValuesAsStrings()) {
			if (!valueStr.isEmpty()) {
				values.add(valueStr.get(0));
			}
		}
	}
}

package ezvcard.io;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import ezvcard.EZVCard;
import ezvcard.VCard;
import ezvcard.VCardException;
import ezvcard.VCardSubTypes;
import ezvcard.VCardVersion;
import ezvcard.parameters.ValueParameter;
import ezvcard.types.MemberType;
import ezvcard.types.TextType;
import ezvcard.types.VCardType;

/*
 Copyright (c) 2012, Michael Angstadt
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
 * Converts vCards to their XML representation.
 * @author Michael Angstadt
 * @see <a href="http://tools.ietf.org/html/rfc6351">RFC 6351</a>
 */
public class XCardMarshaller {
	/**
	 * Defines the names of the XML elements that are used to hold each
	 * parameter's value.
	 */
	private static final Map<String, String> parameterChildElementNames;
	static {
		Map<String, String> m = new HashMap<String, String>();
		m.put("altid", "text");
		m.put("calscale", "text");
		m.put("geo", "uri");
		m.put("label", "text");
		m.put("language", "language-tag");
		m.put("mediatype", "text");
		m.put("pid", "text");
		m.put("pref", "integer");
		m.put("sort-as", "text");
		m.put("type", "text");
		m.put("tz", "uri");
		parameterChildElementNames = Collections.unmodifiableMap(m);
	}

	private CompatibilityMode compatibilityMode = CompatibilityMode.RFC;
	private boolean addGenerator = true;
	private VCardVersion targetVersion = VCardVersion.V4_0; //xCard standard only supports 4.0
	private List<String> warnings = new ArrayList<String>();
	private final Document document;
	private final Element root;

	public XCardMarshaller() {
		DocumentBuilder builder = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			//should never be thrown
		}
		document = builder.newDocument();
		root = createElement("vcards");
		document.appendChild(root);
	}

	/**
	 * Gets the compatibility mode.
	 * @return the compatibility mode
	 */
	public CompatibilityMode getCompatibilityMode() {
		return compatibilityMode;
	}

	/**
	 * Used for customizing the marshalling process based on the mail client
	 * that the vCard is being generated for.
	 * @param compatibilityMode the compatiblity mode
	 */
	public void setCompatibilityMode(CompatibilityMode compatibilityMode) {
		this.compatibilityMode = compatibilityMode;
	}

	/**
	 * Sets whether or not to add a "X-GENERATOR" type to the vCard, saying that
	 * it was generated by this library.
	 * @param addGenerator true to add this extended type, false not to
	 * (defaults to true)
	 */
	public void setAddGenerator(boolean addGenerator) {
		this.addGenerator = addGenerator;
	}

	/**
	 * Gets the warnings from the last vCard that was marshalled. This list is
	 * reset every time a new vCard is written.
	 * @return the warnings or empty list if there were no warnings
	 */
	public List<String> getWarnings() {
		return new ArrayList<String>(warnings);
	}

	/**
	 * Gets the XML document that was generated.
	 * @return the XML document
	 */
	public Document getDocument() {
		return document;
	}

	/**
	 * Writes the XML document to an output stream.
	 * @param writer the output stream
	 * @throws TransformerException if there's a problem writing to the output
	 * stream
	 */
	public void write(Writer writer) throws TransformerException {
		Transformer t = TransformerFactory.newInstance().newTransformer();
		Source source = new DOMSource(document);
		Result result = new StreamResult(writer);
		t.transform(source, result);
	}

	/**
	 * Adds a vCard to the XML document
	 * @param vcard the vCard to add
	 * @throws VCardException if there's a problem marshalling the vCard
	 */
	public void addVCard(VCard vcard) throws VCardException, IOException {
		warnings.clear();

		if (vcard.getFormattedName() == null) {
			warnings.add("vCard version " + targetVersion + " requires that a formatted name be defined.");
		}

		//use reflection to get all VCardType fields in the VCard class
		//the order that the Types are in doesn't matter
		ListMultimap<String, VCardType> types = ArrayListMultimap.create(); //group the types by group
		for (Field f : vcard.getClass().getDeclaredFields()) {
			try {
				f.setAccessible(true);
				Object value = f.get(vcard);
				if (value instanceof VCardType) {
					VCardType type = (VCardType) value;
					addToTypeList(type, vcard, types);
				} else if (value instanceof Collection) {
					Collection<?> collection = (Collection<?>) value;
					for (Object obj : collection) {
						if (obj instanceof VCardType) {
							VCardType type = (VCardType) obj;
							addToTypeList(type, vcard, types);
						}
					}
				}
			} catch (IllegalArgumentException e) {
				//shouldn't be thrown because we're passing the correct object into Field.get()
			} catch (IllegalAccessException e) {
				//shouldn't be thrown because we're calling Field.setAccessible(true)
			}
		}

		//add extended types
		for (VCardType extendedType : vcard.getExtendedTypes().values()) {
			addToTypeList(extendedType, vcard, types);
		}

		//add an extended type saying it was generated by EZ vCard
		if (addGenerator) {
			addToTypeList(new TextType("X-GENERATOR", "EZ vCard v" + EZVCard.VERSION + " " + EZVCard.URL), vcard, types);
		}

		//marshal each type object
		Element vcardElement = createElement("vcard");
		for (String groupName : types.keySet()) {
			Element parent;
			if (groupName != null) {
				Element groupElement = createElement("group");
				groupElement.setAttribute("name", groupName);
				vcardElement.appendChild(groupElement);
				parent = groupElement;
			} else {
				parent = vcardElement;
			}

			for (VCardType type : types.get(groupName)) {
				Element typeElement = marshalType(type, vcard);
				if (typeElement != null) {
					parent.appendChild(typeElement);
				}
			}
		}
		root.appendChild(vcardElement);
	}

	/**
	 * Marshals a type object to an XML element.
	 * @param type the type object to marshal
	 * @param vcard the vcard the type belongs to
	 * @return the XML element or null not to add anything to the final XML
	 * document
	 * @throws VCardException
	 */
	private Element marshalType(VCardType type, VCard vcard) throws VCardException {
		Element typeElement = createElement(type.getTypeName().toLowerCase());

		//marshal the sub types
		VCardSubTypes subTypes;
		warnings.clear();
		try {
			subTypes = type.marshalSubTypes(targetVersion, warnings, compatibilityMode, vcard);
		} finally {
			this.warnings.addAll(warnings);
		}
		if (!subTypes.getMultimap().isEmpty()) {
			Element parametersElement = createElement("parameters");
			for (String paramName : subTypes.getNames()) {
				if (paramName.equals(ValueParameter.NAME)) {
					//don't include the VALUE parameter
					continue;
				}

				paramName = paramName.toLowerCase();
				Element parameterElement = createElement(paramName);
				for (String paramValue : subTypes.get(paramName)) {
					String valueElementName = parameterChildElementNames.get(paramName);
					if (valueElementName == null) {
						valueElementName = "unknown";
					}
					Element parameterValueElement = createElement(valueElementName);
					parameterValueElement.setTextContent(paramValue);
					parameterElement.appendChild(parameterValueElement);
				}
				parametersElement.appendChild(parameterElement);
			}
			typeElement.appendChild(parametersElement);
		}

		//marshal the value
		//TODO add a new method to "VCardType" so each type class can handle how it is marshalled to XML
		Element valueElement = createElement("text");
		String value = null;
		List<String> warnings = new ArrayList<String>();
		try {
			value = type.marshalValue(targetVersion, warnings, compatibilityMode);
		} finally {
			this.warnings.addAll(warnings);
		}
		if (value == null) {
			warnings.add(type.getTypeName() + " type has requested that it not be marshalled.");
			return null;
		}
		valueElement.setTextContent(value);
		typeElement.appendChild(valueElement);

		return typeElement;
	}

	/**
	 * Adds a type object to the "will-be-marshalled" list if it determines that
	 * the type should be added to the final XML document.
	 * @param type the type to consider for addition
	 * @param vcard the vcard that the type belongs to
	 * @param list the "will-be-marshalled" list
	 */
	private void addToTypeList(VCardType type, VCard vcard, ListMultimap<String, VCardType> list) {
		if (type == null) {
			return;
		}

		//determine if this type is supported by the target version
		boolean supported = false;
		for (VCardVersion v : type.getSupportedVersions()) {
			if (v == targetVersion) {
				supported = true;
				break;
			}
		}

		if (supported) {
			if (type instanceof MemberType && (vcard.getKind() == null || !vcard.getKind().isGroup())) {
				warnings.add("The value of KIND must be set to \"group\" in order to add MEMBERs to the vCard.");
				return;
			}
			list.put(type.getGroup(), type);
		} else {
			warnings.add("The " + type.getTypeName() + " type is not supported by vCard version " + targetVersion + ".  The supported versions are " + Arrays.toString(type.getSupportedVersions()) + ".  This type will not be added to the vCard.");
			return;
		}
	}

	/**
	 * Creates a new XML element.
	 * @param name the name of the XML element
	 * @return the new XML element
	 */
	private Element createElement(String name) {
		return document.createElementNS("urn:ietf:params:xml:ns:vcard-" + targetVersion.getVersion(), name);
	}
}

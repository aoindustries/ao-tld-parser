/*
 * ao-tld-parser - Parses JSP tag library *.tld files.
 * Copyright (C) 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-tld-parser.
 *
 * ao-tld-parser is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-tld-parser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-tld-parser.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.tld.parser;

import com.aoindustries.xml.XmlUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * At least through Servlet 3.0, *.tld files do not support generics,
 * but we want to include the extra generics information in
 * <a href="https://semanticcms.com/tag-reference/">generated taglib documentation</a>.
 * This allows for the generics information to be included in
 * comments, that will be parsed and included for display.
 */
class XmlHelper {

	static final String PATTERN_PRE  = "^\\s*";
	static final String PATTERN_POST = "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')\\s*$";

	private XmlHelper() {}

	/**
	 * Looks for a comment that defines a variable of the given name using the
	 * provided pattern.
	 * The variable may be defined at most once.
	 */
	static String getVariable(Element element, Pattern pattern, String varName) {
		NodeList children = element.getChildNodes();
		String match = null;
		for(int i = 0, len = children.getLength(); i < len; i++) {
			Node child = children.item(i);
			if(child instanceof Comment) {
				Comment comment = (Comment)child;
				Matcher matcher = pattern.matcher(comment.getData());
				while(matcher.find()) {
					String doubleQuoted = matcher.group(1);
					String singleQuoted = matcher.group(2);
					String value;
					if(doubleQuoted != null) {
						if(singleQuoted != null) throw new IllegalArgumentException(varName + ": Found both in double quotes (\") and single quotes ('): " + matcher.group());
						value = doubleQuoted;
					} else {
						assert singleQuoted != null : "At least one of the two capturing groups must have matched";
						value = singleQuoted;
					}
					if(match != null) {
						throw new IllegalArgumentException(varName + ": More than one value found: \"" + match + "\" and \"" + value + "'");
					}
					match = value;
				}
			}
		}
		return match;
	}

	/**
	 * Gets a value from a child node, with optional variable-comment providing
	 * a more detailed version with generics.
	 * <p>
	 * The generics are provided within special comments inside the XML, where the value must match
	 * <code><var>childTagName</var></code>, but with the addition of {@code <…>} segments.
	 * </p>
	 * <pre>&lt;!-- <var>varName</var> = "…" --&gt;</pre>
	 *
	 * @see  XmlUtils#getChildTextContent(org.w3c.dom.Element, java.lang.String)
	 */
	static String getChildWithGenerics(Element element, String childTagName, Pattern pattern, String varName) {
		String text = XmlUtils.getChildTextContent(element, childTagName);
		String comment = getVariable(element, pattern, varName);
		if(text == null) {
			if(comment != null) {
				throw new IllegalArgumentException("variable-comment (" + varName + ") without child element (" + childTagName +")");
			}
			return null;
		} else {
			if(comment == null) {
				return text;
			} else {
				// Verify consistency between values
				int textLen = text.length();
				int commentLen = comment.length();
				int textPos = 0;
				int commentPos = 0;
				while(textPos < textLen || commentPos < commentLen) {
					int textCh = (textPos < textLen) ? text.charAt(textPos++) : -1;
					if(textCh == '<' || textCh == '>') throw new IllegalArgumentException("Generics not allowed directly in child element (" + childTagName + "): \"" + text + "\"");
					int commentCh = (commentPos < commentLen) ? comment.charAt(commentPos++) : -1;
					// Skip any generics segment
					if(commentCh == '<') {
						int depth = 1;
						do {
							if(commentPos >= commentLen) throw new IllegalArgumentException("Incomplete generic segment in variable-comment (" + varName + "): \"" + comment + "\"");
							commentCh = comment.charAt(commentPos++);
							if(commentCh == '>') depth--;
							else if(commentCh == '<') depth++;
						} while(depth > 0);
						commentCh = (commentPos < commentLen) ? comment.charAt(commentPos++) : -1;
					}
					if(textCh != commentCh) {
						throw new IllegalArgumentException("child element (" + childTagName +") and variable-comment (" + varName + ") mismatch: \"" + text + "\" -> \"" + comment + "\"");
					}
				}
				return comment;
			}
		}
	}
}
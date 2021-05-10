//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.0 
// See <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2021.05.06 at 06:44:37 PM CEST 
//


package life.catalogue.doi.datacite.model;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for numberType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="numberType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="Article"/&gt;
 *     &lt;enumeration value="Chapter"/&gt;
 *     &lt;enumeration value="Report"/&gt;
 *     &lt;enumeration value="Other"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "numberType")
@XmlEnum
public enum NumberType {

    @XmlEnumValue("Article")
    ARTICLE("Article"),
    @XmlEnumValue("Chapter")
    CHAPTER("Chapter"),
    @XmlEnumValue("Report")
    REPORT("Report"),
    @XmlEnumValue("Other")
    OTHER("Other");
    private final String value;

    NumberType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static NumberType fromValue(String v) {
        for (NumberType c: NumberType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
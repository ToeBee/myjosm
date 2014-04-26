//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2011.01.09 at 07:33:18 PM CET
//
package org.openstreetmap.josm.data.imagery.types;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="projection" type="{http://josm.openstreetmap.de/wms-cache-1.0}projection" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="tileSize" use="required" type="{http://www.w3.org/2001/XMLSchema}int" /&gt;
 *       &lt;attribute name="totalFileSize" use="required" type="{http://www.w3.org/2001/XMLSchema}int" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "projection"
})
@XmlRootElement(name = "wms-cache")
public class WmsCacheType {

    protected List<ProjectionType> projection;
    @XmlAttribute(required = true)
    protected int tileSize;
    @XmlAttribute(required = true)
    protected int totalFileSize;

    /**
     * Gets the value of the projection property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the projection property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProjection().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProjectionType }
     *
     *
     */
    public List<ProjectionType> getProjection() {
        if (projection == null) {
            projection = new ArrayList<>();
        }
        return this.projection;
    }

    /**
     * Gets the value of the tileSize property.
     *
     */
    public int getTileSize() {
        return tileSize;
    }

    /**
     * Sets the value of the tileSize property.
     *
     */
    public void setTileSize(int value) {
        this.tileSize = value;
    }

    /**
     * Gets the value of the totalFileSize property.
     *
     */
    public int getTotalFileSize() {
        return totalFileSize;
    }

    /**
     * Sets the value of the totalFileSize property.
     *
     */
    public void setTotalFileSize(int value) {
        this.totalFileSize = value;
    }

}

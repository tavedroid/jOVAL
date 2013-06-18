// Copyright (C) 2012 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.xml;

import javax.xml.bind.JAXBException;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.joval.intf.xml.ITransformable;
import org.joval.util.JOVALMsg;

/**
 * Utility for working with XML DOM.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class DOMTools {
    /**
     * Transform a JAXB object into a W3C Node.
     */
    public static Element toElement(ITransformable source) throws Exception {
	TransformerFactory xf = XSLTools.XSLVersion.V1.getFactory();
	Transformer transformer = xf.newTransformer();
	DOMResult result = new DOMResult();
	transformer.transform(source.getSource(), result);
	return ((Document)result.getNode()).getDocumentElement();
    }

    public static String getNamespace(ITransformable source) {
	try {
	    JAXBIntrospector ji = source.getJAXBContext().createJAXBIntrospector();
	    return ji.getElementName(source.getRootObject()).getNamespaceURI();
	} catch (JAXBException e) {
	    return null;
	}
    }
}

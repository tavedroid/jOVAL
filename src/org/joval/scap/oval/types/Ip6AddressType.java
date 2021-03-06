// Copyright (C) 2012 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.scap.oval.types;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import org.joval.intf.scap.oval.IType;
import org.joval.util.JOVALMsg;

/**
 * A type class for dealing with individual addresses or CIDR ranges for IPv6.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class Ip6AddressType extends AbstractType {
    private short[] addr = new short[16];
    private short[] mask = new short[16];
    private int maskVal;

    public Ip6AddressType(String str) throws IllegalArgumentException {
	str = str.trim();
	if (str.length() == 0) {
	    throw new IllegalArgumentException(str);
	}
	int ptr = str.indexOf("/");
	maskVal = 128;
	String ipStr = null;
	if (ptr == -1) {
	    ipStr = str;
	} else {
	    maskVal = Integer.parseInt(str.substring(ptr+1));
	    ipStr = str.substring(0, ptr);
	}

	//
	// Create the netmask
	//
	short[] maskBits = new short[128];
	for (int i=0; i < 128; i++) {
	    if (i < maskVal) {
		maskBits[i] = 1;
	    } else {
		maskBits[i] = 0;
	    }
	}
	for (int i=0; i < 16; i++) {
	    mask[i] = (short)(maskBits[8*i + 0] * 128 +
			      maskBits[8*i + 1] * 64 +
			      maskBits[8*i + 2] * 32 +
			      maskBits[8*i + 3] * 16 +
			      maskBits[8*i + 4] * 8 +
			      maskBits[8*i + 5] * 4 +
			      maskBits[8*i + 6] * 2 +
			      maskBits[8*i + 7]);
	}

	//
	// Parse the address
	//
	try {
	    int i=0;
	    byte[] bytes = InetAddress.getByName(ipStr).getAddress();
	    if (bytes.length == 16) {
		for (byte b : bytes) {
		    addr[i++] = (short)(0xFF & b);
		}
	    } else {
		throw new IllegalArgumentException(str);
	    }
	} catch (UnknownHostException e) {
	    throw new IllegalArgumentException(str);
	}
    }


    public int getMask() {
	return maskVal;
    }

    public BigInteger toBigInteger() {
	StringBuffer sb = new StringBuffer();
	for (int i=0; i < addr.length; i++) {
	    sb.append(Integer.toHexString(addr[i] & 0xFF));
	}
	return new BigInteger(sb.toString(), 16);
    }

    public String getIpAddressString() {
	StringBuffer sb = new StringBuffer();
	for (int i=0, j=0; i < 16; j++) {
	    if (i > 0) {
		sb.append(":");
	    }
	    StringBuffer word = new StringBuffer();
	    word.append(Integer.toHexString(addr[i++] & 0xFF));
	    word.append(Integer.toHexString(addr[i++] & 0xFF));
	    sb.append(Integer.toHexString(Integer.parseInt(word.toString(), 16)));
	}
	return sb.toString();
    }

    public String getSubnetString() {
	StringBuffer sb = new StringBuffer();
	for (int i=0, j=0; i < 16; j++) {
	    if (i > 0) {
		sb.append(":");
	    }
	    StringBuffer word = new StringBuffer();
	    word.append(Integer.toHexString(mask[i++] & 0xFF));
	    word.append(Integer.toHexString(mask[i++] & 0xFF));
	    sb.append(Integer.toHexString(Integer.parseInt(word.toString(), 16)));
	}
	return sb.toString();
    }

    @Override
    public String toString() {
	StringBuffer sb = new StringBuffer(getIpAddressString());
	if (maskVal == 128) {
	    return sb.toString();
	} else {
	    return sb.append("/").append(Integer.toString(maskVal)).toString();
	}
    }

    public boolean contains(Ip6AddressType other) {
	for (int i=0; i < 16; i++) {
	    if (addr[i] != (other.addr[i] & mask[i])) {
		return false;
	    }
	}
	return true;
    }

    // Implement IType

    public Type getType() {
	return Type.IPV_6_ADDRESS;
    }

    public String getString() {
	return toString();
    }

    // Implement Comparable

    public int compareTo(IType t) {
	Ip6AddressType other = null;
	try {
	    other = (Ip6AddressType)t.cast(getType());
	} catch (TypeConversionException e) {
	    throw new IllegalArgumentException(e);
	}
	if (getMask() == other.getMask()) {
	    return toBigInteger().compareTo(other.toBigInteger());
	} else {
	    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_TYPE_CIDR_MASKS, getMask(), other.getMask());
	    throw new IllegalArgumentException(msg);
	}
    }
}

// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.scap.oval.adapter.unix;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import jsaf.Message;
import jsaf.intf.io.IFile;
import jsaf.intf.io.IFileEx;
import jsaf.intf.system.ISession;
import jsaf.intf.unix.io.IUnixFileInfo;
import jsaf.intf.unix.io.IUnixFilesystem;
import jsaf.intf.unix.system.IUnixSession;
import jsaf.io.StreamTool;

import scap.oval.common.MessageLevelEnumeration;
import scap.oval.common.MessageType;
import scap.oval.common.SimpleDatatypeEnumeration;
import scap.oval.definitions.core.ObjectType;
import scap.oval.definitions.unix.FileObject;
import scap.oval.systemcharacteristics.core.EntityItemBoolType;
import scap.oval.systemcharacteristics.core.EntityItemIntType;
import scap.oval.systemcharacteristics.core.EntityItemStringType;
import scap.oval.systemcharacteristics.core.FlagEnumeration;
import scap.oval.systemcharacteristics.core.ItemType;
import scap.oval.systemcharacteristics.core.StatusEnumeration;
import scap.oval.systemcharacteristics.unix.FileItem;

import org.joval.intf.plugin.IAdapter;
import org.joval.scap.oval.CollectException;
import org.joval.scap.oval.Factories;
import org.joval.scap.oval.adapter.independent.BaseFileAdapter;
import org.joval.util.JOVALMsg;

/**
 * Evaluates UNIX File OVAL tests.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class FileAdapter extends BaseFileAdapter<FileItem> {
    private IUnixSession us;

    // Implement IAdapter

    public Collection<Class> init(ISession session, Collection<Class> notapplicable) {
	Collection<Class> classes = new ArrayList<Class>();
	if (session instanceof IUnixSession) {
	    us = (IUnixSession)session;
	    baseInit(us);
	    classes.add(FileObject.class);
	} else {
	    notapplicable.add(FileObject.class);
	}
	return classes;
    }

    // Protected

    protected Class getItemClass() {
	return FileItem.class;
    }

    protected Collection<FileItem> getItems(ObjectType obj, Collection<IFile> files, IRequestContext rc)
		throws CollectException {

	Collection<FileItem> items = new ArrayList<FileItem>();
	for (IFile f : files) {
	    try {
		FileItem item = (FileItem)getBaseItem(obj, f);
		if (item != null) {
		    setItem(item, f);
		    items.add(item);
		}
	    } catch (IOException e) {
		session.getLogger().warn(Message.ERROR_IO, f.getPath(), e.getMessage());
		MessageType msg = Factories.common.createMessageType();
		msg.setLevel(MessageLevelEnumeration.ERROR);
		msg.setValue(JOVALMsg.getMessage(Message.ERROR_IO, f.getPath(), e.getMessage()));
		rc.addMessage(msg);
	    }
	}
	return items;
    }

    // Private

    /**
     * Decorate the Item with information about the file.
     */
    private void setItem(FileItem item, IFile f) throws IOException, CollectException {
	IFileEx info = f.getExtended();
	IUnixFileInfo ufi = null;
	if (info instanceof IUnixFileInfo) {
	    ufi = (IUnixFileInfo)info;
	} else {
	    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNIX_FILE, f.getClass().getName());
	    throw new CollectException(msg, FlagEnumeration.NOT_APPLICABLE);
	}
	session.getLogger().trace(JOVALMsg.STATUS_UNIX_FILE, f.getPath());
	EntityItemIntType aTime = Factories.sc.core.createEntityItemIntType();
	Date at = f.getAccessTime();
	if (at == null) {
	    aTime.setStatus(StatusEnumeration.NOT_COLLECTED);
	} else {
	    aTime.setValue(Long.toString(at.getTime()/1000L));
	    aTime.setDatatype(SimpleDatatypeEnumeration.INT.value());
	}
	item.setATime(aTime);

	EntityItemIntType cTime = Factories.sc.core.createEntityItemIntType();
	Date ct = f.getCreateTime();
	if (ct == null) {
	    cTime.setStatus(StatusEnumeration.NOT_COLLECTED);
	} else {
	    cTime.setValue(Long.toString(ct.getTime()/1000L));
	    cTime.setDatatype(SimpleDatatypeEnumeration.INT.value());
	}
	item.setCTime(cTime);

	EntityItemIntType mTime = Factories.sc.core.createEntityItemIntType();
	Date lm = f.getLastModified();
	if (lm == null) {
	    mTime.setStatus(StatusEnumeration.NOT_COLLECTED);
	} else {
	    mTime.setValue(Long.toString(lm.getTime()/1000L));
	    mTime.setDatatype(SimpleDatatypeEnumeration.INT.value());
	}
	item.setMTime(mTime);

	EntityItemIntType sizeType = Factories.sc.core.createEntityItemIntType();
	sizeType.setValue(Long.toString(f.length()));
	sizeType.setDatatype(SimpleDatatypeEnumeration.INT.value());
	item.setSize(sizeType);

	EntityItemStringType type = Factories.sc.core.createEntityItemStringType();
	type.setValue(ufi.getUnixFileType());
	item.setType(type);

	EntityItemIntType userId = Factories.sc.core.createEntityItemIntType();
	userId.setValue(Integer.toString(ufi.getUserId()));
	userId.setDatatype(SimpleDatatypeEnumeration.INT.value());
	item.setUserId(userId);

	EntityItemIntType groupId = Factories.sc.core.createEntityItemIntType();
	groupId.setValue(Integer.toString(ufi.getGroupId()));
	groupId.setDatatype(SimpleDatatypeEnumeration.INT.value());
	item.setGroupId(groupId);

	EntityItemBoolType uRead = Factories.sc.core.createEntityItemBoolType();
	uRead.setValue(ufi.uRead() ? "1" : "0");
	uRead.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setUread(uRead);

	EntityItemBoolType uWrite = Factories.sc.core.createEntityItemBoolType();
	uWrite.setValue(ufi.uWrite() ? "1" : "0");
	uWrite.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setUwrite(uWrite);

	EntityItemBoolType uExec = Factories.sc.core.createEntityItemBoolType();
	uExec.setValue(ufi.uExec() ? "1" : "0");
	uExec.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setUexec(uExec);

	EntityItemBoolType sUid = Factories.sc.core.createEntityItemBoolType();
	sUid.setValue(ufi.sUid() ? "1" : "0");
	sUid.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setSuid(sUid);

	EntityItemBoolType gRead = Factories.sc.core.createEntityItemBoolType();
	gRead.setValue(ufi.gRead() ? "1" : "0");
	gRead.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setGread(gRead);

	EntityItemBoolType gWrite = Factories.sc.core.createEntityItemBoolType();
	gWrite.setValue(ufi.gWrite() ? "1" : "0");
	gWrite.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setGwrite(gWrite);

	EntityItemBoolType gExec = Factories.sc.core.createEntityItemBoolType();
	gExec.setValue(ufi.gExec() ? "1" : "0");
	gExec.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setGexec(gExec);

	EntityItemBoolType sGid = Factories.sc.core.createEntityItemBoolType();
	sGid.setValue(ufi.sGid() ? "1" : "0");
	sGid.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setSgid(sGid);

	EntityItemBoolType oRead = Factories.sc.core.createEntityItemBoolType();
	oRead.setValue(ufi.oRead() ? "1" : "0");
	oRead.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setOread(oRead);

	EntityItemBoolType oWrite = Factories.sc.core.createEntityItemBoolType();
	oWrite.setValue(ufi.oWrite() ? "1" : "0");
	oWrite.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setOwrite(oWrite);

	EntityItemBoolType oExec = Factories.sc.core.createEntityItemBoolType();
	oExec.setValue(ufi.oExec() ? "1" : "0");
	oExec.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setOexec(oExec);

	EntityItemBoolType sticky = Factories.sc.core.createEntityItemBoolType();
	sticky.setValue(ufi.sticky() ? "1" : "0");
	sticky.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setSticky(sticky);

	EntityItemBoolType aclType = Factories.sc.core.createEntityItemBoolType();
	Boolean hasAcl = ufi.hasPosixAcl();
	if (hasAcl == null) {
	    aclType.setStatus(StatusEnumeration.NOT_COLLECTED);
	} else {
	    aclType.setValue(hasAcl ? "1" : "0");
	}
	aclType.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setHasExtendedAcl(aclType);
    }
}

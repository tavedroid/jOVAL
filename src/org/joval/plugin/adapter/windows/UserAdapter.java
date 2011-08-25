// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.plugin.adapter.windows;

import java.util.Hashtable;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.xml.bind.JAXBElement;

import oval.schemas.common.MessageType;
import oval.schemas.common.MessageLevelEnumeration;
import oval.schemas.common.SimpleDatatypeEnumeration;
import oval.schemas.definitions.windows.UserObject;
import oval.schemas.systemcharacteristics.core.ItemType;
import oval.schemas.systemcharacteristics.core.EntityItemBoolType;
import oval.schemas.systemcharacteristics.core.EntityItemStringType;
import oval.schemas.systemcharacteristics.core.StatusEnumeration;
import oval.schemas.systemcharacteristics.windows.UserItem;
import oval.schemas.results.core.ResultEnumeration;

import org.joval.identity.windows.LocalDirectory;
import org.joval.identity.windows.User;
import org.joval.intf.plugin.IAdapter;
import org.joval.intf.plugin.IRequestContext;
import org.joval.intf.windows.wmi.IWmiProvider;
import org.joval.oval.OvalException;
import org.joval.util.JOVALSystem;
import org.joval.windows.wmi.WmiException;

/**
 * Evaluates User OVAL tests.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class UserAdapter implements IAdapter {
    private IWmiProvider wmi;
    private String hostname;
    private LocalDirectory local = null;

    public UserAdapter(String hostname, IWmiProvider wmi) {
	this.wmi = wmi;
	this.hostname = hostname.toUpperCase();
    }

    // Implement IAdapter

    public Class getObjectClass() {
	return UserObject.class;
    }

    public boolean connect() {
	if (wmi.connect()) {
	    local = new LocalDirectory(hostname, wmi);
	    return true;
	}
	return false;
    }

    public void disconnect() {
	wmi.disconnect();
	local = null;
    }

    public List<JAXBElement<? extends ItemType>> getItems(IRequestContext rc) throws OvalException {
	List<JAXBElement<? extends ItemType>> items = new Vector<JAXBElement<? extends ItemType>>();
	UserObject uObj = (UserObject)rc.getObject();
	String user = (String)uObj.getUser().getValue();

	try {
	    switch(uObj.getUser().getOperation()) {
	      case EQUALS:
		items.add(makeUserItem(local.queryUser(user)));
		break;
    
	      case NOT_EQUAL:
		for (User u : local.queryAllUsers()) {
		    JAXBElement<UserItem> item = makeUserItem(u);
		    if (!user.equals((String)item.getValue().getUser().getValue())) {
			items.add(item);
		    }
		}
		break;
    
	      case PATTERN_MATCH:
		try {
		    Pattern p = Pattern.compile(user);
		    for (User u : local.queryAllUsers()) {
			JAXBElement<UserItem> item = makeUserItem(u);
			if (p.matcher((String)item.getValue().getUser().getValue()).find()) {
			    items.add(item);
			}
		    }
		} catch (PatternSyntaxException e) {
		    MessageType msg = JOVALSystem.factories.common.createMessageType();
		    msg.setLevel(MessageLevelEnumeration.ERROR);
		    msg.setValue(JOVALSystem.getMessage("ERROR_PATTERN", e.getMessage()));
		    rc.addMessage(msg);
		    JOVALSystem.getLogger().log(Level.WARNING, e.getMessage(), e);
		}
		break;
    
	      default:
		throw new OvalException(JOVALSystem.getMessage("ERROR_UNSUPPORTED_OPERATION", uObj.getUser().getOperation()));
	    }
	} catch (NoSuchElementException e) {
	    // No match.
	} catch (WmiException e) {
	    MessageType msg = JOVALSystem.factories.common.createMessageType();
	    msg.setLevel(MessageLevelEnumeration.ERROR);
	    msg.setValue(JOVALSystem.getMessage("ERROR_WINWMI_GENERAL", e.getMessage()));
	    rc.addMessage(msg);
	}
	return items;
    }

    // Private

    private JAXBElement<UserItem> makeUserItem(User user) {
	UserItem item = JOVALSystem.factories.sc.windows.createUserItem();
	EntityItemStringType userType = JOVALSystem.factories.sc.core.createEntityItemStringType();
	if (local.isBuiltinUser(user.getNetbiosName())) {
	    userType.setValue(user.getName());
	} else {
	    userType.setValue(user.getNetbiosName());
	}
	item.setUser(userType);
	EntityItemBoolType enabledType = JOVALSystem.factories.sc.core.createEntityItemBoolType();
	enabledType.setValue(user.isEnabled() ? "true" : "false");
	enabledType.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setEnabled(enabledType);
	List<String> groupNetbiosNames = user.getGroupNetbiosNames();
	if (groupNetbiosNames.size() == 0) {
	    EntityItemStringType groupType = JOVALSystem.factories.sc.core.createEntityItemStringType();
	    groupType.setStatus(StatusEnumeration.DOES_NOT_EXIST);
	    item.getGroup().add(groupType);
	} else {
	    for (String groupName : groupNetbiosNames) {
		EntityItemStringType groupType = JOVALSystem.factories.sc.core.createEntityItemStringType();
		if (local.isBuiltinGroup(groupName)) {
		    groupType.setValue(local.getName(groupName));
		} else {
		    groupType.setValue(groupName);
		}
		item.getGroup().add(groupType);
	    }
	}
	return JOVALSystem.factories.sc.windows.createUserItem(item);
    }
}

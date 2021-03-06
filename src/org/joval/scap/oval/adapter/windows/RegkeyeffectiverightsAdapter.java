// Copyright (C) 2012 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.scap.oval.adapter.windows;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jsaf.intf.system.ISession;
import jsaf.intf.windows.registry.IKey;
import jsaf.intf.windows.identity.IACE;
import jsaf.intf.windows.identity.IDirectory;
import jsaf.intf.windows.identity.IPrincipal;
import jsaf.intf.windows.powershell.IRunspace;
import jsaf.intf.windows.registry.IRegistry;
import jsaf.intf.windows.system.IWindowsSession;
import jsaf.provider.windows.powershell.PowershellException;
import jsaf.provider.windows.wmi.WmiException;
import jsaf.util.StringTools;

import scap.oval.common.MessageLevelEnumeration;
import scap.oval.common.MessageType;
import scap.oval.common.OperationEnumeration;
import scap.oval.common.SimpleDatatypeEnumeration;
import scap.oval.definitions.core.ObjectType;
import scap.oval.definitions.windows.Regkeyeffectiverights53Object;
import scap.oval.definitions.windows.RegkeyeffectiverightsObject;
import scap.oval.systemcharacteristics.core.EntityItemBoolType;
import scap.oval.systemcharacteristics.core.EntityItemStringType;
import scap.oval.systemcharacteristics.core.FlagEnumeration;
import scap.oval.systemcharacteristics.core.ItemType;
import scap.oval.systemcharacteristics.core.StatusEnumeration;
import scap.oval.systemcharacteristics.windows.EntityItemRegistryHiveType;
import scap.oval.systemcharacteristics.windows.EntityItemWindowsViewType;
import scap.oval.systemcharacteristics.windows.RegkeyeffectiverightsItem;

import org.joval.scap.oval.CollectException;
import org.joval.scap.oval.Factories;
import org.joval.util.JOVALMsg;

/**
 * Collects items for Regkeyeffectiverights and Regkeyeffectiverights53 objects.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class RegkeyeffectiverightsAdapter extends BaseRegkeyAdapter<RegkeyeffectiverightsItem> {
    private IDirectory directory;

    // Implement IAdapter

    public Collection<Class> init(ISession session, Collection<Class> notapplicable) {
	Collection<Class> classes = new ArrayList<Class>();
	if (session instanceof IWindowsSession) {
	    super.init((IWindowsSession)session);
	    classes.add(Regkeyeffectiverights53Object.class);
	    classes.add(RegkeyeffectiverightsObject.class);
	} else {
	    notapplicable.add(Regkeyeffectiverights53Object.class);
	    notapplicable.add(RegkeyeffectiverightsObject.class);
	}
	return classes;
    }

    // Protected

    protected Class getItemClass() {
	return RegkeyeffectiverightsItem.class;
    }

    protected Collection<RegkeyeffectiverightsItem> getItems(ObjectType obj, Collection<IKey> keys, IRequestContext rc)
		throws CollectException {

	directory = session.getDirectory();
	Collection<RegkeyeffectiverightsItem> items = new ArrayList<RegkeyeffectiverightsItem>();
	try {
	    List<IPrincipal> principals = new ArrayList<IPrincipal>();
	    IWindowsSession.View view = null;
	    boolean includeGroups = true;
	    boolean resolveGroups = false;
	    if (obj instanceof Regkeyeffectiverights53Object) {
		Regkeyeffectiverights53Object rObj = (Regkeyeffectiverights53Object)obj;
		view = getView(rObj.getBehaviors());
		if (rObj.isSetBehaviors()) {
		    includeGroups = rObj.getBehaviors().getIncludeGroup();
		    resolveGroups = rObj.getBehaviors().getResolveGroup();
		}
		String pSid = (String)rObj.getTrusteeSid().getValue();
		OperationEnumeration op = rObj.getTrusteeSid().getOperation();
		switch(op) {
		  case PATTERN_MATCH: {
		    Pattern p = StringTools.pattern(pSid);
		    for (IPrincipal principal : directory.queryAllPrincipals()) {
			if (p.matcher(principal.getSid()).find()) {
			    principals.add(principal);
			}
		    }
		    break;
		  }

		  case NOT_EQUAL:
		    for (IPrincipal principal : directory.queryAllPrincipals()) {
			if (!pSid.equals(principal.getSid())) {
			    principals.add(principal);
			}
		    }
		    break;

		  case CASE_INSENSITIVE_EQUALS:
		  case EQUALS: {
		    principals.add(directory.queryPrincipalBySid(pSid));
		    break;
		  }

		  default:
		    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_OPERATION, op);
		    throw new CollectException(msg, FlagEnumeration.NOT_COLLECTED);
		}
	    } else if (obj instanceof RegkeyeffectiverightsObject) {
		RegkeyeffectiverightsObject rObj = (RegkeyeffectiverightsObject)obj;
		view = getView(rObj.getBehaviors());
		if (rObj.isSetBehaviors()) {
		    includeGroups = rObj.getBehaviors().getIncludeGroup();
		    resolveGroups = rObj.getBehaviors().getResolveGroup();
		}
		String pName = (String)rObj.getTrusteeName().getValue();
		OperationEnumeration op = rObj.getTrusteeName().getOperation();
		switch(op) {
		  case PATTERN_MATCH: {
		    Pattern p = StringTools.pattern(pName);
		    for (IPrincipal principal : directory.queryAllPrincipals()) {
			if (principal.isBuiltin() && p.matcher(principal.getName()).find()) {
			    principals.add(principal);
			} else if (p.matcher(principal.getNetbiosName()).find()) {
			    principals.add(principal);
			}
		    }
		    break;
		  }

		  case NOT_EQUAL:
		    for (IPrincipal principal : directory.queryAllPrincipals()) {
			if (principal.isBuiltin() && !pName.equals(principal.getName())) {
			    principals.add(principal);
			} else if (!pName.equals(principal.getNetbiosName())) {
			    principals.add(principal);
			}
		    }
		    break;

		  case CASE_INSENSITIVE_EQUALS:
		  case EQUALS: {
		    principals.add(directory.queryPrincipal(pName));
		    break;
		  }

		  default:
		    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_OPERATION, op);
		    throw new CollectException(msg, FlagEnumeration.NOT_COLLECTED);
		}
	    } else {
		String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_OBJECT, obj.getClass().getName(), obj.getId());
		throw new CollectException(msg, FlagEnumeration.ERROR);
	    }

	    //
	    // Filter out any duplicate IPrincipals
	    //
	    Map<String, IPrincipal> principalMap = new HashMap<String, IPrincipal>();
	    for (IPrincipal principal : principals) {
		principalMap.put(principal.getSid(), principal);
	    }

	    for (IKey key : keys) {
	        //
	        // Map the Key's hive to one of the names supported by the GetNamedSecurityInfo method.
	        //
	        // See: http://msdn.microsoft.com/en-us/library/windows/desktop/aa379593%28v=vs.85%29.aspx
	        //
	        String hive = null;
	        switch(key.getHive()) {
	          case HKLM:
		    hive = "MACHINE";
		    break;
	          case HKU:
		    hive = "USERS";
		    break;
	          case HKCU:
		    hive = "CURRENT_USER";
		    break;
	          case HKCR:
		    hive = "CLASSES_ROOT";
		    break;
	          case HKCC:
		    hive = "CONFIG";
		    break;
	          default:
		    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_WINREG_HIVE_NAME, key.getHive());
		    throw new CollectException(msg, FlagEnumeration.NOT_COLLECTED);
	        }

		//
		// Create items
		//
	        RegkeyeffectiverightsItem baseItem = (RegkeyeffectiverightsItem)getBaseItem(obj, key);
		Map<String, IPrincipal> resolvedPrincipals = new HashMap<String, IPrincipal>();
		for (IPrincipal principal : principalMap.values()) {
		    switch(principal.getType()) {
		      case USER:
			resolvedPrincipals.put(principal.getSid(), principal);
			break;
		      case GROUP:
			for (IPrincipal p : directory.getAllPrincipals(principal, includeGroups, resolveGroups)) {
			    resolvedPrincipals.put(p.getSid(), p);
			}
			break;
		    }
		}
		StringBuffer cmd = new StringBuffer();
		for (Map.Entry<String, IPrincipal> entry : resolvedPrincipals.entrySet()) {
		    if (cmd.length() > 0) {
			cmd.append(",");
		    }
		    cmd.append("\"").append(entry.getKey()).append("\"");
		}
		cmd.append(" | Get-EffectiveRights -ObjectType RegKey ");
		cmd.append(" -Name \"").append(hive).append("\\").append(key.getPath()).append("\"");
		for (String line : getRunspace(view).invoke(cmd.toString()).split("\r\n")) {
		    int ptr = line.indexOf(":");
		    String sid = line.substring(0,ptr);
		    int mask = Integer.parseInt(line.substring(ptr+1).trim());
		    items.add(makeItem(baseItem, resolvedPrincipals.get(sid), mask));
		}
	    }
	} catch (NoSuchElementException e) {
	    MessageType msg = Factories.common.createMessageType();
	    msg.setLevel(MessageLevelEnumeration.INFO);
	    msg.setValue(JOVALMsg.getMessage(JOVALMsg.ERROR_WIN_NOPRINCIPAL, e.getMessage()));
	    rc.addMessage(msg);
	} catch (PatternSyntaxException e) {
	    session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	    throw new CollectException(e, FlagEnumeration.ERROR);
	} catch (CollectException e) {
	    throw e;
	} catch (Exception e) {
	    session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	    throw new CollectException(e, FlagEnumeration.ERROR);
	}
	return items;
    }

    @Override
    protected List<InputStream> getPowershellAssemblies() {
	return Arrays.asList(getClass().getResourceAsStream("Effectiverights.dll"));
    }

    @Override
    protected List<InputStream> getPowershellModules() {
	return Arrays.asList(getClass().getResourceAsStream("Effectiverights.psm1"));
    }

    // Private

    /**
     * Create a new RegkeyeffectiverightsItem based on the base RegkeyeffectiverightsItem, IPrincipal and mask.
     */
    private RegkeyeffectiverightsItem makeItem(RegkeyeffectiverightsItem base, IPrincipal p, int mask) {
	RegkeyeffectiverightsItem item = Factories.sc.windows.createRegkeyeffectiverightsItem();
	item.setHive(base.getHive());
	item.setKey(base.getKey());
	if (base.isSetWindowsView()) {
	    item.setWindowsView(base.getWindowsView());
	}

	boolean test = IACE.ACCESS_SYSTEM_SECURITY == (IACE.ACCESS_SYSTEM_SECURITY & mask);
	EntityItemBoolType accessSystemSecurity = Factories.sc.core.createEntityItemBoolType();
	accessSystemSecurity.setValue(test ? "1" : "0");
	accessSystemSecurity.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setAccessSystemSecurity(accessSystemSecurity);

	test = IACE.FILE_GENERIC_ALL == (IACE.FILE_GENERIC_ALL & mask);
	EntityItemBoolType genericAll = Factories.sc.core.createEntityItemBoolType();
	genericAll.setValue(test ? "1" : "0");
	genericAll.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setGenericAll(genericAll);

	test = IACE.FILE_GENERIC_EXECUTE == (IACE.FILE_GENERIC_EXECUTE & mask);
	EntityItemBoolType genericExecute = Factories.sc.core.createEntityItemBoolType();
	genericExecute.setValue(test ? "1" : "0");
	genericExecute.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setGenericExecute(genericExecute);

	test = IACE.FILE_GENERIC_READ == (IACE.FILE_GENERIC_READ & mask);
	EntityItemBoolType genericRead = Factories.sc.core.createEntityItemBoolType();
	genericRead.setValue(test ? "1" : "0");
	genericRead.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setGenericRead(genericRead);

	test = IACE.FILE_GENERIC_WRITE == (IACE.FILE_GENERIC_WRITE & mask);
	EntityItemBoolType genericWrite = Factories.sc.core.createEntityItemBoolType();
	genericWrite.setValue(test ? "1" : "0");
	genericWrite.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setGenericWrite(genericWrite);

	test = IACE.DELETE == (IACE.DELETE & mask);
	EntityItemBoolType standardDelete = Factories.sc.core.createEntityItemBoolType();
	standardDelete.setValue(test ? "1" : "0");
	standardDelete.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setStandardDelete(standardDelete);

	test = IACE.READ_CONTROL == (IACE.READ_CONTROL & mask);
	EntityItemBoolType standardReadControl = Factories.sc.core.createEntityItemBoolType();
	standardReadControl.setValue(test ? "1" : "0");
	standardReadControl.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setStandardReadControl(standardReadControl);

	test = IACE.SYNCHRONIZE == (IACE.SYNCHRONIZE & mask);
	EntityItemBoolType standardSynchronize = Factories.sc.core.createEntityItemBoolType();
	standardSynchronize.setValue(test ? "1" : "0");
	standardSynchronize.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setStandardSynchronize(standardSynchronize);

	test = IACE.WRITE_DAC == (IACE.WRITE_DAC & mask);
	EntityItemBoolType standardWriteDac = Factories.sc.core.createEntityItemBoolType();
	standardWriteDac.setValue(test ? "1" : "0");
	standardWriteDac.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setStandardWriteDac(standardWriteDac);

	test = IACE.WRITE_OWNER == (IACE.WRITE_OWNER & mask);
	EntityItemBoolType standardWriteOwner = Factories.sc.core.createEntityItemBoolType();
	standardWriteOwner.setValue(test ? "1" : "0");
	standardWriteOwner.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setStandardWriteOwner(standardWriteOwner);

	test = IACE.KEY_CREATE_LINK == (IACE.KEY_CREATE_LINK & mask);
	EntityItemBoolType keyCreateLink = Factories.sc.core.createEntityItemBoolType();
	keyCreateLink.setValue(test ? "1" : "0");
	keyCreateLink.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyCreateLink(keyCreateLink);

	test = IACE.KEY_CREATE_SUB_KEY == (IACE.KEY_CREATE_SUB_KEY & mask);
	EntityItemBoolType keyCreateSubKey = Factories.sc.core.createEntityItemBoolType();
	keyCreateSubKey.setValue(test ? "1" : "0");
	keyCreateSubKey.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyCreateSubKey(keyCreateSubKey);

	test = IACE.KEY_ENUMERATE_SUB_KEYS == (IACE.KEY_ENUMERATE_SUB_KEYS & mask);
	EntityItemBoolType keyEnumerateSubKeys = Factories.sc.core.createEntityItemBoolType();
	keyEnumerateSubKeys.setValue(test ? "1" : "0");
	keyEnumerateSubKeys.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyEnumerateSubKeys(keyEnumerateSubKeys);

	test = IACE.KEY_NOTIFY == (IACE.KEY_NOTIFY & mask);
	EntityItemBoolType keyNotify = Factories.sc.core.createEntityItemBoolType();
	keyNotify.setValue(test ? "1" : "0");
	keyNotify.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyNotify(keyNotify);

	test = IACE.KEY_QUERY_VALUE == (IACE.KEY_QUERY_VALUE & mask);
	EntityItemBoolType keyQueryValue = Factories.sc.core.createEntityItemBoolType();
	keyQueryValue.setValue(test ? "1" : "0");
	keyQueryValue.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyQueryValue(keyQueryValue);

	test = IACE.KEY_SET_VALUE == (IACE.KEY_SET_VALUE & mask);
	EntityItemBoolType keySetValue = Factories.sc.core.createEntityItemBoolType();
	keySetValue.setValue(test ? "1" : "0");
	keySetValue.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeySetValue(keySetValue);

	test = IACE.KEY_WOW64_32_KEY == (IACE.KEY_WOW64_32_KEY & mask);
	EntityItemBoolType keyWow6432Key = Factories.sc.core.createEntityItemBoolType();
	keyWow6432Key.setValue(test ? "1" : "0");
	keyWow6432Key.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyWow6432Key(keyWow6432Key);

	test = IACE.KEY_WOW64_64_KEY == (IACE.KEY_WOW64_64_KEY & mask);
	EntityItemBoolType keyWow6464Key = Factories.sc.core.createEntityItemBoolType();
	keyWow6464Key.setValue(test ? "1" : "0");
	keyWow6464Key.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyWow6464Key(keyWow6464Key);

	test = IACE.KEY_WOW64_RES == (IACE.KEY_WOW64_RES & mask);
	EntityItemBoolType keyWow64Res = Factories.sc.core.createEntityItemBoolType();
	keyWow64Res.setValue(test ? "1" : "0");
	keyWow64Res.setDatatype(SimpleDatatypeEnumeration.BOOLEAN.value());
	item.setKeyWow64Res(keyWow64Res);

	EntityItemStringType trusteeName = Factories.sc.core.createEntityItemStringType();
	if (p.isBuiltin()) {
	    trusteeName.setValue(p.getName());
	} else {
	    trusteeName.setValue(p.getNetbiosName());
	}
	item.setTrusteeName(trusteeName);

	EntityItemStringType trusteeSid = Factories.sc.core.createEntityItemStringType();
	trusteeSid.setValue(p.getSid());
	item.setTrusteeSid(trusteeSid);
	return item;
    }
}

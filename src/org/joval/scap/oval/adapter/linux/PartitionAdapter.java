// Copyright (C) 2012 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.scap.oval.adapter.linux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jsaf.intf.system.ISession;
import jsaf.intf.unix.system.IUnixSession;
import jsaf.util.SafeCLI;
import jsaf.util.StringTools;

import scap.oval.common.MessageType;
import scap.oval.common.MessageLevelEnumeration;
import scap.oval.common.OperationEnumeration;
import scap.oval.common.SimpleDatatypeEnumeration;
import scap.oval.definitions.core.ObjectType;
import scap.oval.definitions.linux.PartitionObject;
import scap.oval.systemcharacteristics.core.ItemType;
import scap.oval.systemcharacteristics.core.EntityItemIntType;
import scap.oval.systemcharacteristics.core.EntityItemStringType;
import scap.oval.systemcharacteristics.core.FlagEnumeration;
import scap.oval.systemcharacteristics.core.StatusEnumeration;
import scap.oval.systemcharacteristics.linux.PartitionItem;

import org.joval.intf.plugin.IAdapter;
import org.joval.scap.oval.CollectException;
import org.joval.scap.oval.Factories;
import org.joval.util.JOVALMsg;

/**
 * Retrieves linux:partition items.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class PartitionAdapter implements IAdapter {
    private IUnixSession session;
    private HashMap<String, PartitionItem> partitions;
    private HashMap<PartitionItem, MessageType> messages;
    private CollectException error;

    // Implement IAdapter

    public Collection<Class> init(ISession session, Collection<Class> notapplicable) {
	Collection<Class> classes = new ArrayList<Class>();
	if (session instanceof IUnixSession && ((IUnixSession)session).getFlavor() == IUnixSession.Flavor.LINUX) {
	    this.session = (IUnixSession)session;
	    classes.add(PartitionObject.class);
	} else {
	    notapplicable.add(PartitionObject.class);
	}
	return classes;
    }

    public Collection<PartitionItem> getItems(ObjectType obj, IRequestContext rc) throws CollectException {
	init();
	PartitionObject pObj = (PartitionObject)obj;
	Collection<PartitionItem> items = new ArrayList<PartitionItem>();
	try {
	    String mountPoint = (String)pObj.getMountPoint().getValue();
	    OperationEnumeration op = pObj.getMountPoint().getOperation();
	    Pattern p = null;
	    if (op == OperationEnumeration.PATTERN_MATCH) {
		p = StringTools.pattern(mountPoint);
	    }
	    for (String mount : partitions.keySet()) {
		switch(op) {
		  case EQUALS:
		    if (mount.equals(mountPoint)) {
			items.add(partitions.get(mount));
		    }
		    break;
		  case CASE_INSENSITIVE_EQUALS:
		    if (mount.equalsIgnoreCase(mountPoint)) {
			items.add(partitions.get(mount));
		    }
		    break;
		  case NOT_EQUAL:
		    if (!mount.equals(mountPoint)) {
			items.add(partitions.get(mount));
		    }
		    break;
		  case PATTERN_MATCH:
		    if (p.matcher(mount).find()) {
			items.add(partitions.get(mount));
		    }
		    break;
		  default:
		    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_OPERATION, op);
		    throw new CollectException(msg, FlagEnumeration.NOT_COLLECTED);
		}
	    }
	} catch (PatternSyntaxException e) {
	    MessageType msg = Factories.common.createMessageType();
	    msg.setLevel(MessageLevelEnumeration.ERROR);
	    msg.setValue(JOVALMsg.getMessage(JOVALMsg.ERROR_PATTERN, e.getMessage()));
	    rc.addMessage(msg);
	    session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	}
	for (PartitionItem item : items) {
	    if (messages.containsKey(item)) {
		rc.addMessage(messages.get(item));
	    }
	}
	return items;
    }

    // Private

    /**
     * Idempotent
     */
    private void init() throws CollectException {
	if (error != null) {
	    throw error;
	} else if (partitions != null) {
	    return;
	}

	partitions = new HashMap<String, PartitionItem>();
	messages = new HashMap<PartitionItem, MessageType>();
	try {
	    Map<String, Device> devices = getBlockDevices();
	    for (Mount mount : getMounts()) {
		if (!mount.mountPoint.startsWith("/")) {
		    continue; // skip
		}

		PartitionItem item = Factories.sc.linux.createPartitionItem();

		for (String mountOption : mount.options) {
		    EntityItemStringType option = Factories.sc.core.createEntityItemStringType();
		    option.setValue(mountOption);
		    item.getMountOptions().add(option);
		}

		EntityItemStringType mountPoint = Factories.sc.core.createEntityItemStringType();
		mountPoint.setValue(mount.mountPoint);
		item.setMountPoint(mountPoint);

		try {
		    Space space = new Space(mount.mountPoint);

		    EntityItemIntType spaceLeft = Factories.sc.core.createEntityItemIntType();
		    spaceLeft.setDatatype(SimpleDatatypeEnumeration.INT.value());
		    spaceLeft.setValue(Integer.toString(space.available));
		    item.setSpaceLeft(spaceLeft);

		    EntityItemIntType spaceUsed = Factories.sc.core.createEntityItemIntType();
		    spaceUsed.setDatatype(SimpleDatatypeEnumeration.INT.value());
		    spaceUsed.setValue(Integer.toString(space.used));
		    item.setSpaceUsed(spaceUsed);

		    EntityItemIntType totalSpace = Factories.sc.core.createEntityItemIntType();
		    totalSpace.setDatatype(SimpleDatatypeEnumeration.INT.value());
		    totalSpace.setValue(Integer.toString(space.capacity));
		    item.setTotalSpace(totalSpace);
		} catch (SpaceException e) {
		    MessageType msg = Factories.common.createMessageType();
		    msg.setLevel(MessageLevelEnumeration.WARNING);
		    msg.setValue(e.getMessage());
		    messages.put(item, msg);

		    EntityItemIntType spaceLeft = Factories.sc.core.createEntityItemIntType();
		    spaceLeft.setDatatype(SimpleDatatypeEnumeration.INT.value());
		    spaceLeft.setStatus(StatusEnumeration.ERROR);
		    item.setSpaceLeft(spaceLeft);

		    EntityItemIntType spaceUsed = Factories.sc.core.createEntityItemIntType();
		    spaceUsed.setDatatype(SimpleDatatypeEnumeration.INT.value());
		    spaceUsed.setStatus(StatusEnumeration.ERROR);
		    item.setSpaceUsed(spaceUsed);

		    EntityItemIntType totalSpace = Factories.sc.core.createEntityItemIntType();
		    totalSpace.setDatatype(SimpleDatatypeEnumeration.INT.value());
		    totalSpace.setStatus(StatusEnumeration.ERROR);
		    item.setTotalSpace(totalSpace);
		}

		if (devices.containsKey(mount.deviceName)) {
		    Device dev = devices.get(mount.deviceName);

		    EntityItemStringType device = Factories.sc.core.createEntityItemStringType();
		    device.setValue(dev.name);
		    item.setDevice(device);

		    EntityItemStringType fsType = Factories.sc.core.createEntityItemStringType();
		    fsType.setValue(dev.type);
		    item.setFsType(fsType);

		    EntityItemStringType uuid = Factories.sc.core.createEntityItemStringType();
		    uuid.setValue(dev.uuid);
		    item.setUuid(uuid);
		} else {
		    EntityItemStringType device = Factories.sc.core.createEntityItemStringType();
		    device.setValue(mount.deviceName);
		    item.setDevice(device);

		    EntityItemStringType fsType = Factories.sc.core.createEntityItemStringType();
		    fsType.setValue(mount.type);
		    item.setFsType(fsType);

		    EntityItemStringType uuid = Factories.sc.core.createEntityItemStringType();
		    uuid.setStatus(StatusEnumeration.DOES_NOT_EXIST);
		    item.setUuid(uuid);
		}
		partitions.put(mount.mountPoint, item);
	    }
	} catch (Exception e) {
	    session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	    error = new CollectException(e.getMessage(), FlagEnumeration.ERROR);
	    throw error;
	}
    }

    /**
     * List all the active mounts on the machine.
     */
    List<Mount> getMounts() throws Exception {
	List<Mount> mounts = new ArrayList<Mount>();
	for (String line : SafeCLI.multiLine("/bin/mount", session, IUnixSession.Timeout.S)) {
	    if (line.length() > 0) {
		try {
		    mounts.add(new Mount(line));
		} catch (IllegalArgumentException e) {
		    session.getLogger().warn(JOVALMsg.ERROR_LINUX_PARTITION, "Mount", e.getMessage());
		}
	    }
	}
	return mounts;
    }

    /**
     * Get the block devices on the machine, indexed by name.
     */
    Map<String, Device> getBlockDevices() throws Exception {
	Map<String, Device> devices = new HashMap<String, Device>();
	for (String line : SafeCLI.multiLine("/sbin/blkid", session, IUnixSession.Timeout.M)) {
	    if (line.length() > 0) {
		try {
		    Device device = new Device(line);
		    devices.put(device.name, device);
		} catch (IllegalArgumentException e) {
		    session.getLogger().warn(JOVALMsg.ERROR_LINUX_PARTITION, "Device", e.getMessage());
		}
	    }
	}
	return devices;
    }

    class Device {
	String name;
	String uuid;
	String type;
	String label;

	Device(String line) throws IllegalArgumentException {
	    int ptr = line.indexOf(":");
	    if (ptr == -1) {
		throw new IllegalArgumentException(line);
	    } else {
		name = line.substring(0,ptr);
		String data = line.substring(ptr+1);
		while ((ptr = data.indexOf("=")) != -1) {
		    String attr = data.substring(0,ptr).trim();
		    data = data.substring(ptr+1);
		    String value = null;
		    if (data.startsWith("\"")) {
			data = data.substring(1);
			ptr = data.indexOf("\"");
			if (ptr == -1) {
			    throw new IllegalArgumentException(line);
			}
			value = data.substring(0, ptr);
			data = data.substring(ptr+1);
		    } else {
			ptr = data.indexOf(" ");
			if (ptr == -1) {
			    throw new IllegalArgumentException(line);
			}
			value = data.substring(0, ptr);
			data = data.substring(ptr+1);
		    }
		    if ("LABEL".equalsIgnoreCase(attr)) {
			label = value;
		    } else if ("UUID".equalsIgnoreCase(attr)) {
			uuid = value;
		    } else if ("TYPE".equalsIgnoreCase(attr)) {
			type = value;
		    }
		}
	    }
	}
    }

    class Mount {
	String deviceName;
	String mountPoint;
	String type;
	List<String> options;

	Mount(String line) throws IllegalArgumentException {
	    StringTokenizer tok = new StringTokenizer(line);
	    if (tok.countTokens() >= 6) {
		deviceName = tok.nextToken();
		tok.nextToken(); // on
		mountPoint = tok.nextToken();
		tok.nextToken(); // type
		type = tok.nextToken();
		String optionText = tok.nextToken();
		if (optionText.startsWith("(") && optionText.endsWith(")")) {
		    optionText = optionText.substring(1, optionText.length()-1);
		}
		options = StringTools.toList(optionText.split(","));
	    } else {
		throw new IllegalArgumentException(line);
	    }
	}
    }

    class Space {
	int available, used, capacity;

	Space(String mountPoint) throws Exception {
	    int lineNum = 0;
	    String cmd = new StringBuffer("/bin/df --direct -P ").append(mountPoint).toString();
	    SafeCLI.ExecData data = SafeCLI.execData(cmd, null, session, session.getTimeout(IUnixSession.Timeout.S));
	    if (data.getExitCode() == 0) {
		for (String line : data.getLines()) {
		    line = line.trim();
		    if (line.length() > 0 && lineNum++ > 0) {
			StringTokenizer tok = new StringTokenizer(line);
			if (tok.countTokens() >= 4) {
			    tok.nextToken(); // mount point
			    capacity = Integer.parseInt(tok.nextToken());
			    used = Integer.parseInt(tok.nextToken());
			    available = Integer.parseInt(tok.nextToken());
			}
			break;
		    }
		}
	    } else {
		throw new SpaceException(new String(data.getData(), StringTools.UTF8).trim());
	    }
	}
    }

    class SpaceException extends Exception {
	SpaceException(String message) {
	    super(message);
	}
    }
}

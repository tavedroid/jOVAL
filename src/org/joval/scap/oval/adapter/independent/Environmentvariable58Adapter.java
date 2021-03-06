// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.scap.oval.adapter.independent;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.io.InputStream;
import java.io.IOException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jsaf.intf.io.IFile;
import jsaf.intf.io.IReader;
import jsaf.intf.system.IComputerSystem;
import jsaf.intf.system.IEnvironment;
import jsaf.intf.system.ISession;
import jsaf.intf.unix.system.IUnixSession;
import jsaf.intf.windows.powershell.IRunspace;
import jsaf.intf.windows.system.IWindowsSession;
import jsaf.provider.windows.powershell.PowershellException;
import jsaf.util.Base64;
import jsaf.util.Environment;
import jsaf.util.SafeCLI;
import jsaf.util.StringTools;

import scap.oval.common.MessageType;
import scap.oval.common.MessageLevelEnumeration;
import scap.oval.common.OperationEnumeration;
import scap.oval.common.SimpleDatatypeEnumeration;
import scap.oval.definitions.core.ObjectType;
import scap.oval.definitions.independent.Environmentvariable58Object;
import scap.oval.systemcharacteristics.core.ItemType;
import scap.oval.systemcharacteristics.core.EntityItemAnySimpleType;
import scap.oval.systemcharacteristics.core.EntityItemIntType;
import scap.oval.systemcharacteristics.core.EntityItemStringType;
import scap.oval.systemcharacteristics.core.FlagEnumeration;
import scap.oval.systemcharacteristics.independent.Environmentvariable58Item;

import org.joval.intf.plugin.IAdapter;
import org.joval.scap.oval.CollectException;
import org.joval.scap.oval.Factories;
import org.joval.util.JOVALMsg;
import org.joval.xml.XSITools;

/**
 * Evaluates Environmentvariable OVAL tests.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class Environmentvariable58Adapter implements IAdapter {
    private IComputerSystem session;
    private IEnvironmentBuilder builder;

    // Implement IAdapter

    public Collection<Class> init(ISession session, Collection<Class> notapplicable) {
	Collection<Class> classes = new ArrayList<Class>();
	switch(session.getType()) {
	  case UNIX:
	  case WINDOWS:
	    this.session = (IComputerSystem)session;
	    classes.add(Environmentvariable58Object.class);
	    break;

	  default:
	    notapplicable.add(Environmentvariable58Object.class);
	    break;
	}
	return classes;
    }

    public Collection<? extends ItemType> getItems(ObjectType obj, IRequestContext rc) throws CollectException {
	if (builder == null) {
	    try {
		builder = getEnvironmentBuilder();
	    } catch (CollectException e) {
		throw e;
	    } catch (Exception e) {
		session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
		throw new CollectException(e, FlagEnumeration.ERROR);
	    }
	}

	//
	// First, create a map of process environments matching the specified PID
	//
	Environmentvariable58Object eObj = (Environmentvariable58Object)obj;
	HashMap<String, IEnvironment> environments = new HashMap<String, IEnvironment>();
	if (!XSITools.isNil(eObj.getPid()) && ((String)eObj.getPid().getValue().getValue()).length() > 0) {
	    OperationEnumeration op = eObj.getPid().getValue().getOperation();
	    String pid = (String)eObj.getPid().getValue().getValue();
	    try {
		switch(op) {
		  case EQUALS:
		    environments.put(pid, builder.getProcessEnvironment(Integer.parseInt(pid)));
		    break;
		  case NOT_EQUAL:
		    for (int i : builder.listProcesses()) {
			if (i != Integer.parseInt(pid)) {
			    environments.put(Integer.toString(i), builder.getProcessEnvironment(i));
			}
		    }
		    break;
		  case GREATER_THAN:
		    for (int i : builder.listProcesses()) {
			if (i > Integer.parseInt(pid)) {
			    environments.put(Integer.toString(i), builder.getProcessEnvironment(i));
			}
		    }
		    break;
		  case GREATER_THAN_OR_EQUAL:
		    for (int i : builder.listProcesses()) {
			if (i >= Integer.parseInt(pid)) {
			    environments.put(Integer.toString(i), builder.getProcessEnvironment(i));
			}
		    }
		    break;
		  case LESS_THAN:
		    for (int i : builder.listProcesses()) {
			if (i < Integer.parseInt(pid)) {
			    environments.put(Integer.toString(i), builder.getProcessEnvironment(i));
			}
		    }
		    break;
		  case LESS_THAN_OR_EQUAL:
		    for (int i : builder.listProcesses()) {
			if (i <= Integer.parseInt(pid)) {
			    environments.put(Integer.toString(i), builder.getProcessEnvironment(i));
			}
		    }
		    break;
		  default:
		    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_OPERATION, op);
		    throw new CollectException(msg, FlagEnumeration.NOT_COLLECTED);
		}
	    } catch (CollectException e) {
		throw e;
	    } catch (Exception e) {
		MessageType msg = Factories.common.createMessageType();
		msg.setLevel(MessageLevelEnumeration.ERROR);
		msg.setValue(e.getMessage());
		rc.addMessage(msg);
		session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	    }
	} else if (session.getHostname().equals(ISession.LOCALHOST)) {
	    //
	    // In this case, we're supposed to use the tool's PID, and by extension, the tool's environment.
	    // To get the PID, we use a hack to obtain it from the name of the RuntimeMXBean.
	    //
	    String name = ManagementFactory.getRuntimeMXBean().getName();
	    int ptr = name.indexOf("@");
	    if (ptr != -1) {
		try {
		int pid = Integer.parseInt(name.substring(0,ptr));
		    environments.put(Integer.toString(pid), builder.getProcessEnvironment(pid));
		} catch (Exception e) {
		    MessageType msg = Factories.common.createMessageType();
		    msg.setLevel(MessageLevelEnumeration.ERROR);
		    msg.setValue(e.getMessage());
		    rc.addMessage(msg);
		    session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
		}
	    }
	} else {
	    MessageType msg = Factories.common.createMessageType();
	    msg.setLevel(MessageLevelEnumeration.INFO);
	    msg.setValue(JOVALMsg.getMessage(JOVALMsg.STATUS_NO_PROCESS, session.getHostname()));
	    rc.addMessage(msg);
	    environments.put("", session.getEnvironment());
	}

	//
	// If no environments were found, then just quit now.
	//
	if (environments.size() == 0) {
	    @SuppressWarnings("unchecked")
	    Collection<Environmentvariable58Item> empty = (Collection<Environmentvariable58Item>)Collections.EMPTY_LIST;
	    return empty;
	}

	//
	// Then, filter the environment data according to the specified variable name.
	//
	Collection<Environmentvariable58Item> items = new ArrayList<Environmentvariable58Item>();
	OperationEnumeration op = eObj.getName().getOperation();
	String name = (String)eObj.getName().getValue();
	try {
	    switch(op) {
	      case EQUALS:
		for (Map.Entry<String, IEnvironment> env : environments.entrySet()) {
		    String pid = env.getKey();
		    String value = env.getValue().getenv(name);
		    if (value != null) {
			items.add(makeItem(pid, name, value));
		    }
		}
		break;
	      case CASE_INSENSITIVE_EQUALS:
		for (Map.Entry<String, IEnvironment> env : environments.entrySet()) {
		    String pid = env.getKey();
		    for (String var : env.getValue()) {
			if (name.equalsIgnoreCase(var)) {
			    items.add(makeItem(pid, var, env.getValue().getenv(var)));
			}
		    }
		}
		break;
	      case NOT_EQUAL:
		for (Map.Entry<String, IEnvironment> env : environments.entrySet()) {
		    String pid = env.getKey();
		    for (String var : env.getValue()) {
			if (!name.equals(var)) {
			    items.add(makeItem(pid, var, env.getValue().getenv(var)));
			}
		    }
		}
		break;
	      case CASE_INSENSITIVE_NOT_EQUAL:
		for (Map.Entry<String, IEnvironment> env : environments.entrySet()) {
		    String pid = env.getKey();
		    for (String var : env.getValue()) {
			if (!name.equalsIgnoreCase(var)) {
			    items.add(makeItem(pid, var, env.getValue().getenv(var)));
			}
		    }
		}
		break;
	      case PATTERN_MATCH:
		Pattern p = StringTools.pattern(name);
		for (Map.Entry<String, IEnvironment> env : environments.entrySet()) {
		    String pid = env.getKey();
		    for (String var : env.getValue()) {
			if (p.matcher(var).find()) {
			    items.add(makeItem(pid, var, env.getValue().getenv(var)));
			}
		    }
		}
		break;
	      default:
		String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_OPERATION, op);
		throw new CollectException(msg, FlagEnumeration.NOT_COLLECTED);
	    }
	} catch (PatternSyntaxException e) {
	    MessageType msg = Factories.common.createMessageType();
	    msg.setLevel(MessageLevelEnumeration.ERROR);
	    msg.setValue(JOVALMsg.getMessage(JOVALMsg.ERROR_PATTERN, e.getMessage()));
	    rc.addMessage(msg);
	    session.getLogger().warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	}
	return items;
    }

    // Private

    private Environmentvariable58Item makeItem(String pid, String name, String value) {
	Environmentvariable58Item item = Factories.sc.independent.createEnvironmentvariable58Item();

	if (pid.length() > 0) { // handle the special case with no PID
	    EntityItemIntType pidType = Factories.sc.core.createEntityItemIntType();
	    pidType.setDatatype(SimpleDatatypeEnumeration.INT.value());
	    pidType.setValue(pid);
	    item.setPid(pidType);
	}

	EntityItemStringType nameType = Factories.sc.core.createEntityItemStringType();
	nameType.setValue(name);
	item.setName(nameType);

	EntityItemAnySimpleType valueType = Factories.sc.core.createEntityItemAnySimpleType();
	valueType.setValue(value);
	item.setValue(valueType);

	return item;
    }

    public interface IEnvironmentBuilder {
	IEnvironment getProcessEnvironment(int pid) throws Exception;
	int[] listProcesses() throws Exception;
    }

    /**
     * Create a platform-specific IEnvironmentBuilder.
     */
    private IEnvironmentBuilder getEnvironmentBuilder() throws Exception {
	switch(session.getType()) {
	  case UNIX:
	    IUnixSession us = (IUnixSession)session;
	    switch(us.getFlavor()) {
	      case AIX:
		return new AixEnvironmentBuilder(us);
	      case LINUX:
		return new LinuxEnvironmentBuilder(us);
	      case SOLARIS:
		return new SolarisEnvironmentBuilder(us);
	      default:
		throw new Exception(JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_UNIX_FLAVOR, us.getFlavor()));
	    }

	  case WINDOWS:
	    return new WindowsEnvironmentBuilder((IWindowsSession)session);

	  default:
	    String msg = JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_SESSION_TYPE, session.getType());
	    throw new CollectException(msg, FlagEnumeration.NOT_COLLECTED);
	}
    }

    /**
     * See: http://yong321.freeshell.org/computer/ProcEnv.txt
     */
    abstract class UnixEnvironmentBuilder implements IEnvironmentBuilder {
	protected IUnixSession session;

	UnixEnvironmentBuilder(IUnixSession session) {
	    this.session = session;
	}

	public int[] listProcesses() throws Exception {
	    ArrayList<Integer> ids = new ArrayList<Integer>();
	    for (String line : SafeCLI.multiLine("ps -eo pid", session, IUnixSession.Timeout.S)) {
		if (!line.trim().equalsIgnoreCase("PID")) { // skip the header row
		    try {
			ids.add(new Integer(line.trim()));
		    } catch (NumberFormatException e) {
		    }
		}
	    }
	    int[] result = new int[ids.size()];
	    for (int i=0; i < result.length; i++) {
		result[i] = ids.get(i).intValue();
	    }
	    return result;
	}
    }

    class AixEnvironmentBuilder extends UnixEnvironmentBuilder {
	AixEnvironmentBuilder(IUnixSession session) {
	    super(session);
	}

	public IEnvironment getProcessEnvironment(int pid) throws Exception {
	    for (String line : SafeCLI.multiLine("ps eww " + pid, session, IUnixSession.Timeout.S)) {
		line = line.trim();
		if (line.startsWith(Integer.toString(pid))) {
		    StringTokenizer tok = new StringTokenizer(line);
		    Stack<String> stack = new Stack<String>();
		    while (tok.hasMoreTokens()) {
			stack.push(tok.nextToken());
		    }
		    Map<String, String> processEnv = new HashMap<String, String>();
		    while (!stack.empty()) {
			String token = stack.pop();
			int ptr = token.indexOf("=");
			if (ptr > 0) {
			    String key = token.substring(0,ptr);
			    String val = token.substring(ptr+1);
			    processEnv.put(key, val);
			} else {
			    break; // no more environment variables
			}
		    }
		    return new Environment(processEnv);
		}
	    }
	    throw new NoSuchElementException(Integer.toString(pid));
	}
    }

    class LinuxEnvironmentBuilder extends UnixEnvironmentBuilder {
	LinuxEnvironmentBuilder(IUnixSession session) {
	    super(session);
	}

	public IEnvironment getProcessEnvironment(int pid) throws Exception {
	    String path = "/proc/" + pid + "/environ";
	    IReader reader = null;
	    IFile proc = session.getFilesystem().getFile(path);
	    if (proc.exists()) {
		Map<String, String> processEnv = new HashMap<String, String>();
		long timeout = session.getTimeout(IUnixSession.Timeout.M);
		byte[] bytes = SafeCLI.execData("cat " + path, null, session, timeout).getData();
		String data = new String(bytes, StringTools.ASCII);
		String delim = new StringBuffer().append((char)127).toString(); // 127 == delimiter char
		for (String pair : data.split(delim)) {
		    int ptr = pair.indexOf("=");
		    if (ptr > 0) {
			String key = pair.substring(0,ptr);
			String val = pair.substring(ptr+1);
			processEnv.put(key, val);
		    }
		}
		return new Environment(processEnv);
	    } else {
		throw new NoSuchElementException(Integer.toString(pid));
	    }
	}
    }

    /**
     * In Solaris 10+, there is the pargs command:
     * http://www.unix.com/hp-ux/112024-how-can-i-get-environment-running-process.html
     */
    class SolarisEnvironmentBuilder extends UnixEnvironmentBuilder {
	SolarisEnvironmentBuilder(IUnixSession session) throws Exception {
	    super(session);
	    BigDecimal VER_5_9 = new BigDecimal("5.9");
	    BigDecimal osVersion = new BigDecimal(SafeCLI.exec("uname -r", session, IUnixSession.Timeout.S));
	    if (osVersion.compareTo(VER_5_9) < 0) {
		throw new Exception(JOVALMsg.getMessage(JOVALMsg.ERROR_UNSUPPORTED_OS_VERSION, osVersion));
	    }
	}

	public IEnvironment getProcessEnvironment(int pid) throws Exception {
	    IFile proc = session.getFilesystem().getFile("/proc/" + pid);
	    if (proc.exists() && proc.isDirectory()) {
		Map<String, String> processEnv = new HashMap<String, String>();
		for (String line : SafeCLI.multiLine("pargs -e " + pid, session, IUnixSession.Timeout.S)) {
		    if (line.startsWith("envp")) {
			String pair = line.substring(line.indexOf(" ")).trim();
			int ptr = pair.indexOf("=");
			if (ptr > 0) {
			    String key = pair.substring(0,ptr);
			    String val = pair.substring(ptr+1);
			    processEnv.put(key, val);
			}
		    }
		}
		return new Environment(processEnv);
	    } else {
		throw new NoSuchElementException(Integer.toString(pid));
	    }
	}
    }

    class WindowsEnvironmentBuilder implements IEnvironmentBuilder {
	private HashSet<Integer> process32, process64, inaccessible;
	private IRunspace rs, rs32;

	WindowsEnvironmentBuilder(IWindowsSession session) throws Exception {
	    process32 = new HashSet<Integer>();
	    process64 = new HashSet<Integer>();
	    inaccessible = new HashSet<Integer>();
	    String cmd = "List-Processes | Transfer-Encode";
	    String data = new String(Base64.decode(getRunspace(session.getNativeView()).invoke(cmd)), StringTools.UTF8);
	    for (String line : data.split("\r\n")) {
		int ptr = line.indexOf(":");
		if (ptr > 0) {
		    Integer pid = new Integer(line.substring(0,ptr));
		    if (line.endsWith("32")) {
			process32.add(pid);
		    } else if (line.endsWith("64")) {
			process64.add(pid);
		    } else {
			inaccessible.add(pid);
		    }
		}
	    }
	}

	public int[] listProcesses() throws Exception {
	    List<Integer> processes = new ArrayList<Integer>();
	    processes.addAll(process32);
	    processes.addAll(process64);
	    int[] result = new int[processes.size()];
	    int i=0;
	    for (Integer id : processes) {
		result[i++] = id.intValue();
	    }
	    return result;
	}

	public IEnvironment getProcessEnvironment(int pid) throws Exception {
	    Integer id = new Integer(pid);
	    String cmd = "Get-ProcessEnvironment -ProcessId " + pid + " | Transfer-Encode";
	    if (process32.contains(id)) {
		byte[] buff = Base64.decode(getRunspace(IWindowsSession.View._32BIT).invoke(cmd));
		return toEnvironment(new String(buff, StringTools.UTF8));
	    } else if (process64.contains(id)) {
		byte[] buff = Base64.decode(getRunspace(IWindowsSession.View._64BIT).invoke(cmd));
		return toEnvironment(new String(buff, StringTools.UTF8));
	    } else if (inaccessible.contains(id)) {
		throw new AccessControlException(id.toString());
	    } else {
		throw new NoSuchElementException(id.toString());
	    }
	}

	// Private

	private Environment toEnvironment(String data) {
	    Map<String, String> processEnv = new HashMap<String, String>();
	    if (data != null) {
		String var = null;
		for (String line : data.split("\r\n")) {
		    int ptr = line.indexOf("=");
		    if (ptr == -1) {
			if (var != null) { // line continuation case
			    StringBuffer sb = new StringBuffer(processEnv.get(var));
			    sb.append(line);
			    processEnv.put(var, sb.toString());
			}
		    } else {
			var = line.substring(0,ptr);
			processEnv.put(var, line.substring(ptr+1));
		    }
		}
	    }
	    return new Environment(processEnv, true);
	}

	private IRunspace getRunspace(IWindowsSession.View view) throws Exception {
	    switch(view) {
	      case _32BIT:
		if (rs32 != null && rs32.isAlive()) {
		    return rs32;
		} else {
		    return rs32 = createRunspace(view);
		}
	      default:
		if (rs != null && rs.isAlive()) {
		    return rs;
		} else {
		    return rs = createRunspace(view);
		}
	    }
	}

	private IRunspace createRunspace(IWindowsSession.View view) throws Exception {
	    IRunspace runspace = null;
	    for (IRunspace rs : ((IWindowsSession)session).getRunspacePool().enumerate()) {
		if (rs.getView() == view) {
		    runspace = rs;
		    break;
		}
	    }
	    if (runspace == null) {
		runspace = ((IWindowsSession)session).getRunspacePool().spawn(view);
	    }
	    runspace.loadAssembly(getClass().getResourceAsStream("Environmentvariable58.dll"));
	    runspace.loadModule(getClass().getResourceAsStream("Environmentvariable58.psm1"));
	    return runspace;
	}
    }
}

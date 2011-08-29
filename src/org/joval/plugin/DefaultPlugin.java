// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.plugin;

import java.util.List;

import org.joval.discovery.Local;
import org.joval.intf.di.IJovaldiConfiguration;
import org.joval.intf.system.ISession;
import org.joval.intf.windows.system.IWindowsSession;
import org.joval.oval.di.BasePlugin;

/**
 * Implementation of an IJovaldiPlugin for the Windows operating system.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class DefaultPlugin extends BasePlugin {
    /**
     * Create a plugin for scanning or test evaluation.
     */
    public DefaultPlugin() {
	super();
	session = Local.getSession();
    }

    // Implement IJovaldiPlugin

    public boolean configure(String[] args, IJovaldiConfiguration jDIconfig) {
	for (int i=0; i < args.length; i++) {
	    if (args[i].equals("-redirect64")) {
		boolean redirect64 = "true".equals(args[++i]);
		if (session.getType() == ISession.Type.WINDOWS) {
		    ((IWindowsSession)session).set64BitRedirect(redirect64);
		}
	    }
	}
	return true;
    }
}

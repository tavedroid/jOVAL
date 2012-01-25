// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import javax.security.auth.login.LoginException;

import org.joval.discovery.SessionFactory;
import org.joval.intf.identity.ICredential;
import org.joval.intf.identity.ICredentialStore;
import org.joval.intf.identity.ILocked;
import org.joval.intf.system.IBaseSession;
import org.joval.intf.system.IEnvironment;
import org.joval.intf.system.ISession;
import org.joval.intf.windows.system.IWindowsSession;
import org.joval.os.embedded.system.IosSession;
import org.joval.os.unix.remote.system.UnixSession;
import org.joval.oval.OvalException;
import org.joval.ssh.system.SshSession;
import org.joval.util.JOVALMsg;
import org.joval.util.JOVALSystem;

/**
 * Implementation of an IPlugin for remote scanning.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class RemotePlugin extends BasePlugin {
    private static SessionFactory sessionFactory = new SessionFactory();
    private static ICredentialStore cs;

    /**
     * Set a location where the RemotePlugin class can store host discovery information.
     */
    public static void setDataDirectory(File dir) throws IOException {
	sessionFactory.setDataDirectory(dir);
    }

    /**
     * Set an SSH gateway for the plugin.  If set, the RemotePlugin will not be able to connect to Windows machines.
     */
    public static void setSshGateway(SshSession gateway) {
	sessionFactory.setSshGateway(gateway);
    }

    /**
     * Set the ICredentialStore for the RemotePlugin class.
     */
    public static void setCredentialStore(ICredentialStore cs) {
	RemotePlugin.cs = cs;
    }

    protected String hostname;
    private ICredential cred;

    /**
     * Create a remote plugin.
     */
    public RemotePlugin(String hostname) {
	super();
	this.hostname = hostname;
    }

    // Implement IPlugin

    /**
     * Creation of the session is deferred until this point because it can be a blocking, time-consuming operation.  By
     * doing that as part of the connect routine, it happens inside of the IEngine's run method, which can be wrapped inside
     * a Thread.
     */
    public void connect() throws OvalException {
	if (hostname != null) {
	    try {
		IBaseSession base = sessionFactory.createSession(hostname);
		JOVALSystem.configureSession(base);
		base.setLogger(logger);
		setCredential(base);

		IBaseSession.Type type = base.getType();
		switch(type) {
		  case WINDOWS:
		    session = (IWindowsSession)base;
		    break;

		  case UNIX:
		    session = new UnixSession((SshSession)base);
		    break;

		  case CISCO_IOS:
		    base.disconnect();
		    session = new IosSession((SshSession)base);
		    break;

		  default:
		    base.disconnect();
		    throw new Exception(JOVALSystem.getMessage(JOVALMsg.ERROR_SESSION_TYPE, type));
	        }

		JOVALSystem.configureSession(session);
		setCredential(session);
	    } catch (Exception e) {
		throw new OvalException(e);
	    }
	} else {
	    throw new OvalException(JOVALSystem.getMessage(JOVALMsg.ERROR_SESSION_TARGET));
	}

	super.connect();
    }

    //Private

    private void setCredential(IBaseSession base) throws Exception {
	if (base instanceof ILocked) {
	    if (cs == null) {
		throw new Exception(JOVALSystem.getMessage(JOVALMsg.ERROR_SESSION_CREDENTIAL_STORE, hostname));
	    } else {
		ICredential cred = cs.getCredential(base);
		if (cred == null) {
		    throw new LoginException(JOVALSystem.getMessage(JOVALMsg.ERROR_SESSION_CREDENTIAL));
		} else if (((ILocked)base).unlock(cred)) {
		    logger.debug(JOVALMsg.STATUS_CREDENTIAL_SET, hostname);
		} else {
		    String baseName = base.getClass().getName();
		    String credName = cred.getClass().getName();
		    throw new Exception(JOVALSystem.getMessage(JOVALMsg.ERROR_SESSION_LOCK, credName, baseName));
		}
	    }
	}
    }
}

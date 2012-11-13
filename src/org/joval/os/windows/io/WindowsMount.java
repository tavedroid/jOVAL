// Copyright (C) 2012 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.os.windows.io;

import org.joval.intf.io.IFilesystem;
import org.joval.intf.windows.io.IWindowsFilesystem;
import org.joval.util.StringTools;

/**
 * Windows drive representation.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class WindowsMount implements IFilesystem.IMount {
    /**
     * Determine whether or not the string represents a drive.
     */
    public static boolean isDrive(String s) {
        if (s.length() == 2) {
            return StringTools.isLetter(s.charAt(0)) && s.charAt(1) == ':';
        }
        return false;
    }

    private String path;
    private IWindowsFilesystem.FsType type;

    /**
     * Create a new mount given a drive string and a type.
     */
    public WindowsMount(String path, IWindowsFilesystem.FsType type) throws IllegalArgumentException {
	if (isDrive(path)) {
            this.path = path + IWindowsFilesystem.DELIM_STR;
	}
        this.type = type;
    }

    // Implement IFilesystem.IMount

    public String getPath() {
        return path;
    }

    public String getType() {
        return type.value();
    }
}


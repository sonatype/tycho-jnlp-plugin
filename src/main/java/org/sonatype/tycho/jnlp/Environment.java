/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.sonatype.tycho.jnlp;

public class Environment
{
    private String key;

    private String os;

    private String arch;

    public Environment()
    {
    }

    public Environment( String key, String os, String arch )
    {
        this.key = key;
        this.os = os;
        this.arch = arch;
    }

    public String getKey()
    {
        return key;
    }

    public void setKey( String key )
    {
        this.key = key;
    }

    public String getOs()
    {
        return os;
    }

    public void setOs( String os )
    {
        this.os = os;
    }

    public String getArch()
    {
        return arch;
    }

    public void setArch( String arch )
    {
        this.arch = arch;
    }
}

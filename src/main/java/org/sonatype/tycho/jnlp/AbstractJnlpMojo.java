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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.tycho.ArtifactDescriptor;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.ArtifactDependencyWalker;
import org.eclipse.tycho.core.TychoProject;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.XMLWriter;

public abstract class AbstractJnlpMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * @parameter default-value="${project.build.directory}/product/eclipse"
     */
    protected File target;

    /** @component */
    protected PlexusContainer plexus;

    protected ArtifactDependencyWalker getDependencyWalker()
    {
        return getTychoProjectFacet().getDependencyWalker( project );
    }

    protected TychoProject getTychoProjectFacet()
    {
        return getTychoProjectFacet( project.getPackaging() );
    }

    protected TychoProject getTychoProjectFacet( String packaging )
    {
        TychoProject facet;
        try
        {
            facet = plexus.lookup( TychoProject.class, packaging );
        }
        catch ( ComponentLookupException e )
        {
            throw new IllegalStateException( "Could not lookup required component", e );
        }
        return facet;
    }

    protected String getVersion( ArtifactDescriptor artifact )
    {
        String version = artifact.getKey().getVersion();
        ReactorProject project = artifact.getMavenProject();
        if ( project != null )
        {
            version = project.getExpandedVersion();
        }
        return version;
    }

    protected void writeXmlFile( Document document, File file )
        throws MojoExecutionException
    {
        try
        {
            OutputStream os = new BufferedOutputStream( new FileOutputStream( file ) );
            try
            {
                String enc = document.getEncoding() != null ? document.getEncoding() : "UTF-8";
                Writer w = new OutputStreamWriter( os, enc );
                XMLWriter xw = new XMLWriter( w );
                try
                {
                    document.toXML( xw );
                }
                finally
                {
                    xw.flush();
                }
            }
            finally
            {
                IOUtil.close( os );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not write output file " + file.getAbsolutePath(), e );
        }
    }

}

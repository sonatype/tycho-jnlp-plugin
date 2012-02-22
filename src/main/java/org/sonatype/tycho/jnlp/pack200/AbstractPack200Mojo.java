/*******************************************************************************
 * Copyright (c) 2012 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.sonatype.tycho.jnlp.pack200;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.tycho.core.ArtifactDependencyVisitor;
import org.eclipse.tycho.core.FeatureDescription;
import org.eclipse.tycho.core.PluginDescription;
import org.sonatype.tycho.jnlp.AbstractJnlpMojo;

public abstract class AbstractPack200Mojo
    extends AbstractJnlpMojo
{
    public void execute()
        throws MojoExecutionException
    {

        // normalization is only needed when pack200 is used together with signing
        // overall flow
        // 1. pack
        // 2. unpack
        // 3. add or update META-INF/eclipse.inf
        // 4. sign
        // 5. pack
        // To guarantee signature validity, both pack 1. and 5. must use exactly the same Packer properties

        final ArrayList<Exception> exceptions = new ArrayList<Exception>();

        getDependencyWalker().walk( new ArtifactDependencyVisitor()
        {
            @Override
            public boolean visitFeature( FeatureDescription feature )
            {
                return true; // keep visiting
            }

            @Override
            public void visitPlugin( PluginDescription plugin )
            {
                String id = plugin.getKey().getId();
                String version = getVersion( plugin );

                File archive = new File( target, "plugins/" + id + "_" + version + ".jar" );

                if ( archive.isFile() && archive.canWrite() )
                {
                    try
                    {
                        process( archive );
                    }
                    catch ( IOException e )
                    {
                        getLog().warn( "Could not pack200 jar " + archive.getAbsolutePath(), e );
                        exceptions.add( e );
                    }
                }
            }
        } );

        if ( !exceptions.isEmpty() )
        {
            throw new MojoExecutionException( "Could not pack200 some jar files" );
        }
    }

    protected abstract void process( File archive )
        throws IOException;

}

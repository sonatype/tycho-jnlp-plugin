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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.JarFile;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;
import java.util.zip.GZIPOutputStream;

import org.codehaus.plexus.util.IOUtil;

/**
 * http://docs.oracle.com/javase/6/docs/technotes/guides/jweb/tools/pack200.html
 * 
 * @goal pack200-pack
 * @phase package
 */
public class Pack200Pack
    extends AbstractPack200Mojo
{

    /**
     * @parameter default-value="false"
     */
    private boolean deleteUnpackedJars;

    @Override
    protected void process( File jar )
        throws IOException
    {
        JarFile jarFile = new JarFile( jar );
        try
        {
            EclipseInf eclipseInf = EclipseInf.readEclipseInf( jarFile );
            if ( eclipseInf.shouldPack() && eclipseInf.isPackNormalized() )
            {
                getLog().info( "Pack200 packing jar " + jar.getAbsolutePath() );

                File jarpackgz = new File( jar.getCanonicalPath() + ".pack.gz" );
                OutputStream os = new GZIPOutputStream( new BufferedOutputStream( new FileOutputStream( jarpackgz ) ) );
                try
                {
                    Packer packer = Pack200.newPacker();
                    packer.pack( jarFile, os );
                }
                finally
                {
                    IOUtil.close( os );
                }
                if ( deleteUnpackedJars )
                {
                    if ( !jar.delete() )
                    {
                        throw new IOException( "Could not delete jar " + jar.getAbsolutePath() );
                    }
                }
            }
        }
        finally
        {
            try
            {
                jarFile.close();
            }
            catch ( IOException e )
            {
                // ignore
            }
        }
    }
}

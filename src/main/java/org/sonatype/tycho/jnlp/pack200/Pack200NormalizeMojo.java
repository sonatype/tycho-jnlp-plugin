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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;
import java.util.jar.Pack200.Unpacker;

import org.codehaus.plexus.util.IOUtil;

/**
 * @goal pack200-normalize
 * @phase package
 */
public class Pack200NormalizeMojo
    extends AbstractPack200Mojo
{

    @Override
    protected void process( File jar )
        throws IOException
    {
        JarFile jarFile = new JarFile( jar );
        File jarpack = null;
        File jarunpack = null;
        try
        {
            EclipseInf eclipseInf = EclipseInf.readEclipseInf( jarFile );
            if ( eclipseInf.shouldPack() && !eclipseInf.isPackNormalized() && !isSigned( jarFile ) )
            {
                getLog().info( "Pack200 nomalizing jar " + jar.getAbsolutePath() );

                jarpack = File.createTempFile( jar.getName(), ".pack" );

                // 1. pack
                try
                {
                    OutputStream os = new BufferedOutputStream( new FileOutputStream( jarpack ) );
                    try
                    {
                        Packer packer = Pack200.newPacker();
                        packer.pack( jarFile, os );
                    }
                    finally
                    {
                        IOUtil.close( os );
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
                    finally
                    {
                        jarFile = null; // prevent double-close in the outer finally block
                    }
                }

                // 2. unpack
                jarunpack = File.createTempFile( jar.getName(), ".unpack" );
                JarOutputStream jos =
                    new JarOutputStream( new BufferedOutputStream( new FileOutputStream( jarunpack ) ) );
                try
                {
                    Unpacker unpacker = Pack200.newUnpacker();
                    unpacker.unpack( jarpack, jos );
                }
                finally
                {
                    IOUtil.close( jos );
                }

                // 3. add or update META-INF/eclipse.inf
                eclipseInf.setPackNormalized();
                jos = new JarOutputStream( new BufferedOutputStream( new FileOutputStream( jar ) ) );
                try
                {
                    jarFile = new JarFile( jarunpack );
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while ( entries.hasMoreElements() )
                    {
                        JarEntry entry = entries.nextElement();
                        if ( !entry.getName().equals( EclipseInf.PATH_ECLIPSEINF ) )
                        {
                            copyJarEntry( jarFile, entry, jos );
                        }
                    }
                    JarEntry entry = new JarEntry( EclipseInf.PATH_ECLIPSEINF );
                    jos.putNextEntry( entry );
                    jos.write( eclipseInf.toByteArray() );
                    jos.closeEntry();
                }
                finally
                {
                    IOUtil.close( jos );
                }
            }
        }
        finally
        {
            if ( jarFile != null )
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
            if ( jarpack != null )
            {
                jarpack.delete();
            }
            if ( jarunpack != null )
            {
                jarunpack.delete();
            }
        }
    }

    private boolean isSigned( JarFile jarFile )
        throws IOException
    {
        Enumeration<JarEntry> entries = jarFile.entries();
        while ( entries.hasMoreElements() )
        {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if ( name.startsWith( "META-INF/" ) && name.endsWith( ".SF" ) )
            {
                return true;
            }
        }
        return false;
    }

    private void copyJarEntry( JarFile jarFile, JarEntry entry, JarOutputStream jos )
        throws IOException
    {
        jos.putNextEntry( entry );

        InputStream is = jarFile.getInputStream( entry );
        byte[] buf = new byte[4096];
        int n;
        while ( ( n = is.read( buf ) ) != -1 )
        {
            jos.write( buf, 0, n );
        }

        jos.closeEntry();
    }
}

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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.interpolation.InterpolatorFilterReader;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PrefixedPropertiesValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.eclipse.tycho.core.ArtifactDependencyVisitor;
import org.eclipse.tycho.core.PluginDescription;
import org.eclipse.tycho.model.PluginRef;
import org.eclipse.tycho.model.ProductConfiguration;
import org.eclipse.tycho.core.utils.PlatformPropertiesUtils;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.Text;
import de.pdark.decentxml.XMLParser;

/**
 * Generates JNLP File. http://java.sun.com/j2se/1.5.0/docs/guide/javaws/developersguide/syntax.html
 * http://java.sun.com/javase/6/docs/technotes/guides/javaws/developersguide/syntax.html
 * http://java.sun.com/javase/6/docs/technotes/guides/javaws/developersguide/contents.html
 * 
 * @phase package
 * @goal jnlp-file
 */
public class JnlpFileMojo
    extends AbstractJnlpMojo
{
    private static final String CONFIGINI_PROPERTY_PREFIX = "configini";

    private static final String PRODUCT_PROPERTY_PREFIX = "product";

    /**
     * Maps OSGi environment key to Java
     */
    private static final List<Environment> ENVIRONMENTS_MAP = new ArrayList<Environment>();

    private static final String NO_ENVIRONMENT = "";

    static
    {
        // http://lopica.sourceforge.net/os.html
        ENVIRONMENTS_MAP.add( new Environment( getEnvKey( PlatformPropertiesUtils.OS_LINUX,
                                                          PlatformPropertiesUtils.ARCH_X86_64 ), //
                                               "Linux", "amd64" ) );

        ENVIRONMENTS_MAP.add( new Environment( getEnvKey( PlatformPropertiesUtils.OS_LINUX,
                                                          PlatformPropertiesUtils.ARCH_X86 ), //
                                               "Linux", "i386" ) );

        ENVIRONMENTS_MAP.add( new Environment( getEnvKey( PlatformPropertiesUtils.OS_WIN32,
                                                          PlatformPropertiesUtils.ARCH_X86 ), //
                                               "Windows", "x86" ) );

        ENVIRONMENTS_MAP.add( new Environment( getEnvKey( PlatformPropertiesUtils.OS_WIN32,
                                                          PlatformPropertiesUtils.ARCH_X86_64 ), //
                                               "Windows", "amd64" ) );

        ENVIRONMENTS_MAP.add( new Environment( getEnvKey( PlatformPropertiesUtils.OS_MACOSX,
                                                          PlatformPropertiesUtils.ARCH_X86_64 ), //
                                               "Mac", "x86_64" ) );
    }

    /**
     * @parameter default-value="${project.basedir}/src/main/jnlp/install.jnlp"
     */
    private File jnlpTemplate;

    /**
     * @parameter 
     *            default-value="${project.build.directory}/product/eclipse/${project.artifactId}_${unqualifiedVersion}.jnlp"
     */
    private File jnlpFile;

    /**
     * Maps environment key (i.e. osgi os/ws/arch) to Java os.name and os.arch system properties values.
     * 
     * @parameter
     */
    private Environment[] environmentsMap;

    /**
     * The product configuration, a .product file. This file manages all aspects of a product definition from its
     * constituent plug-ins to configuration files to branding.
     * 
     * @parameter expression="${productConfiguration}" default-value="${project.basedir}/${project.artifactId}.product"
     */
    private File productConfigurationFile;

    /**
     * If specified, generated <jar href="..."/> elements will use value of this property as url prefix.
     * 
     * @parameter default-value="plugins/"
     */
    private String hrefPrefix;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        Document document = loadTemplate( jnlpTemplate );

        addResources( document.getRootElement() );

        writeXmlFile( document, jnlpFile );
    }

    protected Document loadTemplate( File template )
        throws MojoExecutionException
    {
        try
        {
            StringSearchInterpolator interpolator = new StringSearchInterpolator();
            interpolator.addValueSource( new PrefixedObjectValueSource( "project", project ) );

            File configIniFile = new File( target, "configuration/config.ini" );
            if ( configIniFile.canRead() )
            {
                final Properties configIni = new Properties();
                InputStream is = new BufferedInputStream( new FileInputStream( configIniFile ) );
                try
                {
                    configIni.load( is );
                }
                finally
                {
                    IOUtil.close( is );
                }

                interpolator.addValueSource( new PrefixedPropertiesValueSource( CONFIGINI_PROPERTY_PREFIX, configIni ) );
            }

            if ( productConfigurationFile.canRead() )
            {
                ProductConfiguration product = ProductConfiguration.read( productConfigurationFile );

                interpolator.addValueSource( new PrefixedObjectValueSource( PRODUCT_PROPERTY_PREFIX, product ) );
            }

            interpolator.addValueSource( new PropertiesBasedValueSource( project.getProperties() ) );

            Reader reader = ReaderFactory.newXmlReader( template );
            reader = new InterpolatorFilterReader( reader, interpolator );
            try
            {
                return XMLParser.parse( IOUtil.toString( reader ) );
            }
            finally
            {
                IOUtil.close( reader );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not read jnlp file template " + template.getAbsolutePath(), e );
        }
    }

    private static String getEnvKey( String os, String arch )
    {
        if ( os == null && arch == null )
        {
            return NO_ENVIRONMENT;
        }
        StringBuilder key = new StringBuilder();
        if ( os != null )
        {
            key.append( os );
        }
        key.append( '/' );
        if ( arch != null )
        {
            key.append( arch );
        }

        return key.toString();
    }

    protected void addResources( Element jnlpDom )
    {
        final Map<String, List<PluginDescription>> plugins = new LinkedHashMap<String, List<PluginDescription>>();

        getDependencyWalker().walk( new ArtifactDependencyVisitor()
        {
            public void visitPlugin( PluginDescription plugin )
            {
                PluginRef ref = plugin.getPluginRef();

                String key = getEnvKey( ref.getOs(), ref.getArch() );

                List<PluginDescription> list = plugins.get( key );
                if ( list == null )
                {
                    list = new ArrayList<PluginDescription>();
                    plugins.put( key, list );
                }
                list.add( plugin );
            }
        } );

        for ( Map.Entry<String, List<PluginDescription>> entry : plugins.entrySet() )
        {
            if ( NO_ENVIRONMENT.equals( entry.getKey() ) )
            {
                addResources( jnlpDom, entry.getValue(), null, null );
            }
            else
            {
                for ( Environment env : getEnvironments( entry.getKey() ) )
                {
                    addResources( jnlpDom, entry.getValue(), env.getOs(), env.getArch() );
                }
            }
        }
    }

    private List<Environment> getEnvironments( String key )
    {
        ArrayList<Environment> envs = new ArrayList<Environment>();

        for ( Environment env : ENVIRONMENTS_MAP )
        {
            if ( key.equals( env.getKey() ) )
            {
                envs.add( env );
            }
        }

        if ( environmentsMap != null )
        {
            for ( Environment env : environmentsMap )
            {
                if ( key.equals( env.getKey() ) )
                {
                    envs.add( env );
                }
            }
        }

        if ( envs.isEmpty() )
        {
            getLog().warn( "Unknown or unsupported target environment " + key );
        }

        return envs;
    }

    protected void addResources( Element jnlpDom, List<PluginDescription> plugins, String os, String arch )
    {
        jnlpDom.addNode( new Text( "\n" ) );
        
        Element resourcesDom = new Element( "resources" );
        jnlpDom.addNode( resourcesDom );

        if ( os != null )
        {
            resourcesDom.setAttribute( "os", os );
        }
        if ( arch != null )
        {
            resourcesDom.setAttribute( "arch", arch );
        }

        for ( PluginDescription plugin : plugins )
        {
            resourcesDom.addNode( new Text( "\n" ) );
            
            String bundleId = plugin.getKey().getId();
            String version = getVersion( plugin );

            Element jarDom = new Element( "jar" );
            resourcesDom.addNode( jarDom );

            StringBuilder href = new StringBuilder();
            href.append( hrefPrefix );
            href.append( bundleId ).append( '_' ).append( version ).append( ".jar" );

            jarDom.setAttribute( "href", href.toString() );
        }
    }
}

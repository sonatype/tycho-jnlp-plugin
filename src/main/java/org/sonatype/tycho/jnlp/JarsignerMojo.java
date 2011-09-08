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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.FeatureDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.eclipsepackaging.UpdateSiteAssembler;

/**
 * Signs bundle and feature jar files assembled inside target/site folder using jarsigner. This mojo signs all jars,
 * regardless if they were built locally or came from third party artifact repository. This is necessary for Java
 * Webstart, which requires use of the same signature for all jars referenced from JNLP file. This mojo honours most of
 * properties used by maven-jarsigner-plugin (${jarsigner.keystore}, ${jarsigner.storepass} and so on).
 * 
 * @see http://java.sun.com/javase/6/docs/technotes/tools/solaris/jarsigner.html
 * @phase package
 * @goal sign-jars
 */
public class JarsignerMojo
    extends AbstractJnlpMojo
{

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.keystore}"
     */
    private String keystore;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.storepass}"
     */
    private String storepass;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.keypass}"
     */
    private String keypass;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.sigfile}"
     */
    private String sigfile;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.storetype}"
     */
    private String storetype;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.providerName}"
     */
    private String providerName;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.providerClass}"
     */
    private String providerClass;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.providerArg}"
     */
    private String providerArg;

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.alias}"
     * @required
     */
    private String alias;

    /**
     * Set to {@code true} to disable the plugin.
     * 
     * @parameter expression="${jarsigner.skip}" default-value="true"
     */
    private boolean skip;

    /**
     * The path to the jarsigner we are going to use.
     */
    private String executable;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !this.skip )
        {
            this.executable = getExecutable();

            final ArrayList<Exception> exceptions = new ArrayList<Exception>();

            getDependencyWalker().walk( new ArtifactDependencyVisitor()
            {
                @Override
                public boolean visitFeature( FeatureDescription feature )
                {
                    String id = feature.getKey().getId();
                    String version = getVersion( feature );

                    File archive = new File( target, UpdateSiteAssembler.FEATURES_DIR + id + "_" + version + ".jar" );

                    if ( archive.isFile() && archive.canWrite() )
                    {
                        try
                        {
                            signFile( archive );
                        }
                        catch ( MojoExecutionException e )
                        {
                            getLog().warn( "Could not sign jar", e );
                            exceptions.add( e );
                        }
                    }

                    return true; // keep visiting
                }

                @Override
                public void visitPlugin( PluginDescription plugin )
                {
                    String id = plugin.getKey().getId();
                    String version = getVersion( plugin );

                    File archive = new File( target, UpdateSiteAssembler.PLUGINS_DIR + id + "_" + version + ".jar" );

                    if ( archive.isFile() && archive.canWrite() )
                    {
                        try
                        {
                            signFile( archive );
                        }
                        catch ( MojoExecutionException e )
                        {
                            getLog().warn( "Could not sign jar", e );
                            exceptions.add( e );
                        }
                    }
                }
            } );

            if ( !exceptions.isEmpty() )
            {
                throw new MojoExecutionException( "Could not sign some jar files" );
            }
        }
    }

    void signFile( File archive )
        throws MojoExecutionException
    {
        getLog().info( "Executing jarsigner on " + archive.getAbsolutePath() );

        Commandline commandLine = new Commandline();

        commandLine.setExecutable( this.executable );

        commandLine.setWorkingDirectory( this.project.getBasedir() );

        commandLine = getCommandline( archive, commandLine );

        getLog().debug( "Executing: " + commandLine );

        StreamConsumer out = new StreamConsumer()
        {
            public void consumeLine( String line )
            {
                getLog().debug( line );
            }
        };

        StreamConsumer err = new StreamConsumer()
        {
            public void consumeLine( String line )
            {
                getLog().warn( line );
            }
        };

        try
        {
            int rc = CommandLineUtils.executeCommandLine( commandLine, out, err );

            if ( rc != 0 )
            {
                throw new MojoExecutionException( "Could not sign jar " + archive + " (return code " + rc
                    + "), command line was " + commandLine );
            }
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Could not sign jar " + archive, e );
        }
    }

    /**
     * @Copy&paste from org.apache.maven.plugins.jarsigner.JarsignerSignMojo
     */
    private Commandline getCommandline( final File archive, final Commandline commandLine )
    {
        if ( archive == null )
        {
            throw new NullPointerException( "archive" );
        }
        if ( commandLine == null )
        {
            throw new NullPointerException( "commandLine" );
        }

        if ( !StringUtils.isEmpty( this.keystore ) )
        {
            commandLine.createArg().setValue( "-keystore" );
            commandLine.createArg().setValue( this.keystore );
        }
        if ( !StringUtils.isEmpty( this.storepass ) )
        {
            commandLine.createArg().setValue( "-storepass" );
            commandLine.createArg().setValue( this.storepass );
        }
        if ( !StringUtils.isEmpty( this.keypass ) )
        {
            commandLine.createArg().setValue( "-keypass" );
            commandLine.createArg().setValue( this.keypass );
        }
        if ( !StringUtils.isEmpty( this.storetype ) )
        {
            commandLine.createArg().setValue( "-storetype" );
            commandLine.createArg().setValue( this.storetype );
        }
        if ( !StringUtils.isEmpty( this.providerName ) )
        {
            commandLine.createArg().setValue( "-providerName" );
            commandLine.createArg().setValue( this.providerName );
        }
        if ( !StringUtils.isEmpty( this.providerClass ) )
        {
            commandLine.createArg().setValue( "-providerClass" );
            commandLine.createArg().setValue( this.providerClass );
        }
        if ( !StringUtils.isEmpty( this.providerArg ) )
        {
            commandLine.createArg().setValue( "-providerArg" );
            commandLine.createArg().setValue( this.providerArg );
        }
        if ( !StringUtils.isEmpty( this.sigfile ) )
        {
            commandLine.createArg().setValue( "-sigfile" );
            commandLine.createArg().setValue( this.sigfile );
        }

        commandLine.createArg().setFile( archive );

        if ( !StringUtils.isEmpty( this.alias ) )
        {
            commandLine.createArg().setValue( this.alias );
        }

        return commandLine;
    }

    /**
     * Locates the executable for the jarsigner tool.
     * 
     * @Copy&paste from org.apache.maven.plugins.jarsigner.AbstractJarsignerMojo
     * @return The executable of the jarsigner tool, never <code>null<code>.
     */
    private String getExecutable()
    {
        String command = "jarsigner" + ( Os.isFamily( Os.FAMILY_WINDOWS ) ? ".exe" : "" );

        String executable =
            findExecutable( command, System.getProperty( "java.home" ), new String[] { "../bin", "bin", "../sh" } );

        if ( executable == null )
        {
            try
            {
                Properties env = CommandLineUtils.getSystemEnvVars();

                String[] variables = { "JDK_HOME", "JAVA_HOME" };

                for ( int i = 0; i < variables.length && executable == null; i++ )
                {
                    executable =
                        findExecutable( command, env.getProperty( variables[i] ), new String[] { "bin", "sh" } );
                }
            }
            catch ( IOException e )
            {
                if ( getLog().isDebugEnabled() )
                {
                    getLog().warn( "Failed to retrieve environment variables, cannot search for " + command, e );
                }
                else
                {
                    getLog().warn( "Failed to retrieve environment variables, cannot search for " + command );
                }
            }
        }

        if ( executable == null )
        {
            executable = command;
        }

        return executable;
    }

    /**
     * Finds the specified command in any of the given sub directories of the specified JDK/JRE home directory.
     * 
     * @Copy&paste from org.apache.maven.plugins.jarsigner.AbstractJarsignerMojo
     * @param command The command to find, must not be <code>null</code>.
     * @param homeDir The home directory to search in, may be <code>null</code>.
     * @param subDirs The sub directories of the home directory to search in, must not be <code>null</code>.
     * @return The (absolute) path to the command if found, <code>null</code> otherwise.
     */
    private String findExecutable( String command, String homeDir, String[] subDirs )
    {
        if ( StringUtils.isNotEmpty( homeDir ) )
        {
            for ( int i = 0; i < subDirs.length; i++ )
            {
                File file = new File( new File( homeDir, subDirs[i] ), command );

                if ( file.isFile() )
                {
                    return file.getAbsolutePath();
                }
            }
        }

        return null;
    }
}

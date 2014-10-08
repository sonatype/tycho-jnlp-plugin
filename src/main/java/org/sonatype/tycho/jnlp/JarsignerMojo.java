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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.eclipse.tycho.core.ArtifactDependencyVisitor;
import org.eclipse.tycho.core.FeatureDescription;
import org.eclipse.tycho.core.PluginDescription;

import de.pdark.decentxml.Document;

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
    extends JnlpFileMojo
{

    private static final String FEATURES_DIR = "features/";
    private static final String PLUGINS_DIR = "plugins/";
    private static final String MANIFEST_FILE = "manifest_file";
    
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
     * See Options for <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">java 6</a>.
     * and <a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">java 7</a>.
     * If this option is not specified,  SHA-1 will be used with java 6, and SHA256 will be used for java 7
     * 
     * @parameter expression="${jarsigner.digestalg}"
     */
    private String digestalg;

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
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">Permissions Attribute</a>.
     * 
     * @parameter expression="${jarsigner.permissions}"
     */
    private String permissions;

    /**
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">Codebase Attribute</a>.
     * 
     * @parameter expression="${jarsigner.codebase}"
     */
    private String codebase;

    /**
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">Application-Name Attribute </a>.
     * 
     * @parameter expression="${jarsigner.applicationName}"
     */
    private String applicationName;

    /**
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">Application-Library-Allowable-Codebase Attribute</a>.
     * 
     * @parameter expression="${jarsigner.applicationLibraryAllowableCodebase}"
     */
    private String applicationLibraryAllowableCodebase;

    /**
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">Caller-Allowable-Codebase Attribute</a>.
     * 
     * @parameter expression="${jarsigner.callerAllowableCodebase}"
     */
    private String callerAllowableCodebase;

    /**
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">Trusted-Only Attribute</a>.
     * 
     * @parameter expression="${jarsigner.trustedOnly}"
     */
    private String trustedOnly;

    /**
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">Trusted-Library Attribute</a>.
     * 
     * @parameter expression="${jarsigner.trustedLibrary}"
     */
    private String trustedLibrary;

    /**
     * Set to {@code true} to sign the jnlp file
     * 
     * @parameter expression="${jarsigner.signJnlpFile}" default-value="false"
     */
    private boolean signJnlpFile;


    /**
     * Set to {@code true} to sign the jnlp file with the template instead of the the jnlp file
     * 
     * @parameter expression="${jarsigner.signJnlpFileWithTemplate}" default-value="false"
     */
    private boolean signJnlpFileWithTemplate;

    /**
     * @parameter default-value="${project.basedir}/src/main/jnlp/signing_install.jnlp"
     */
    private File jnlpSigningTemplate;

    /**
     * The generated file used to sign the jnlp file
     */
    private File jnlpSigningFile;

    /**
     * The application main class, used to find the jar to use to sign the jnlp file
     */
    private String applicationMainClassFile;

    /**
     * Set to {@code true} to disable the plugin.
     * 
     * @parameter expression="${jarsigner.skip}" default-value="true"
     */
    private boolean skip;

    /**
     * The path to the jarsigner we are going to use.
     */
    private String jarsignerExecutable;

    /**
     * The path to the jar we are going to use.
     */
    private String jarExecutable;
    
    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     * 
     * @parameter expression="${jarsigner.tsa}"
     */
    private String tsa;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( this.skip )
            return;
            
        this.jarsignerExecutable = getExecutable("jarsigner");
        this.jarExecutable = getExecutable("jar");

        final ArrayList<Exception> exceptions = new ArrayList<Exception>();

        if ( this.signJnlpFile)
        {
            getLog().info( "  Generating the signed jnlp file" );
            Document document = loadTemplate( this.signJnlpFileWithTemplate ? 
                                              this.jnlpSigningTemplate : this.jnlpTemplate);
            addResources( document.getRootElement() );
            jnlpSigningFile = new File(jnlpFile.getParent(), "template.jnlp");
            writeXmlFile( document, jnlpSigningFile );
            
            //Find the application main class to find the appropriate jar file to 
            //sign the jnlp
            Document documentJnlp = loadTemplate( jnlpTemplate );
            String mainClass = documentJnlp.getRootElement().getChild("application-desc").getAttribute("main-class").getValue();
            applicationMainClassFile = mainClass.replace('.', File.separatorChar) + ".class";
            getLog().info( "  Application main class : " + applicationMainClassFile);
        }

        getDependencyWalker().walk( new ArtifactDependencyVisitor()
        {
            @Override
            public boolean visitFeature( FeatureDescription feature )
            {
                String id = feature.getKey().getId();
                String version = getVersion( feature );

                File archive = new File( target, FEATURES_DIR + id + "_" + version + ".jar" );

                if ( archive.isFile() && archive.canWrite() )
                {
                    try
                    {
                        getLog().info( "Jar file :" + archive.getAbsolutePath() );
                        addSecurityAttributes( archive );
                        signFile( archive );
                    }
                    catch ( MojoExecutionException e )
                    {
                        getLog().warn( "Could not add security attributes or sign jar", e );
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

                File archive = new File( target, PLUGINS_DIR + id + "_" + version + ".jar" );

                if ( archive.isFile() && archive.canWrite() )
                {
                    try
                    {
                        getLog().info( "Jar file :" + archive.getAbsolutePath() );
                        addSecurityAttributes( archive );
                        signFile( archive );
                    }
                    catch ( MojoExecutionException e )
                    {
                        getLog().warn( "Could not add security attributes or sign jar", e );
                        exceptions.add( e );
                    }
                }
            }
        } );

        if ( !exceptions.isEmpty() )
        {
            throw new MojoExecutionException( "Could not add security attributes or sign some jar files" );
        }
    }

    /**
     * Add security attributes to the manifest. 
     * See <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html">JAR File Manifest Attributes for Security </a>
     * Since the jar is modified, the signatures are removed from the jar file
     * before adding the attributes in the manifest.
     * 
     * @param The archive file
     */
    void addSecurityAttributes( File archive )
        throws MojoExecutionException
    {
        if ( StringUtils.isEmpty( this.permissions ) 
                && StringUtils.isEmpty( this.codebase ) 
                && StringUtils.isEmpty( this.applicationName )
                && StringUtils.isEmpty( this.applicationLibraryAllowableCodebase )
                && StringUtils.isEmpty( this.callerAllowableCodebase )
                && StringUtils.isEmpty( this.trustedOnly )
                && StringUtils.isEmpty( this.trustedLibrary ) )
            return;

        getLog().info( "  Removing signatures" );
        File tempFolder = new File(archive.getParentFile(), "temp");
        if (tempFolder.exists())
            if( !deleteFolder(tempFolder) )
                throw new MojoExecutionException( "Cannot delete folder " + tempFolder.getAbsolutePath());
        if ( !tempFolder.mkdir() )
            throw new MojoExecutionException( "Cannot create folder " + tempFolder.getAbsolutePath());
        
        //unjar in a folder
        Commandline commandLine = new Commandline();
        commandLine.setExecutable( this.jarExecutable );
        commandLine.setWorkingDirectory( tempFolder );
        commandLine.createArg().setValue( "xf" );
        commandLine.createArg().setFile( archive );
        getLog().debug( "Executing: " + commandLine );
        try
        {
            int rc = executeCommandLine( commandLine );
            if ( rc != 0 )
                throw new MojoExecutionException( "Could not extract jar " + archive + " (return code " + rc
                    + "), command line was " + commandLine );
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Could not extract jar " + archive, e );
        }
        
        //Delete the signature files because the manifest is modified. 
        for (File file : new File(tempFolder, "META-INF").listFiles())
            if (file.getName().endsWith(".SF")
                    || file.getName().endsWith(".DSA")
                    || file.getName().endsWith(".RSA")
                    || file.getName().endsWith(".EC"))
                file.delete();
        
        //Recreate the jar file
        commandLine = new Commandline();
        commandLine.setExecutable( this.jarExecutable );
        commandLine.setWorkingDirectory( tempFolder );
        File metaInfFolder = new File(tempFolder, "META-INF");
        File manifestFile = new File(metaInfFolder, "MANIFEST.MF");
        
        
        //Add the singed jnlp in the jar of the application main class
        if ( this.signJnlpFile && new File(tempFolder, applicationMainClassFile).exists())
        {
            getLog().info( "  Adding jnlp file signature to " + archive.getName());
            File jnlpInfFolder = new File(tempFolder, "JNLP-INF");
            if ( !jnlpInfFolder.mkdir() )
                throw new MojoExecutionException( "Cannot create folder " + jnlpInfFolder.getAbsolutePath());
            
            try
            {
                Files.copy(this.jnlpSigningFile.toPath(), 
                            new File(jnlpInfFolder, this.signJnlpFileWithTemplate ? 
                                     "APPLICATION_TEMPLATE.JNLP" : "APPLICATION.JNLP").toPath());
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Could not copy the jar file to sign " + archive, e );
            }
        }
        
        if ( manifestFile.exists())
        {
            commandLine.createArg().setValue( "cfm" );
            commandLine.createArg().setFile( archive );
            commandLine.createArg().setFile( manifestFile );
        }
        else
        {
            commandLine.createArg().setValue( "cf" );
            commandLine.createArg().setFile( archive );
        }
        for (File file : tempFolder.listFiles())
            commandLine.createArg().setValue( file.getName() );
        getLog().debug( "Executing: " + commandLine );
        try
        {
            int rc = executeCommandLine( commandLine);
            if ( rc != 0 )
                throw new MojoExecutionException( "Could not recreate jar " + archive + " (return code " + rc
                    + "), command line was " + commandLine );
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Could not recreate jar " + archive, e );
        }
        if( !deleteFolder(tempFolder) )
            throw new MojoExecutionException( "Cannot delete folder " + tempFolder.getAbsolutePath());
        
        getLog().info( "  Adding security attributes" );
        
        commandLine = new Commandline();
        commandLine.setExecutable( this.jarExecutable );
        commandLine.setWorkingDirectory( this.project.getBasedir() );

        StringBuffer content = new StringBuffer();
        if ( !StringUtils.isEmpty( this.permissions ) )
            content.append(String.format("Permissions: %s\n", this.permissions));
        if ( !StringUtils.isEmpty( this.codebase ) )
            content.append(String.format("Codebase: %s\n", this.codebase));
        if ( !StringUtils.isEmpty( this.applicationName ) )
            content.append(String.format("Application-Name: %s\n", this.applicationName));
        if ( !StringUtils.isEmpty( this.applicationLibraryAllowableCodebase ) )
            content.append(String.format("Application-Library-Allowable-Codebase: %s\n", this.applicationLibraryAllowableCodebase));
        if ( !StringUtils.isEmpty( this.callerAllowableCodebase ) )
            content.append(String.format("Caller-Allowable-Codebase: %s\n", this.callerAllowableCodebase));
        if ( !StringUtils.isEmpty( this.trustedOnly ) )
            content.append(String.format("Trusted-Only: %s\n", this.trustedOnly));
        if ( !StringUtils.isEmpty( this.trustedLibrary ) )
            content.append(String.format("Trusted-Library: %s\n", this.trustedLibrary));

        File inputManifestFile = new File(archive.getParentFile(), MANIFEST_FILE);
        writeFile(inputManifestFile, content.toString());
        commandLine.createArg().setValue( "ufm" );
        commandLine.createArg().setFile( archive );
        commandLine.createArg().setFile( inputManifestFile );
        getLog().debug( "Executing: " + commandLine );
        
        try
        {
            int rc = executeCommandLine( commandLine);
            if ( rc != 0 )
                throw new MojoExecutionException( "Could not add security attributes to jar " + archive + " (return code " + rc
                    + "), command line was " + commandLine );
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Could not add security attributes to jar " + archive, e );
        }
    }

    /**
     * Sign file
     * 
     * @param The archive file to be signed
     */
    void signFile( File archive )
        throws MojoExecutionException
    {
        getLog().info( "  Signing jar file" );

        Commandline commandLine = new Commandline();
        commandLine.setExecutable( this.jarsignerExecutable );
        commandLine.setWorkingDirectory( this.project.getBasedir() );
        commandLine = getCommandline( archive, commandLine );
        getLog().debug( "Executing: " + commandLine );
        try
        {
            int rc = executeCommandLine( commandLine );
            if ( rc != 0 )
                throw new MojoExecutionException( "Could not sign jar " + archive + " (return code " + rc
                    + "), command line was " + commandLine );
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
        if ( !StringUtils.isEmpty( this.digestalg ) )
        {
            commandLine.createArg().setValue( "-digestalg" );
            commandLine.createArg().setValue( this.digestalg );
        }
        if ( !StringUtils.isEmpty( this.tsa ) )
        {
        	commandLine.createArg().setValue( "-tsa" );
        	commandLine.createArg().setValue( this.tsa );
        }

        commandLine.createArg().setFile( archive );

        if ( !StringUtils.isEmpty( this.alias ) )
        {
            commandLine.createArg().setValue( this.alias );
        }

        return commandLine;
    }

    /**
     * Execute a command line
     * 
     * @return The return value of the command.
     */
    private int executeCommandLine( Commandline commandLine )
        throws CommandLineException
    {
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

        return CommandLineUtils.executeCommandLine( commandLine, out, err );
    }

    /**
     * Write the input manifest file 
     * 
     * @param file The file to write
     * @param content The Content
     */
   protected void writeFile(File file, String content)
                 throws MojoExecutionException
    {
        try
        {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(
                    file));
            try
            {
                OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8");
                try
                {
                    writer.write(content);
                }
                finally
                {
                    writer.flush();
                }
            }
            finally
            {
                IOUtil.close(os);
            }
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Could not write output file "
                                           + file.getAbsolutePath(), e);
        }
    }

    /**
     * Delete directory file
     * 
     * @param path
     *            The directory
     * @return true if ok
     */
    private boolean deleteFolder(File path)
    {
        if (path.isDirectory() && path.exists())
        {
            File[] files = path.listFiles();
            for (int i = 0; i < files.length; i++)
            {
                if (files[i].isDirectory())
                    deleteFolder(files[i]);
                else
                    files[i].delete();
            }
        }
        return (path.delete());
    }


    /**
     * Locates the executable for the jarsigner tool.
     * 
     * @Copy&paste from org.apache.maven.plugins.jarsigner.AbstractJarsignerMojo
     * @return The executable of the jarsigner tool, never <code>null<code>.
     */
    private String getExecutable(String name)
    {
        String command = name + ( Os.isFamily( Os.FAMILY_WINDOWS ) ? ".exe" : "" );

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

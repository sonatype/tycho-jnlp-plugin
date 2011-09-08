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
import java.io.Reader;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.interpolation.InterpolatorFilterReader;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.PluginDescription;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.Text;
import de.pdark.decentxml.XMLParser;

/**
 * Generates artifacts.xml file (i.e. p2 simple artifact repository state) custom tailored for mse installer and
 * nexus-onboarding plugin. This mojo does not belong here, but I am too lazy to start new plugin.
 * 
 * @phase package
 * @goal artifacts-xml
 */
public class ArtifactsXmlMojo
    extends AbstractJnlpMojo
{
    /**
     * @parameter default-value="${project.basedir}/src/main/jnlp/artifacts.xml"
     */
    private File artifactsTemplate;

    /**
     * @parameter default-value="${project.build.directory}/artifacts.xml"
     */
    private File artifactsFile;

    /**
     * @parameter default-value="mse.installer.bundle"
     */
    private String artifactClassifier;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        Document document = loadTemplate( artifactsTemplate );
        final Element artifactsDom = document.getRootElement().getChild( "artifacts" );

        getDependencyWalker().walk( new ArtifactDependencyVisitor()
        {
            public void visitPlugin( PluginDescription plugin )
            {
                Element artifactDom = new Element( "artifact" );

                artifactDom.setAttribute( "id", plugin.getKey().getId() );
                artifactDom.setAttribute( "version", getVersion( plugin ) );
                artifactDom.setAttribute( "classifier", artifactClassifier );

                artifactsDom.addNode( new Text( "\n" ) );
                artifactsDom.addNode( artifactDom );
            }
        } );

        writeXmlFile( document, artifactsFile );
    }

    protected Document loadTemplate( File template )
        throws MojoExecutionException
    {
        try
        {
            StringSearchInterpolator interpolator = new StringSearchInterpolator();
            interpolator.addValueSource( new PrefixedObjectValueSource( "project", project ) );

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
            throw new MojoExecutionException( "Could not read template " + template.getAbsolutePath(), e );
        }
    }

}

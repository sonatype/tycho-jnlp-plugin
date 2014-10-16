# Overview

tycho-jnlp-plugin is a Tycho extensions plugin that generates JNLP file
for projects with eclipse-application packaging. It also (re)signs all
artifacts of the application using the same certificate, which is required
to launch remote WebStart applications.

# jnlp:jnlp-file goal

Generates JNLP file from provided template. 

Performs ${property} substitution in the template, where property values can 
come either from pom.xml or from application's config.ini file. The latter is 
useful to pass -Declipse.product, -Declipse.application and -Dosgi.bundles 
properties, which are usually required to launch equinox-based application 
using Java WebStart.

Adds &lt;resources/> node with &lt;jar/> subelements to the generated JNLP file.
&lt;jar/> elements that correspond to platform-specific bundles and fragments
will have proper os/arch attributes set for them. 

# jnlp:sign-jars goal

Signs bundle and feature jar files assembled inside target/site folder using 
jarsigner. This mojo signs all jars, regardless if they were built locally or 
came from third party artifact repository. This is necessary for Java Webstart, 
which requires use of the same signature for all jars referenced from JNLP file. 
This mojo honours most of properties used by maven-jarsigner-plugin 
(${jarsigner.keystore}, ${jarsigner.storepass} and so on). The property 
${jarsigner.digestalg} must be set to SHA-1 when using a jarsigner form a JDK7 
because the default signing algorithm is now SHA256.

The [JAR File Manifest Attributes](http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/manifest.html) 
for Security can also be added to each jar file. The attributes can be configured
by the properties :

 * ${jarsigner.permissions} Permissions
 * ${jarsigner.codebase} Codebase
 * ${jarsigner.applicationName} Application-Name
 * ${jarsigner.applicationLibraryAllowableCodebase} Application-Library-Allowable-Codebase
 * ${jarsigner.callerAllowableCodebase} Caller-Allowable-Codebase
 * ${jarsigner.trustedOnly} Trusted-Only
 * ${jarsigner.trustedLibrary} Trusted-Library

If one of these properties is specified, the signatures of the jar files are 
removed because the jar files are modified.

The [JNLP file can also be signed](http://docs.oracle.com/javase/7/docs/technotes/guides/jweb/signedJNLP.html) 
if the property ${jarsigner.signJnlpFile} is set to true. The signed JNLP file is
created in the folder JNLP-INF in the jar jar file of the main class. The file is
APPLICATION_TEMPLATE.JNLP for a template and is APPLICATION.JNLP if the file is 
the strict copy of the JNLP file.

If the property ${jarsigner.signJnlpFileWithTemplate} is set to true, the JNLP 
file is signed with the template defined in the property jnlpSigningTemplate. 

Note that if the JNLP file is not signed, some properties are considered as
insecure if they are not preceded by the "jnlp."or "javaws." prefix. (Properties
such as "eclipse.product", "eclipse.application", etc... )
The properties can be transformed into "secure" ones by adding the "jnlp."-prefix. 
A custom launcher must be wrote which iterates through all properties and removes 
the prefix. The custom launcher can be :

```java
public class WebStartMain
{
    public static void main(String[] args)
    {
        Properties props = System.getProperties();
        for (String key : props.stringPropertyNames())
            if (key.startsWith("jnlp."))
                System.setProperty(key.substring(5), props.getProperty(key));
        org.eclipse.equinox.launcher.WebStartMain.main(args);
    }
}
 ```








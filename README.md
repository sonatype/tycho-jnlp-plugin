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
(${jarsigner.keystore}, ${jarsigner.storepass} and so on).

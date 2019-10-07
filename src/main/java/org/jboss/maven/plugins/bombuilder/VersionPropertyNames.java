/**
 * 
 */
package org.jboss.maven.plugins.bombuilder;

/**
 *
 */
public class VersionPropertyNames {
	public static final String VERSION_PROPERTY_PREFIX = "version.";

	private VersionPropertyNames() {
		// Utility class.
	}

	public static String buildPropertyName(String groupId) {
		return VERSION_PROPERTY_PREFIX + groupId;
	}

	public static String buildPropertyNameForGroupAndArtifact(String groupId, String artifactId) {
		return VERSION_PROPERTY_PREFIX + groupId + "." + artifactId;
	}

}

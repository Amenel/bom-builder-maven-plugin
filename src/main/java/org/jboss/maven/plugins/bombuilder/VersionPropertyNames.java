/**
 * 
 */
package org.jboss.maven.plugins.bombuilder;

import java.util.Set;

/**
 *
 */
public class VersionPropertyNames {
	public static final String VERSION_PROPERTY_PREFIX = "version.";
	public static final String VERSION_PROPERTY_SUFFIX = "-version";

	private VersionPropertyNames() {
		// Utility class.
	}

	/**
	 * @param groupId
	 * @return
	 */
	public static String buildPropertyName(boolean useSuffix, String groupId) {
		if (!useSuffix) {
			return VERSION_PROPERTY_PREFIX + groupId;
		}
		return groupId + VERSION_PROPERTY_SUFFIX;
	}

	public static String buildPropertyNameForGroupAndArtifact(boolean useSuffix, String groupId, String artifactId) {
		if (!useSuffix) {
			return VERSION_PROPERTY_PREFIX + groupId + "." + artifactId;
		}
		return artifactId + VERSION_PROPERTY_SUFFIX;
	}

	/**
	 * @return the property name to use when there is only one artifact.
	 */
	public static String getPropertyNameToUse(boolean useSuffix, String groupId, Set<String> artifactIds) {
		String nameToUse = groupId;
		if (useSuffix) {
			for (String artifactId : artifactIds) {
				nameToUse = artifactId;
			}
		}
		return nameToUse;
	}

}

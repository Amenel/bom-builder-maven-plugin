package org.jboss.maven.plugins.bombuilder;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;

/**
 * For all dependencies in the dependency management section, takes the
 * specified digital version of a dependency (e.g. {@code 1.5.0}) and replaces
 * it with a version property.
 */
class PomDependencyVersionsTransformer {

	private static final String VERSION_PLACEHOLDER_PREFIX = "${";
	private static final String VERSION_PLACEHOLDER_SUFFIX = "}";

	private Map<String, String> groupIdArtifactIdPropertyNames;
	private Model pomModel;

	public PomDependencyVersionsTransformer() {
		groupIdArtifactIdPropertyNames = new TreeMap<>();
	}

	public Model transformPomModel(Model model) {
		pomModel = model.clone();
		DependencyManagement depMgmt = pomModel.getDependencyManagement();

		// Version of each artifact identified by its groupId and artifactId.
		Map<String, String> groupIdArtifactIdVersions = new TreeMap<>();
		// Version of each groupId.
		Map<String, String> groupIdVersions = new TreeMap<>();
		// Mapping of a groupId to all known artifactIds.
		Map<String, Set<String>> groupIdArtifactIds = new TreeMap<>();

		collectVersionsAndMappings(depMgmt, groupIdArtifactIdVersions, groupIdVersions, groupIdArtifactIds);

		buildVersionProperties(pomModel, groupIdArtifactIdVersions, groupIdArtifactIdPropertyNames, groupIdVersions,
				groupIdArtifactIds);

		assignVersionPropertiesToArtifacts(depMgmt, groupIdArtifactIdPropertyNames);
		return pomModel;
	}

	/**
	 * @param depMgmt
	 * @param groupIdArtifactIdPropertyNames
	 */
	private void assignVersionPropertiesToArtifacts(DependencyManagement depMgmt,
			Map<String, String> groupIdArtifactIdPropertyNames) {
		for (Dependency dependency : depMgmt.getDependencies()) {
			if (!dependency.getVersion().startsWith(VERSION_PLACEHOLDER_PREFIX)) {
				String groupId = dependency.getGroupId();
				String artifactId = dependency.getArtifactId();
				String groupIdArtifactId = groupId + ":" + artifactId;
				String propertyName = groupIdArtifactIdPropertyNames.get(groupIdArtifactId);
				dependency.setVersion(VERSION_PLACEHOLDER_PREFIX + propertyName + VERSION_PLACEHOLDER_SUFFIX);
			}
		}
	}

	/**
	 * @param pomModel
	 * @param groupIdArtifactIdVersions
	 * @param groupIdArtifactIdPropertyNames
	 * @param groupIdVersions
	 * @param groupIdArtifactIds
	 */
	private void buildVersionProperties(Model pomModel, Map<String, String> groupIdArtifactIdVersions,
			Map<String, String> groupIdArtifactIdPropertyNames, Map<String, String> groupIdVersions,
			Map<String, Set<String>> groupIdArtifactIds) {
		Properties properties = pomModel.getProperties();

		for (Map.Entry<String, String> groupVersion : groupIdVersions.entrySet()) {
			buildVersionPropertiesForGroupId(groupIdArtifactIdVersions, groupIdArtifactIdPropertyNames,
					groupIdArtifactIds, properties, groupVersion);
		}
	}

	/**
	 * @param groupIdArtifactIdVersions
	 * @param groupIdArtifactIdPropertyNames
	 * @param groupIdArtifactIds
	 * @param properties
	 * @param groupVersion
	 */
	private void buildVersionPropertiesForGroupId(Map<String, String> groupIdArtifactIdVersions,
			Map<String, String> groupIdArtifactIdPropertyNames, Map<String, Set<String>> groupIdArtifactIds,
			Properties properties, Map.Entry<String, String> groupVersion) {
		String groupId = groupVersion.getKey();
		Set<String> artifactIds = groupIdArtifactIds.get(groupId);
		if (artifactIds.size() == 1
				|| allArtifactsInGroupHaveSameVersion(groupId, groupIdArtifactIdVersions, artifactIds)) {
			String propertyName = VersionPropertyNames.buildPropertyName(groupId);
			if (properties.getProperty(propertyName) == null) {
				properties.setProperty(propertyName, groupVersion.getValue());
			}
			for (String artifactId : artifactIds) {
				String groupIdArtifactId = groupId + ":" + artifactId;
				groupIdArtifactIdPropertyNames.put(groupIdArtifactId, propertyName);
			}
		} else {
			for (String artifactId : artifactIds) {
				String groupIdArtifactId = groupId + ":" + artifactId;
				String propertyName = VersionPropertyNames.buildPropertyNameForGroupAndArtifact(groupId, artifactId);
				groupIdArtifactIdPropertyNames.put(groupIdArtifactId, propertyName);
				if (properties.getProperty(propertyName) == null) {
					properties.setProperty(propertyName, groupIdArtifactIdVersions.get(groupIdArtifactId));
				}
			}
		}
	}

	/**
	 * Collects the versions of artifacts, the versions of groupIds and maps
	 * groupIds to their artifacts.
	 * 
	 * @param depMgmt
	 * @param groupIdArtifactIdVersions mapping of each artifact to its version
	 * @param groupIdVersions           mapping of a groupId to its first known
	 *                                  version
	 * @param groupIdArtifactIdsMapping mapping of a groupId to all its known
	 *                                  artifacts
	 */
	private void collectVersionsAndMappings(DependencyManagement depMgmt, Map<String, String> groupIdArtifactIdVersions,
			Map<String, String> groupIdVersions, Map<String, Set<String>> groupIdArtifactIdsMapping) {
		for (Dependency dependency : depMgmt.getDependencies()) {
			String groupId = dependency.getGroupId();
			String artifactId = dependency.getArtifactId();
			String groupIdArtifactId = groupId + ":" + artifactId;
			groupIdArtifactIdVersions.put(groupIdArtifactId, dependency.getVersion());

//			if (groupIdVersions.get(groupId) == null) {
//				groupIdVersions.put(groupId, dependency.getVersion());
//			}
			groupIdVersions.computeIfAbsent(groupId, k -> dependency.getVersion());

			Set<String> artifactIds = groupIdArtifactIdsMapping.get(groupId);
			if (artifactIds == null) {
				artifactIds = new HashSet<>();
				groupIdArtifactIdsMapping.put(groupId, artifactIds);
			}
			// groupIdArtifactIdsMapping.computeIfAbsent(groupId, k -> new HashSet<>());
			artifactIds.add(artifactId);
		}
	}

	private boolean allArtifactsInGroupHaveSameVersion(String groupId, Map<String, String> groupIdArtifactIdVersions,
			Set<String> artifactIds) {
		String version = null;
		for (String artifactId : artifactIds) {
			String groupIdArtifactId = groupId + ":" + artifactId;
			if (version == null) {
				version = groupIdArtifactIdVersions.get(groupIdArtifactId);
			} else {
				if (!areVersionsIdentical(version, groupIdArtifactIdVersions.get(groupIdArtifactId))) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean areVersionsIdentical(String version1, String version2) {
		String digitalVersion1 = version1;
		String digitalVersion2 = version2;

		if (digitalVersion1.startsWith(VERSION_PLACEHOLDER_PREFIX)) {
			digitalVersion1 = interpolateVersion(digitalVersion1);
		}
		if (digitalVersion2.startsWith(VERSION_PLACEHOLDER_PREFIX)) {
			digitalVersion2 = interpolateVersion(digitalVersion2);
		}
		return (digitalVersion1.equals(digitalVersion2));
	}

	private String interpolateVersion(String literalVersion) {
		String versionCode = literalVersion.substring(VERSION_PLACEHOLDER_PREFIX.length(),
				literalVersion.length() - VERSION_PLACEHOLDER_SUFFIX.length());
		return pomModel.getProperties().getProperty(versionCode);
	}

}

package org.jboss.maven.plugins.bombuilder;

import static org.codehaus.plexus.util.StringUtils.defaultString;
import static org.codehaus.plexus.util.StringUtils.trim;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Build a BOM based on the dependencies in a GAV
 */
@Mojo(name = "build-bom", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class BuildBomMojo extends AbstractMojo {

	/**
	 * BOM groupId
	 */
	@Parameter(required = true)
	private String bomGroupId;

	/**
	 * BOM artifactId
	 */
	@Parameter(required = true)
	private String bomArtifactId;

	/**
	 * BOM version
	 */
	@Parameter(required = true)
	private String bomVersion;

	/**
	 * BOM name
	 */
	@Parameter(defaultValue = "")
	private String bomName;

	/**
	 * BOM name
	 */
	@Parameter
	private boolean addVersionProperties;

	/**
	 * BOM description
	 */
	@Parameter(defaultValue = "")
	private String bomDescription;

	/**
	 * Whether to use a prefix (if {@code true}) or a suffix.
	 */
	@Parameter(property = "bom-builder.useVersionSuffix")
	private boolean useVersionSuffix;

	/**
	 * Whether to append to an existing file (if {@code true}).
	 */
	@Parameter(property = "bom-builder.appendToBom")
	private boolean appendToBom;

	/**
	 * Whether the output file is to be generated outside of target (if
	 * {@code true}).
	 */
	@Parameter(property = "bom-builder.absoluteFilepath")
	private boolean absoluteFilepath;

	/**
	 * BOM output file
	 */
	@Parameter(property = "bom-builder.outputFilename", defaultValue = "bom-pom.xml")
	String outputFilename;

	/**
	 * Whether the BOM should include the dependency exclusions that are present in
	 * the source POM. By default the exclusions will not be copied to the new BOM.
	 */
	@Parameter
	private List<BomExclusion> exclusions;

	/**
	 * List of dependencies which should not be added to BOM
	 */
	@Parameter
	private List<DependencyExclusion> dependencyExclusions;

	/**
	 * Whether use properties to specify dependency versions in BOM
	 */
	@Parameter
	boolean usePropertiesForVersion;

	/**
	 * The current project
	 */
	@Component
	MavenProject mavenProject;

	/**
	 *
	 */
	@Component
	private ModelBuilder modelBuilder;

	/**
	 *
	 */
	@Component
	private ProjectBuilder projectBuilder;

	private final PomDependencyVersionsTransformer versionsTransformer;
	private final ModelWriter modelWriter;
	private final MavenXpp3Reader mavenXpp3Reader;

	public BuildBomMojo() {
		this(new ModelWriter(), new PomDependencyVersionsTransformer());
	}

	public BuildBomMojo(ModelWriter modelWriter, PomDependencyVersionsTransformer versionsTransformer) {
		this.versionsTransformer = versionsTransformer;
		this.modelWriter = modelWriter;
		this.mavenXpp3Reader = new MavenXpp3Reader();
	}

	public void execute() throws MojoExecutionException {
		getLog().debug("Generating BOM");
		Model model = initializeModel();
		addDependencyManagement(model);
		if (usePropertiesForVersion) {
			model = versionsTransformer.transformPomModel(model, useVersionSuffix);
			getLog().debug("Dependencies versions converted to properties");
		}
		File outputFile;
		if (absoluteFilepath) {
			outputFile = new File(outputFilename);
		} else {
			outputFile = new File(mavenProject.getBuild().getDirectory(), outputFilename);
		}

		modelWriter.writeModel(model, outputFile);
	}

	private Model initializeModel() throws MojoExecutionException {
		Model pomModel;
		if (this.appendToBom) {
			try {
				pomModel = mavenXpp3Reader.read(new FileInputStream(new File(outputFilename)));
				return pomModel;
			} catch (IOException | XmlPullParserException e) {
				throw new MojoExecutionException("Could not parse BOM file: " + outputFilename, e);
			}
		} else {
			pomModel = new Model();
			pomModel.setModelVersion("4.0.0");

			pomModel.setGroupId(bomGroupId);
			pomModel.setArtifactId(bomArtifactId);
			pomModel.setVersion(bomVersion);
			pomModel.setPackaging("pom");

			pomModel.setName(bomName);
			pomModel.setDescription(bomDescription);

			pomModel.setProperties(new OrderedProperties());
			pomModel.getProperties().setProperty("project.build.sourceEncoding", "UTF-8");
			return pomModel;
		}

	}

	private void addDependencyManagement(Model pomModel) {
		// Sort the artifacts for readability
		List<Artifact> projectArtifacts = new ArrayList<>(mavenProject.getArtifacts());
		Collections.sort(projectArtifacts);

		Properties versionProperties = retrieveProperties(pomModel);
		DependencyManagement depMgmt = retrieveDependencyManagement(pomModel);

		for (Artifact artifact : projectArtifacts) {
			if (isExcludedDependency(artifact)) {
				continue;
			}

			String versionPropertyName = VersionPropertyNames.buildPropertyName(useVersionSuffix, artifact.getArtifactId());
			if (versionProperties.getProperty(versionPropertyName) != null
					&& !versionProperties.getProperty(versionPropertyName).equals(artifact.getVersion())) {
				versionPropertyName = VersionPropertyNames.buildPropertyNameForGroupAndArtifact(useVersionSuffix,
						artifact.getGroupId(), artifact.getArtifactId());
			}
			versionProperties.setProperty(versionPropertyName, artifact.getVersion());

			Dependency dep = new Dependency();
			dep.setGroupId(artifact.getGroupId());
			dep.setArtifactId(artifact.getArtifactId());
			dep.setVersion(artifact.getVersion());
			if (!StringUtils.isEmpty(artifact.getClassifier())) {
				dep.setClassifier(artifact.getClassifier());
			}
			if (!StringUtils.isEmpty(artifact.getType())) {
				dep.setType(artifact.getType());
			}
			if (exclusions != null) {
				applyExclusions(artifact, dep);
			}
			if (!depMgmt.getDependencies().contains(dep)) {
				depMgmt.addDependency(dep);
			}
		}
		pomModel.setDependencyManagement(depMgmt);
		if (addVersionProperties) {
			pomModel.getProperties().putAll(versionProperties);
		}
		getLog().debug("Added " + projectArtifacts.size() + " dependencies.");
	}

	/**
	 * @param pomModel
	 * @return
	 */
	private DependencyManagement retrieveDependencyManagement(Model pomModel) {
		DependencyManagement depMgmt = pomModel.getDependencyManagement();
		if (depMgmt == null) {
			depMgmt = new DependencyManagement();
		}
		return depMgmt;
	}

	/**
	 * @param pomModel
	 * @return
	 */
	private Properties retrieveProperties(Model pomModel) {
		Properties versionProperties = pomModel.getProperties();
		if (versionProperties == null) {
			versionProperties = new Properties();
		}
		return versionProperties;
	}

	boolean isExcludedDependency(Artifact artifact) {
		if (dependencyExclusions == null || dependencyExclusions.isEmpty()) {
			return false;
		}
		for (DependencyExclusion exclusion : dependencyExclusions) {
			if (matchesExcludedDependency(artifact, exclusion)) {
				getLog().debug("Artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId()
						+ " matches excluded dependency " + exclusion.getGroupId() + ":" + exclusion.getArtifactId());
				return true;
			}
		}
		return false;
	}

	boolean matchesExcludedDependency(Artifact artifact, DependencyExclusion exclusion) {
		String groupId = defaultAndTrim(artifact.getGroupId());
		String artifactId = defaultAndTrim(artifact.getArtifactId());
		String exclusionGroupId = defaultAndTrim(exclusion.getGroupId());
		String exclusionArtifactId = defaultAndTrim(exclusion.getArtifactId());
		boolean groupIdMatched = ("*".equals(exclusionGroupId) || groupId.equals(exclusionGroupId));
		boolean artifactIdMatched = ("*".equals(exclusionArtifactId) || artifactId.equals(exclusionArtifactId));
		return groupIdMatched && artifactIdMatched;
	}

	private String defaultAndTrim(String string) {
		return defaultString(trim(string), "");
	}

	private void applyExclusions(Artifact artifact, Dependency dep) {
		for (BomExclusion exclusion : exclusions) {
			if (exclusion.getDependencyGroupId().equals(artifact.getGroupId())
					&& exclusion.getDependencyArtifactId().equals(artifact.getArtifactId())) {
				Exclusion ex = new Exclusion();
				ex.setGroupId(exclusion.getExclusionGroupId());
				ex.setArtifactId(exclusion.getExclusionArtifactId());
				dep.addExclusion(ex);
			}
		}
	}

	static class ModelWriter {

		void writeModel(Model pomModel, File outputFile) throws MojoExecutionException {
			if (!outputFile.getParentFile().exists()) {
				outputFile.getParentFile().mkdirs();
			}
			try (FileWriter writer = new FileWriter(outputFile)) {
				MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
				mavenWriter.write(writer, pomModel);
			} catch (IOException e) {
				throw new MojoExecutionException("Unable to write pom file.", e);
			}

		}
	}

}

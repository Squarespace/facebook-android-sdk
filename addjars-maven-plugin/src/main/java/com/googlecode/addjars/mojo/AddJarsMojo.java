/*
 * AddJars Maven Plugin
 * Copyright (C) 2012 Vasily Karyaev <v.karyaev@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.addjars.mojo;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.WriterFactory;


/**
 * Adds arbitrary jars to project's classpath.
 * 
 * @goal add-jars
 * @phase generate-sources
 *
 * @author Vasily Karyaev <v.karyaev@gmail.com>
 */
public class AddJarsMojo extends AbstractMojo {
	
	/**
	 * @parameter
	 * @required 
	 * @readonly
	 */
	private List<JarResource> resources;

	/**
	 * @parameter expression="${project}" 
	 * @required 
	 * @readonly
	 */
	private MavenProject project;
	
	/**
	 * @component
	 */
	private ArtifactFactory artifactFactory;
	
	/**
	 * @component
	 */
	private ArtifactInstaller artifactInstaller;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			executeInt();
		} catch(MojoFailureException e) {
			throw e;
		} catch(MojoExecutionException e) {
			throw e;
		} catch(Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}
	
	private void executeInt() throws Exception {
		File workdir = new File(project.getBuild().getDirectory(), getClass().getName());
		workdir.mkdirs();
		
		Set<Artifact> dependenciesArtifacts = new HashSet<Artifact>();		
		
		for(JarResource resource: resources) {
			for(File jar: getJars(resource)) {
				Artifact a = artifactFactory.createArtifact(project.getGroupId(), project.getArtifactId()+"-"+jar.getName(), project.getVersion(), resource.getScope(), "jar");
				
				File stamp = new File(workdir, a.getArtifactId());
				if(jar.lastModified() > stamp.lastModified()) {
				    a.addMetadata(new ProjectArtifactMetadata(a, createArtifactPom(a)));
				    artifactInstaller.install(jar, a, null);
				    stamp.createNewFile();
				    stamp.setLastModified(jar.lastModified());
				}
				
				dependenciesArtifacts.add(a);
			}
		}
		
		Set newDependenciesArtifacts = new HashSet(project.getDependencyArtifacts());
		newDependenciesArtifacts.addAll(dependenciesArtifacts);
		
		project.setDependencyArtifacts(newDependenciesArtifacts);
		
		for (Artifact dependency : dependenciesArtifacts)
			project.getOriginalModel().addDependency(createDependency(dependency));
		
		File pomFile = new File(workdir, "pom.xml");
		writePom(pomFile, project.getOriginalModel());
		project.setFile(pomFile);
	}
	
	private List<File> getJars(JarResource resource) throws IOException {
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(resource.getDirectory());
		if(resource.getIncludes() != null) {
			scanner.setIncludes(resource.getIncludes().toArray(new String[] { }));
		}
		if(resource.getExcludes() != null) {
			scanner.setExcludes(resource.getExcludes().toArray(new String[] { }));
		}
		
		try {
			scanner.scan();
		} catch(IllegalStateException e) {
			getLog().warn("Not a directory: " + resource.getDirectory());
			return Collections.emptyList();
		}
		
		List<File> files = new ArrayList<File>();
		for(String file: scanner.getIncludedFiles()) {
			File f = new File(resource.getDirectory(), file).getCanonicalFile();
			if(f.getName().endsWith(".jar")) {
				files.add(f);
			} else {
				getLog().warn("Not a jar: " + f);
			}
		}
		
		Collections.sort(files);
		return files;
	}
	
	private Dependency createDependency(Artifact a) {
		Dependency d = new Dependency();
		d.setGroupId(a.getGroupId());
		d.setArtifactId(a.getArtifactId());
		d.setVersion(a.getVersion());
		d.setScope(a.getScope());
		d.setType(a.getType());
		return d;
	}
	
	private File createArtifactPom(Artifact a) throws IOException {
		File pomFile = File.createTempFile(a.getArtifactId(), ".pom");
		writePom(pomFile, createModel(a));
		return pomFile;
	}

	private Model createModel(Artifact a) {
		Model model = new Model();
		model.setModelVersion("4.0.0");
		model.setGroupId(a.getGroupId());
		model.setArtifactId(a.getArtifactId());
		model.setVersion(a.getVersion());
		model.setPackaging(a.getType());
		return model;
	}
	
	private void writePom(File pom, Model model) throws IOException {
		Writer writer = WriterFactory.newXmlWriter(pom);
		new MavenXpp3Writer().write(writer, model);
		writer.close();
	}
}

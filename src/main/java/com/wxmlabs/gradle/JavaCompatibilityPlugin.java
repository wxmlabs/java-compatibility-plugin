package com.wxmlabs.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

@SuppressWarnings("unused")
public class JavaCompatibilityPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.getTasks().create("checkCompatibility", CheckCompatibility.class);
  }
}

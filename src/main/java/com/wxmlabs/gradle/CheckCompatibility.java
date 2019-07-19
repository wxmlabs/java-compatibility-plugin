package com.wxmlabs.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CheckCompatibility extends DefaultTask {
  @TaskAction
  public void checkCompatibility() {
    Project project = getProject();
    AtomicBoolean isCompatibility = new AtomicBoolean(true);
    JavaVersion targetCompatibility = (JavaVersion) project.property("targetCompatibility");
    Configuration config = project.getConfigurations().getByName("runtimeClasspath");
    config.getFiles().stream()
        .filter((file) -> file.isFile() && file.getName().endsWith(".jar"))
        .forEach(
            (jarFile) -> {
              JavaVersion classVersion = getClassVersion(jarFile);
              if (classVersion != null
                  && targetCompatibility != null
                  && Float.valueOf(classVersion.toString())
                      > Float.valueOf(targetCompatibility.toString())) {
                isCompatibility.set(false);
                report(jarFile);
              }
            });
    // if not compatibility then task failed
    if (!isCompatibility.get()) {
      throw new IllegalDependencyNotation("Check compatibility failed. See compatibility-report.txt for details.");
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private JavaVersion getClassVersion(File file) {
    try (ZipFile jarFile = new ZipFile(file)) {
      Optional<? extends ZipEntry> classEntryOptional =
          jarFile.stream().filter((entry) -> entry.getName().endsWith(".class")).findFirst();
      if (classEntryOptional.isPresent()) {
        ZipEntry classEntry = classEntryOptional.get();
        try (InputStream classInStream = jarFile.getInputStream(classEntry)) {
          // read class major version
          // see https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html
          byte[] majorVersion = new byte[2];
          classInStream.skip(6);
          classInStream.read(majorVersion);
          @SuppressWarnings("UnnecessaryLocalVariable")
          JavaVersion classVersion = JavaVersion.forClassVersion(majorVersion[1] & 0xFF);
          return classVersion;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private final String DEP_INFO_REGEX =
      ".*[/\\\\]([^/\\\\]+)[/\\\\]([^/\\\\]+)[/\\\\]([^/\\\\]+)[/\\\\]([^/\\\\]+)[/\\\\]([^/\\\\]+)$";
  private final Pattern DEP_INFO_PATTERN = Pattern.compile(DEP_INFO_REGEX);

  private void report(File jarFile) {
    Project project = getProject();
    File outputDir = new File(project.getBuildDir(), "compatibility");
    if (!outputDir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      outputDir.mkdirs();
    }
    File output = new File(outputDir, "compatibility-report.txt");
    Matcher m = DEP_INFO_PATTERN.matcher(jarFile.getPath());
    if (m.matches() && m.groupCount() > 0) {
      String group = m.group(1);
      String name = m.group(2);
      String version = m.group(3);
      try (FileWriter writer = new FileWriter(output)) {
        try (PrintWriter printWriter = new PrintWriter(writer)) {
          printWriter.println(String.format("'%s:%s:%s'", group, name, version));
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}

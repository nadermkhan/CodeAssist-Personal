package com.tyron.builder.compiler;

import com.tyron.builder.compiler.apk.PackageTask;
import com.tyron.builder.compiler.apk.SignTask;
import com.tyron.builder.compiler.apk.ZipAlignTask;
import com.tyron.builder.compiler.buildconfig.GenerateDebugBuildConfigTask;
import com.tyron.builder.compiler.buildconfig.GenerateReleaseBuildConfigTask;
import com.tyron.builder.compiler.dex.R8Task;
import com.tyron.builder.compiler.firebase.GenerateFirebaseConfigTask;
import com.tyron.builder.compiler.incremental.dex.IncrementalD8Task;
import com.tyron.builder.compiler.incremental.java.IncrementalJavaFormatTask;
import com.tyron.builder.compiler.incremental.java.IncrementalJavaTask;
import com.tyron.builder.compiler.incremental.kotlin.IncrementalKotlinCompiler;
import com.tyron.builder.compiler.incremental.kotlin.IncrementalKotlinFormatTask;
import com.tyron.builder.compiler.incremental.resource.IncrementalAapt2Task;
import com.tyron.builder.compiler.incremental.resource.IncrementalAssembleLibraryTask;
import com.tyron.builder.compiler.java.CheckLibrariesTask;
import com.tyron.builder.compiler.log.InjectLoggerTask;
import com.tyron.builder.compiler.manifest.ManifestMergeTask;
import com.tyron.builder.compiler.symbol.MergeSymbolsTask;
import com.tyron.builder.compiler.viewbinding.GenerateViewBindingTask;
import com.tyron.builder.crashlytics.CrashlyticsTask;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;

public class AndroidAppBuilder extends BuilderImpl<AndroidModule> {

  public AndroidAppBuilder(Project project, AndroidModule module, ILogger logger) {
    super(project, module, logger);
  }

  @Override
  public List<Task<? super AndroidModule>> getTasks(BuildType type) {

    AndroidModule module = getModule();
    ILogger logger = getLogger();

    List<Task<? super AndroidModule>> tasks = new ArrayList<>();
    tasks.add(new CleanTask(getProject(), module, logger));

    tasks.add(new IncrementalKotlinFormatTask(getProject(), module, logger));
    tasks.add(new IncrementalJavaFormatTask(getProject(), module, logger));

    tasks.add(new CheckLibrariesTask(getProject(), module, logger));

    try {
      File buildSettings =
          new File(
              getProject().getRootFile(),
              ".idea/" + getProject().getRootName() + "_compiler_settings.json");
      String content = new String(Files.readAllBytes(Paths.get(buildSettings.getAbsolutePath())));

      JSONObject buildSettingsJson = new JSONObject(content);

      boolean isDexLibrariesOnPrebuild =
          Optional.ofNullable(buildSettingsJson.optJSONObject("dex"))
              .map(json -> json.optString("isDexLibrariesOnPrebuild", "false"))
              .map(Boolean::parseBoolean)
              .orElse(false);

      if (isDexLibrariesOnPrebuild) {
        tasks.add(new IncrementalD8Task(getProject(), module, logger));
      }
    } catch (Exception e) {
    }

    tasks.add(new IncrementalAssembleLibraryTask(getProject(), module, logger));
    tasks.add(new ManifestMergeTask(getProject(), module, logger));
    if (type == BuildType.DEBUG) {
      tasks.add(new GenerateDebugBuildConfigTask(getProject(), module, logger));
    } else {
      tasks.add(new GenerateReleaseBuildConfigTask(getProject(), module, logger));
    }
    tasks.add(new GenerateFirebaseConfigTask(getProject(), module, logger));
    if (type == BuildType.DEBUG) {
      tasks.add(new InjectLoggerTask(getProject(), module, logger));
    }
    tasks.add(new CrashlyticsTask(getProject(), module, logger));
    tasks.add(new IncrementalAapt2Task(getProject(), module, logger, false));
    tasks.add(new GenerateViewBindingTask(getProject(), module, logger, true));

    tasks.add(new MergeSymbolsTask(getProject(), module, logger));
    tasks.add(new IncrementalKotlinCompiler(getProject(), module, logger));
    tasks.add(new IncrementalJavaTask(getProject(), module, logger));
    if (module.getMinifyEnabled() && type == BuildType.RELEASE) {
      tasks.add(new R8Task(getProject(), module, logger));
    } else {
      tasks.add(new IncrementalD8Task(getProject(), module, logger));
    }
    tasks.add(new PackageTask(getProject(), module, logger));
    if (module.getZipAlignEnabled()) {
      tasks.add(new ZipAlignTask(getProject(), module, logger));
    }
    tasks.add(new SignTask(getProject(), module, logger));
    return tasks;
  }
}

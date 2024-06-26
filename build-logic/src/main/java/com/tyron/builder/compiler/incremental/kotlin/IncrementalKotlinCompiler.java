package com.tyron.builder.compiler.incremental.kotlin;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Throwables;
import com.tyron.builder.BuildModule;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.Task;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.common.util.BinaryExecutor;
import com.tyron.common.util.ExecutionResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import kotlin.jvm.functions.Function0;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.build.report.ICReporterBase;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.incremental.IncrementalJvmCompilerRunnerKt;
import org.json.JSONObject;

public class IncrementalKotlinCompiler extends Task<AndroidModule> {

  private static final String TAG = "compileKotlin";

  private File mKotlinHome;
  private File mClassOutput;
  private List<File> mFilesToCompile;

  private final MessageCollector mCollector = new Collector();

  public IncrementalKotlinCompiler(Project project, AndroidModule module, ILogger logger) {
    super(project, module, logger);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void prepare(BuildType type) throws IOException {
    mFilesToCompile = new ArrayList<>();
    File javaDir = new File(getModule().getRootFile() + "/src/main/java");
    File kotlinDir = new File(getModule().getRootFile() + "/src/main/kotlin");

    mFilesToCompile.addAll(getSourceFiles(javaDir));
    mFilesToCompile.addAll(getSourceFiles(kotlinDir));

    //        mKotlinHome = new File(BuildModule.getContext().getFilesDir(), "kotlin-home");
    //        if (!mKotlinHome.exists() && !mKotlinHome.mkdirs()) {
    //            throw new IOException("Unable to create kotlin home directory");
    //        }

    mClassOutput = new File(getModule().getBuildDirectory(), "bin/kotlin/classes");
    if (!mClassOutput.exists() && !mClassOutput.mkdirs()) {
      throw new IOException("Unable to create class output directory");
    }
  }

  @Override
  public void run() throws IOException, CompilationFailedException {
    if (mFilesToCompile.stream().noneMatch(file -> file.getName().endsWith(".kt"))) {
      Log.i(TAG, "No kotlin source files, Skipping compilation.");
      return;
    }

    try {
      File buildSettings =
          new File(
              getModule().getProjectDir(),
              ".idea/" + getModule().getRootFile().getName() + "_compiler_settings.json");
      String content = new String(Files.readAllBytes(Paths.get(buildSettings.getAbsolutePath())));

      JSONObject buildSettingsJson = new JSONObject(content);

      boolean isCompilerEnabled =
          Boolean.parseBoolean(
              buildSettingsJson.optJSONObject("kotlin").optString("isCompilerEnabled", "false"));

      String jvm_target = buildSettingsJson.optJSONObject("kotlin").optString("jvmTarget", "1.8");

      // String language_version =
      //     buildSettingsJson.optJSONObject("kotlin").optString("languageVersion", "2.1");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        BuildModule.getKotlinc().setReadOnly();
      }

      if (!isCompilerEnabled) {

        File api_files = new File(getModule().getRootFile(), "/build/libraries/api_files/libs");
        File api_libs = new File(getModule().getRootFile(), "/build/libraries/api_libs");
        File kotlinOutputDir = new File(getModule().getBuildDirectory(), "bin/kotlin/classes");
        File javaOutputDir = new File(getModule().getBuildDirectory(), "bin/java/classes");
        File implementation_files =
            new File(getModule().getRootFile(), "/build/libraries/implementation_files/libs");
        File implementation_libs =
            new File(getModule().getRootFile(), "/build/libraries/implementation_libs");

        File runtimeOnly_files =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnly_files/libs");
        File runtimeOnly_libs =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnly_libs");

        File compileOnly_files =
            new File(getModule().getRootFile(), "/build/libraries/compileOnly_files/libs");
        File compileOnly_libs =
            new File(getModule().getRootFile(), "/build/libraries/compileOnly_libs");

        File runtimeOnlyApi_files =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnlyApi_files/libs");
        File runtimeOnlyApi_libs =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnlyApi_libs");

        File compileOnlyApi_files =
            new File(getModule().getRootFile(), "/build/libraries/compileOnlyApi_files/libs");
        File compileOnlyApi_libs =
            new File(getModule().getRootFile(), "/build/libraries/compileOnlyApi_libs");

        List<File> compileClassPath = new ArrayList<>();
        compileClassPath.addAll(getJarFiles(api_files));
        compileClassPath.addAll(getJarFiles(api_libs));
        compileClassPath.addAll(getJarFiles(implementation_files));
        compileClassPath.addAll(getJarFiles(implementation_libs));
        compileClassPath.addAll(getJarFiles(compileOnly_files));
        compileClassPath.addAll(getJarFiles(compileOnly_libs));
        compileClassPath.addAll(getJarFiles(compileOnlyApi_files));
        compileClassPath.addAll(getJarFiles(compileOnlyApi_libs));

        compileClassPath.add(javaOutputDir);
        compileClassPath.add(kotlinOutputDir);

        List<File> runtimeClassPath = new ArrayList<>();
        runtimeClassPath.addAll(getJarFiles(runtimeOnly_files));
        runtimeClassPath.addAll(getJarFiles(runtimeOnly_libs));
        runtimeClassPath.addAll(getJarFiles(runtimeOnlyApi_files));
        runtimeClassPath.addAll(getJarFiles(runtimeOnlyApi_libs));
        runtimeClassPath.add(getModule().getBootstrapJarFile());
        runtimeClassPath.add(getModule().getLambdaStubsJarFile());
        runtimeClassPath.addAll(getJarFiles(api_files));
        runtimeClassPath.addAll(getJarFiles(api_libs));

        runtimeClassPath.add(javaOutputDir);
        runtimeClassPath.add(kotlinOutputDir);

        List<File> classpath = new ArrayList<>();
        classpath.add(getModule().getBuildClassesDirectory());
        classpath.addAll(getModule().getLibraries());
        classpath.addAll(compileClassPath);
        classpath.addAll(runtimeClassPath);

        List<String> arguments = new ArrayList<>();
        Collections.addAll(
            arguments,
            "-cp",
            classpath.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)));
        arguments.add("-Xskip-metadata-version-check");
        arguments.add("-Xjvm-default=all");

        File javaDir = new File(getModule().getRootFile() + "/src/main/java");
        File kotlinDir = new File(getModule().getRootFile() + "/src/main/kotlin");
        File buildGenDir = new File(getModule().getRootFile() + "/build/gen");
        File viewBindingDir = new File(getModule().getRootFile() + "/build/view_binding");

        List<File> javaSourceRoots = new ArrayList<>();
        if (javaDir.exists()) {
          javaSourceRoots.addAll(getFiles(javaDir, ".java"));
        }
        if (buildGenDir.exists()) {
          javaSourceRoots.addAll(getFiles(buildGenDir, ".java"));
        }
        if (viewBindingDir.exists()) {
          javaSourceRoots.addAll(getFiles(viewBindingDir, ".java"));
        }

        K2JVMCompiler compiler = new K2JVMCompiler();
        K2JVMCompilerArguments args = new K2JVMCompilerArguments();
        compiler.parseArguments(arguments.toArray(new String[0]), args);

        args.setUseJavac(false);
        args.setCompileJava(false);
        args.setIncludeRuntime(false);
        args.setNoJdk(true);
        args.setModuleName(getModule().getRootFile().getName());
        args.setNoReflect(true);
        args.setNoStdlib(true);
        args.setSuppressWarnings(true);
        args.setJavaSourceRoots(
            javaSourceRoots.stream().map(File::getAbsolutePath).toArray(String[]::new));
        // args.setKotlinHome(mKotlinHome.getAbsolutePath());
        args.setDestination(mClassOutput.getAbsolutePath());

        List<File> plugins = getPlugins();
        getLogger().debug("Loading kotlin compiler plugins: " + plugins);

        args.setPluginClasspaths(
            plugins.stream().map(File::getAbsolutePath).toArray(String[]::new));
        args.setPluginOptions(getPluginOptions());

        File cacheDir = new File(getModule().getBuildDirectory(), "kotlin/compileKotlin/cacheable");

        List<File> fileList = new ArrayList<>();
        if (javaDir.exists()) {
          fileList.add(javaDir);
        }
        if (buildGenDir.exists()) {
          fileList.add(buildGenDir);
        }
        if (viewBindingDir.exists()) {
          fileList.add(viewBindingDir);
        }
        if (kotlinDir.exists()) {
          fileList.add(kotlinDir);
        }

        IncrementalJvmCompilerRunnerKt.makeIncrementally(
            cacheDir,
            Arrays.asList(fileList.toArray(new File[0])),
            args,
            mCollector,
            new ICReporterBase() {
              @Override
              public void report(@NonNull Function0<String> function0) {
                // getLogger().info()
                function0.invoke();
              }

              @Override
              public void reportVerbose(@NonNull Function0<String> function0) {
                // getLogger().verbose()
                function0.invoke();
              }

              @Override
              public void reportCompileIteration(
                  boolean incremental,
                  @NonNull Collection<? extends File> sources,
                  @NonNull ExitCode exitCode) {}
            });
        if (mCollector.hasErrors()) {
          throw new CompilationFailedException("Compilation failed, see logs for more details");
        }
      } else {

        File api_files = new File(getModule().getRootFile(), "/build/libraries/api_files/libs");
        File api_libs = new File(getModule().getRootFile(), "/build/libraries/api_libs");
        File kotlinOutputDir = new File(getModule().getBuildDirectory(), "bin/kotlin/classes");
        File javaOutputDir = new File(getModule().getBuildDirectory(), "bin/java/classes");
        File implementation_files =
            new File(getModule().getRootFile(), "/build/libraries/implementation_files/libs");
        File implementation_libs =
            new File(getModule().getRootFile(), "/build/libraries/implementation_libs");

        File runtimeOnly_files =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnly_files/libs");
        File runtimeOnly_libs =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnly_libs");

        File compileOnly_files =
            new File(getModule().getRootFile(), "/build/libraries/compileOnly_files/libs");
        File compileOnly_libs =
            new File(getModule().getRootFile(), "/build/libraries/compileOnly_libs");

        File runtimeOnlyApi_files =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnlyApi_files/libs");
        File runtimeOnlyApi_libs =
            new File(getModule().getRootFile(), "/build/libraries/runtimeOnlyApi_libs");

        File compileOnlyApi_files =
            new File(getModule().getRootFile(), "/build/libraries/compileOnlyApi_files/libs");
        File compileOnlyApi_libs =
            new File(getModule().getRootFile(), "/build/libraries/compileOnlyApi_libs");

        List<File> compileClassPath = new ArrayList<>();
        compileClassPath.addAll(getJarFiles(api_files));
        compileClassPath.addAll(getJarFiles(api_libs));
        compileClassPath.addAll(getJarFiles(implementation_files));
        compileClassPath.addAll(getJarFiles(implementation_libs));
        compileClassPath.addAll(getJarFiles(compileOnly_files));
        compileClassPath.addAll(getJarFiles(compileOnly_libs));
        compileClassPath.addAll(getJarFiles(compileOnlyApi_files));
        compileClassPath.addAll(getJarFiles(compileOnlyApi_libs));

        compileClassPath.add(javaOutputDir);
        compileClassPath.add(kotlinOutputDir);

        List<File> runtimeClassPath = new ArrayList<>();
        runtimeClassPath.addAll(getJarFiles(runtimeOnly_files));
        runtimeClassPath.addAll(getJarFiles(runtimeOnly_libs));
        runtimeClassPath.addAll(getJarFiles(runtimeOnlyApi_files));
        runtimeClassPath.addAll(getJarFiles(runtimeOnlyApi_libs));
        runtimeClassPath.add(getModule().getBootstrapJarFile());
        runtimeClassPath.add(getModule().getLambdaStubsJarFile());
        runtimeClassPath.addAll(getJarFiles(api_files));
        runtimeClassPath.addAll(getJarFiles(api_libs));

        runtimeClassPath.add(javaOutputDir);
        runtimeClassPath.add(kotlinOutputDir);

        List<File> classpath = new ArrayList<>();
        classpath.add(getModule().getBuildClassesDirectory());
        classpath.addAll(getModule().getLibraries());
        classpath.addAll(compileClassPath);
        classpath.addAll(runtimeClassPath);

        List<String> arguments = new ArrayList<>();
        Collections.addAll(
            arguments,
            classpath.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)));

        File javaDir = new File(getModule().getRootFile() + "/src/main/java");
        File kotlinDir = new File(getModule().getRootFile() + "/src/main/kotlin");
        File buildGenDir = new File(getModule().getRootFile() + "/build/gen");
        File viewBindingDir = new File(getModule().getRootFile() + "/build/view_binding");

        List<File> javaSourceRoots = new ArrayList<>();
        if (javaDir.exists()) {
          javaSourceRoots.add(javaDir);
        }
        if (buildGenDir.exists()) {
          javaSourceRoots.add(buildGenDir);
        }
        if (viewBindingDir.exists()) {
          javaSourceRoots.add(viewBindingDir);
        }

        List<File> fileList = new ArrayList<>();
        if (javaDir.exists()) {
          fileList.add(javaDir);
        }
        if (buildGenDir.exists()) {
          fileList.add(buildGenDir);
        }
        if (viewBindingDir.exists()) {
          fileList.add(viewBindingDir);
        }
        if (kotlinDir.exists()) {
          fileList.add(kotlinDir);
        }

        List<File> plugins = getPlugins();
        getLogger().debug("Loading kotlin compiler plugins: " + plugins);

        /*  String[] command =
            new String[] {
              "dalvikvm",
              "-Xcompiler-option",
              "--compiler-filter=speed",
              "-Xmx256m",
              "-cp",
              compiler_path,
              "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
              "-no-reflect",
              "-no-jdk",
              "-no-stdlib",
              "-jvm-target",
              jvm_target,
              //  "-language-version",
              //  language_version,
              "-cp",
              Arrays.toString(arguments.toArray(new String[0])).replace("[", "").replace("]", ""),
              "-Xjava-source-roots="
                  + Arrays.toString(
                          javaSourceRoots.stream()
                              .map(File::getAbsolutePath)
                              .toArray(String[]::new))
                      .replace("[", "")
                      .replace("]", "")
            };

        for (File file : fileList) {
          command = appendElement(command, file.getAbsolutePath());
        }

        command = appendElement(command, "-d");
        command = appendElement(command, mClassOutput.getAbsolutePath());
        command = appendElement(command, "-module-name");
        command = appendElement(command, getModule().getRootFile().getName());
        command = appendElement(command, "-P");

        String plugin = "";
        String pluginString =
            Arrays.toString(plugins.stream().map(File::getAbsolutePath).toArray(String[]::new))
                .replace("[", "")
                .replace("]", "");

        String pluginOptionsString =
            Arrays.toString(getPluginOptions()).replace("[", "").replace("]", "");

        plugin = pluginString + ":" + (pluginOptionsString.isEmpty() ? ":=" : pluginOptionsString);

        command = appendElement(command, "plugin:" + plugin);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder(); // To store the output

        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n"); // Append each line to the output
        }

        String message = output.toString();

        if (!message.isEmpty()) {
          getLogger().info(output.toString());
        }

        process.waitFor();

        if (output.toString().contains("error")) {
          throw new CompilationFailedException("Compilation failed, see logs for more details");
        }*/

        List<String> args = new ArrayList<>();
        args.add("dalvikvm");
        args.add("-Xcompiler-option");
        args.add("--compiler-filter=speed");
        args.add("-Xmx256m");
        args.add("-cp");
        args.add(BuildModule.getKotlinc().getAbsolutePath());
        args.add("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler");

        args.add("-no-jdk");
        args.add("-no-stdlib");
        args.add("-no-reflect");
        args.add("-jvm-target");
        args.add(jvm_target);
        args.add("-cp");
        args.add(String.join(", ", arguments));
        args.add(
            "-Xjava-source-roots="
                + String.join(
                    ", ",
                    javaSourceRoots.stream().map(File::getAbsolutePath).toArray(String[]::new)));

        for (File file : fileList) {
          args.add(file.getAbsolutePath());
        }
        args.add("-Xjvm-default=all");
        args.add("-d");
        args.add(mClassOutput.getAbsolutePath());

        args.add("-module-name");
        args.add(getModule().getRootFile().getName());

        String plugin = "";
        String pluginString =
            Arrays.toString(plugins.stream().map(File::getAbsolutePath).toArray(String[]::new))
                .replace("[", "")
                .replace("]", "");

        String pluginOptionsString =
            Arrays.toString(getPluginOptions()).replace("[", "").replace("]", "");

        plugin = pluginString + ":" + (pluginOptionsString.isEmpty() ? ":=" : pluginOptionsString);

        args.add("-P");
        args.add("plugin:" + plugin);

        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(args);
        ExecutionResult result = executor.run();

        getLogger().info(executor.getLog().trim());

        if (result != null) {
          if (result.getExitValue() != 0) {
            getLogger().info(result.getOutput().trim());
            throw new CompilationFailedException("Compilation failed, see logs for more details");
          }
        }
      }

    } catch (Exception e) {
      throw new CompilationFailedException(Throwables.getStackTraceAsString(e));
    }
  }

  private String[] appendElement(String[] array, String element) {
    String[] newArray = new String[array.length + 1];
    System.arraycopy(array, 0, newArray, 0, array.length);
    newArray[array.length] = element;
    return newArray;
  }

  private List<File> getSourceFiles(File dir) {
    List<File> files = new ArrayList<>();

    File[] children = dir.listFiles();
    if (children != null) {
      for (File child : children) {
        if (child.isDirectory()) {
          files.addAll(getSourceFiles(child));
        } else {
          if (child.getName().endsWith(".kt") || child.getName().endsWith(".java")) {
            files.add(child);
          }
        }
      }
    }

    return files;
  }

  public static Set<File> getFiles(File dir, String ext) {
    Set<File> Files = new HashSet<>();

    File[] files = dir.listFiles();
    if (files == null) {
      return Collections.emptySet();
    }

    for (File file : files) {
      if (file.isDirectory()) {
        Files.addAll(getFiles(file, ext));
      } else {
        if (file.getName().endsWith(ext)) {
          Files.add(file);
        }
      }
    }

    return Files;
  }

  private List<File> getPlugins() {
    File pluginDir = new File(getModule().getBuildDirectory(), "plugins");
    File[] children = pluginDir.listFiles(c -> c.getName().endsWith(".jar"));

    if (children == null) {
      return Collections.emptyList();
    }

    return Arrays.stream(children).collect(Collectors.toList());
  }

  private String[] getPluginOptions() throws IOException {
    File pluginDir = new File(getModule().getBuildDirectory(), "plugins");
    File args = new File(pluginDir, "args.txt");
    if (!args.exists()) {
      return new String[0];
    }

    String string = FileUtils.readFileToString(args, StandardCharsets.UTF_8);
    return string.split(" ");
  }

  private static class Diagnostic extends DiagnosticWrapper {
    private final CompilerMessageSeverity mSeverity;
    private final String mMessage;
    private final CompilerMessageSourceLocation mLocation;

    public Diagnostic(
        CompilerMessageSeverity severity, String message, CompilerMessageSourceLocation location) {
      mSeverity = severity;
      mMessage = message;

      if (location == null) {
        mLocation =
            new CompilerMessageSourceLocation() {
              @NonNull
              @Override
              public String getPath() {
                return "UNKNOWN";
              }

              @Override
              public int getLine() {
                return 0;
              }

              @Override
              public int getColumn() {
                return 0;
              }

              @Override
              public int getLineEnd() {
                return 0;
              }

              @Override
              public int getColumnEnd() {
                return 0;
              }

              @Override
              public String getLineContent() {
                return "";
              }
            };
      } else {
        mLocation = location;
      }
    }

    @Override
    public File getSource() {
      if (mLocation == null || TextUtils.isEmpty(mLocation.getPath())) {
        return new File("UNKNOWN");
      }
      return new File(mLocation.getPath());
    }

    @Override
    public Kind getKind() {
      switch (mSeverity) {
        case ERROR:
          return Kind.ERROR;
        case STRONG_WARNING:
          return Kind.MANDATORY_WARNING;
        case WARNING:
          return Kind.WARNING;
        case LOGGING:
          return Kind.OTHER;
        default:
        case INFO:
          return Kind.NOTE;
      }
    }

    @Override
    public long getLineNumber() {
      return mLocation.getLine();
    }

    @Override
    public long getColumnNumber() {
      return mLocation.getColumn();
    }

    @Override
    public String getMessage(Locale locale) {
      return mMessage;
    }
  }

  public List<File> getJarFiles(File dir) {
    List<File> jarFiles = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".jar")) {
          // Check if the JarFile is valid before adding it to the list
          if (isJarFileValid(file)) {
            jarFiles.add(file);
          }
        } else if (file.isDirectory()) {
          // Recursively add JarFiles from subdirectories
          jarFiles.addAll(getJarFiles(file));
        }
      }
    }
    return jarFiles;
  }

  public boolean isJarFileValid(File file) {
    String message = "File " + file.getParentFile().getName() + " is corrupt! Ignoring.";
    try {
      // Try to open the JarFile
      JarFile jarFile = new JarFile(file);
      // If it opens successfully, close it and return true
      jarFile.close();
      return true;
    } catch (ZipException e) {
      // If the JarFile is invalid, it will throw a ZipException
      getLogger().warning(message);
      return false;
    } catch (IOException e) {
      // If there is some other error reading the JarFile, return false
      getLogger().warning(message);
      return false;
    }
  }

  private class Collector implements MessageCollector {

    private final List<Diagnostic> mDiagnostics = new ArrayList<>();
    private boolean mHasErrors;

    @Override
    public void clear() {
      mDiagnostics.clear();
    }

    @Override
    public boolean hasErrors() {
      return mHasErrors;
    }

    @Override
    public void report(
        @NotNull CompilerMessageSeverity severity,
        @NotNull String message,
        CompilerMessageSourceLocation location) {
      if (message.contains("No class roots are found in the JDK path")) {
        // Android does not have JDK so its okay to ignore this error
        return;
      }
      Diagnostic diagnostic = new Diagnostic(severity, message, location);
      mDiagnostics.add(diagnostic);

      switch (severity) {
        case ERROR:
          mHasErrors = true;
          getLogger().error(diagnostic);
          break;
        case STRONG_WARNING:
        case WARNING:
          getLogger().warning(diagnostic);
          break;
        case INFO:
          getLogger().info(diagnostic);
          break;
        default:
          getLogger().debug(diagnostic);
      }
    }
  }
}

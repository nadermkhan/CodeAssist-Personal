package com.tyron.builder;

import android.content.Context;
import com.tyron.common.util.Decompress;
import java.io.File;

public class BuildModule {

  private static Context sApplicationContext;
  private static File sAndroidJar;
  private static File sLambdaStubs;
  private static File sKotlincZip;
  private static File sJavacZip;

  public static void initialize(Context applicationContext) {
    sApplicationContext = applicationContext.getApplicationContext();
  }

  public static Context getContext() {
    return sApplicationContext;
  }

  public static File getAndroidJar() {
    if (sAndroidJar == null) {
      Context context = BuildModule.getContext();
      if (context == null) {
        return null;
      }

      sAndroidJar = new File(context.getFilesDir(), "rt.jar");
      if (!sAndroidJar.exists()) {
        Decompress.unzipFromAssets(
            BuildModule.getContext(), "rt.zip", sAndroidJar.getParentFile().getAbsolutePath());
      }
    }

    return sAndroidJar;
  }

  public static File getLambdaStubs() {
    if (sLambdaStubs == null) {
      sLambdaStubs = new File(BuildModule.getContext().getFilesDir(), "core-lambda-stubs.jar");

      if (!sLambdaStubs.exists()) {
        Decompress.unzipFromAssets(
            BuildModule.getContext(),
            "lambda-stubs.zip",
            sLambdaStubs.getParentFile().getAbsolutePath());
      }
    }
    return sLambdaStubs;
  }

  public static File getKotlinc() {
    if (sKotlincZip == null) {
      sKotlincZip = new File(BuildModule.getContext().getFilesDir(), "kotlinc.jar");

      if (!sKotlincZip.exists()) {
        Decompress.unzipFromAssets(
            BuildModule.getContext(), "kotlinc.zip", sKotlincZip.getParentFile().getAbsolutePath());
      }
    }
    return sKotlincZip;
  }

  public static File getJavac() {
    if (sJavacZip == null) {
      sJavacZip = new File(BuildModule.getContext().getFilesDir(), "javac.jar");

      if (!sJavacZip.exists()) {
        Decompress.unzipFromAssets(
            BuildModule.getContext(), "javac.zip", sJavacZip.getParentFile().getAbsolutePath());
      }
    }
    return sJavacZip;
  }
}

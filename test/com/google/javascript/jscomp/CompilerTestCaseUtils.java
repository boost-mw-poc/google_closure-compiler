/*
 * Copyright 2017 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** CompilerTestCase utilities */
public final class CompilerTestCaseUtils {
  public static Compiler multistageSerializeAndDeserialize(
      CompilerTestCase testCase,
      Compiler compiler,
      List<SourceFile> externs,
      List<SourceFile> inputs,
      CodeChangeHandler changeHandler) {
    new RemoveCastNodes(compiler).process(compiler.getExternsRoot(), compiler.getJsRoot());
    ErrorManager errorManager = compiler.getErrorManager();
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      compiler.removeChangeHandler(changeHandler);
      compiler.disableThreads();
      compiler.saveState(baos);

      try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
        compiler = testCase.createCompiler();
        compiler.disableThreads();
        compiler.init(externs, inputs, testCase.getOptions());
        compiler.restoreState(bais);
        compiler.setErrorManager(errorManager);
        compiler.addChangeHandler(changeHandler);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return compiler;
  }

  public static void setDebugLogDirectoryOn(CompilerOptions options) {
  }

  private CompilerTestCaseUtils() {}
}

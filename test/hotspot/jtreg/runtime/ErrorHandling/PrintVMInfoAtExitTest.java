/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Red Hat Inc.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


/*
 * @test
 * @summary Test PrintVMInfoAtExit
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.util
 * @requires vm.flagless
 * @requires vm.bits == "64"
 * @run driver PrintVMInfoAtExitTest
 */

import jdk.internal.util.OperatingSystem;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class PrintVMInfoAtExitTest {

  public static void main(String[] args) throws Exception {
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xmx64M", "-Xms64M",
            "-XX:-CreateCoredumpOnCrash",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintVMInfoAtExit",
            "-XX:NativeMemoryTracking=summary",
            "-XX:CompressedClassSpaceSize=256m",
            "-version");

    OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());
    output_detail.shouldContain("# JRE version:");
    output_detail.shouldContain("--  S U M M A R Y --");
    output_detail.shouldContain("Command Line: -Xmx64M -Xms64M -XX:-CreateCoredumpOnCrash -XX:+UnlockDiagnosticVMOptions -XX:+PrintVMInfoAtExit -XX:NativeMemoryTracking=summary -XX:CompressedClassSpaceSize=256m");
    output_detail.shouldContain("Native Memory Tracking:");
    output_detail.shouldContain("Java Heap (reserved=65536KB, committed=65536KB)");

    // -XX:+PrintVMInfoAtExit prints the host's OS name and version too. Each OS reports it
    // differently. Currently we assert the OS version only for macOS since that OS has occasionally
    // reported two different values for the same OS version. We expect the text in the output
    // to contain the OS version that matches the Java system property value for "os.version".
    if (OperatingSystem.isMacOS()) {
      System.out.println("verifying the reported OS version");
      // -XX:+PrintVMInfoAtExit prints to STDOUT
      assertMacOSVersion(output_detail.getStdout());
    }
  }

  // verifies that the macOS version reported in the output of -XX:+PrintVMInfoAtExit
  // matches the value of os.version Java system property
  private static void assertMacOSVersion(final String output) {
    String hostLine = null;
    for (final String line : output.split("\\n")) {
      if (line.startsWith("Host: ")) {
        hostLine = line;
        break;
      }
    }
    if (hostLine == null) {
      throw new AssertionError("Missing \"Host: \" line in the output");
    }
    final String expectedOSVersion = System.getProperty("os.version");
    final String expectedContent = " macOS " + expectedOSVersion + " ";
    if (!hostLine.contains(expectedContent)) {
      throw new AssertionError("\"" + expectedContent + "\" is missing in \"" + hostLine + "\"");
    }
  }
}



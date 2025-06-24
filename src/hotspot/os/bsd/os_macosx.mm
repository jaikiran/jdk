/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include <sys/sysctl.h>
#include <Foundation/Foundation.h>
#include "utilities/debug.hpp"
#include "runtime/os.hpp"

// Some macOS versions require special handling. For example, when
// sysctlbyname("kern.osproductversion") reports 10.16 as the version then
// it should be treated as 11. Similarly, when it reports 16.0 as the version then
// it should be treated as 26.
// If the SYSTEM_VERSION_COMPAT environment variable (a macOS construct) is set to 1, then we
// don't do any special handling for any versions and just literally use the value that
// sysctlbyname("kern.osproductversion").
// Returns TRUE if the reported version requires special handling, FALSE otherwise.

static bool version_requires_special_handling(const char* reported_version) {
    const char *env_val = getenv("SYSTEM_VERSION_COMPAT");
    const bool version_compat_enabled = env_val != nullptr && strncmp(env_val, "1", 1) == 0;
    if (version_compat_enabled) {
        return FALSE; // no special handling required for the macOS version
    }
    // next we split the version text into a major and minor version, with the dot character
    // as the delimiter

    char *tmp_copy = os::strdup(reported_version);
    if (tmp_copy == nullptr) {
        // shouldn't really happen, but if it does then we can't determine whether
        // OS version requires any special handling
        return FALSE;
    }
    char *to_free = tmp_copy;
    char major_version[100] = "\0";
    char minor_version[100] = "\0";
    // we are interested in just 2 tokens from the version string
    for (int i = 1; i <= 2; i++) {
        char *token = strsep(&tmp_copy, ".");
        if (token == nullptr) {
            break;
        }
        if (i == 1) {
            os::snprintf(major_version, sizeof(token), "%s", token);
        } else {
            os::snprintf(minor_version, sizeof(token), "%s", token);
        }
    }
    os::free(to_free);
    bool requires_special_handling = false;
    if (strlen(major_version) != 0 && strlen(minor_version) != 0) {
        vmassert(isdigit(major_version[0]), "unexpected macOS major version:%s", major_version);
        vmassert(isdigit(minor_version[0]), "unexpected macOS minor version:%s", minor_version);
        const intmax_t major_ver = strtoimax(major_version, nullptr, 10);
        const intmax_t minor_ver = strtoimax(minor_version, nullptr, 10);
        if ((major_ver == 10 && minor_ver >= 16)
            || (major_ver == 16 && minor_ver >= 0)) {
            requires_special_handling = true; // this macOS version requires special handling
        }
    }
    return requires_special_handling;
}

int macos_determine_product_version(char* product_version, size_t size) {
    const int ret = sysctlbyname("kern.osproductversion", product_version, &size, nullptr, 0);
    if (ret != 0) {
        product_version = nullptr;
        return ret;
    }
    const bool requires_special_handling = version_requires_special_handling(product_version);
    if (!requires_special_handling) {
        return 0;
    }
    // Requires special handling. We ignore the version reported by
    // sysctlbyname("kern.osproductversion") and instead read the *real* ProductVersion from
    // /System/Library/CoreServices/.SystemVersionPlatform.plist.
    // If not found there, then we fallback to /System/Library/CoreServices/SystemVersion.plist
    NSDictionary *version = [NSDictionary dictionaryWithContentsOfFile:
        @"/System/Library/CoreServices/.SystemVersionPlatform.plist"];
    if (version == nullptr) {
        // fallback to SystemVersion.plist
        version = [NSDictionary dictionaryWithContentsOfFile:
            @"/System/Library/CoreServices/SystemVersion.plist"];
    }
    if (version != nullptr) {
        const NSString * v = [version objectForKey: @"ProductVersion"];
        os::snprintf(product_version, size, "%s", [v UTF8String]);
    }
    return 0;
}

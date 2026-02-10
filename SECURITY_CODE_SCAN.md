# Security Code Scan Report (Apktool)

Date: 2026-02-10
Scope: static review of Java/Kotlin source and Gradle config in this repository.
Method: grep-based pattern scan + targeted manual inspection of sensitive files.

## Executive summary

- No obvious malware behavior was found (no command-and-control networking, no credential stealing logic, no remote shell, no hidden persistence code).
- The project does execute external binaries (aapt/aapt2) through `ProcessBuilder`, but uses argument arrays (not shell concatenation), which reduces command-injection risk.
- The code includes explicit directory traversal protections and tests for traversal cases.
- XML parsing is hardened against XXE/DTD external entity resolution.
- Main residual risk: trust boundary around user-selected external tools/paths and build-time dependency sourcing.

## What was scanned

- Process execution patterns: `Runtime.exec`, `ProcessBuilder`.
- Network patterns: sockets/http clients.
- Reflection and native loading usage.
- Deserialization-related usage.
- ZIP and path traversal related logic.
- XML parser security settings.
- Build/dependency repository configuration.

## Findings

### 1) External process execution exists (reviewed, not malicious by itself)

- `OS.exec` / `OS.execAndReturn` start external processes with `ProcessBuilder`.
- Callers include AAPT invocation during build/link operations.
- Because it passes `String[]` directly (not shell strings), classic shell injection vectors are reduced.

Risk level: **Low/Medium** (depends on trust of input paths/binaries).

Hardening recommendations:

1. Keep binary path allowlist/validation when accepting custom `aapt` path from CLI/config.
2. Consider explicit timeout and process-kill handling in `OS.exec` (currently waits indefinitely).
3. Log command origin (user-provided vs internal default) at debug level.

### 2) Directory traversal protections present

- `FileDirectory.generatePath` normalizes and blocks escapes outside base directory.
- `BrutIO.sanitizePath` rejects empty, absolute, and outside-base paths.
- Unit tests exist for valid/invalid traversal scenarios.

Risk level: **Low** (controls present and tested).

### 3) XML parser is hardened against XXE

- `XmlUtils` enables secure processing.
- Disallows `DOCTYPE` and disables external DTD loading.
- Restricts external DTD/schema access via JAXP attributes where available.

Risk level: **Low**.

### 4) Reflection usage is minimal

- Only `Class.forName("android.app.Activity")` found for environment detection.

Risk level: **Low**.

### 5) Build/dependency supply-chain surface

- Build config uses multiple repositories including GitHub Packages with credentials from env/properties.
- This is normal for private artifact hosting, but expands supply-chain trust surface.

Risk level: **Medium** (operational/security posture dependent).

Hardening recommendations:

1. Pin and verify critical dependencies where possible.
2. Restrict repository content filters to expected groups.
3. Use least-privilege tokens for package access.

## Malware-focused conclusion

From static review, this codebase does **not** exhibit typical malware intent or behavior patterns. The code appears consistent with an APK reverse-engineering/build tool.

## Limitations

- This is static analysis only (no full dynamic behavior execution due environment/repository dependency constraints).
- Not a formal third-party pentest.
- Dependency integrity validation was not fully executed end-to-end in this environment.

## Repro commands used

```bash
rg -n "Runtime\.getRuntime\(\)\.exec|new ProcessBuilder|ProcessBuilder\(" -g '*.java' -g '*.kt'
rg -n "HttpURLConnection|URL\(|OkHttp|Socket\(|ServerSocket\(|URLConnection|java\.net\.http|download" -g '*.java' -g '*.kt'
rg -n "Class\.forName|getDeclaredMethod|getDeclaredField|setAccessible\(|System\.loadLibrary|System\.load\(" -g '*.java' -g '*.kt'
rg -n "ObjectInputStream|readObject\(|Serializable|SnakeYAML|XStream|XMLDecoder" -g '*.java' -g '*.kt'
rg -n "ZipEntry|ZipInputStream|TarInputStream|JarInputStream|getNextEntry|putNextEntry" -g '*.java' -g '*.kt'
rg -n "DocumentBuilderFactory|SAXParserFactory|XMLInputFactory|setFeature\(|disallow-doctype|external-general-entities|ENTITY" -g '*.java' -g '*.kt'
```

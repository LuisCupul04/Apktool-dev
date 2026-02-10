/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.directory;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ZipRODirectory extends AbstractDirectory {
    private final ZipFile mZipFile;
    private final String mPath;

    public ZipRODirectory(String zipFileName) throws DirectoryException {
        this(zipFileName, "");
    }

    public ZipRODirectory(File zipFile) throws DirectoryException {
        this(zipFile, "");
    }

    public ZipRODirectory(ZipFile zipFile) {
        this(zipFile, "");
    }

    public ZipRODirectory(String zipFileName, String path)
            throws DirectoryException {
        this(new File(zipFileName), path);
    }

    public ZipRODirectory(File zipFile, String path) throws DirectoryException {
        try {
            mZipFile = new ZipFile(zipFile);
        } catch (IOException ex) {
            throw new DirectoryException(ex);
        }
        mPath = path;
    }

    public ZipRODirectory(ZipFile zipFile, String path) {
        mZipFile = zipFile;
        mPath = path;
    }

    @Override
    protected AbstractDirectory createDirLocal(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected InputStream getFileInputLocal(String name)
            throws DirectoryException {
        try {
            return getZipFile().getInputStream(new ZipEntry(getPath() + name));
        } catch (IOException ex) {
            throw new PathNotExist(name, ex);
        }
    }

    @Override
    protected OutputStream getFileOutputLocal(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void loadDirs() {
        loadAll();
    }

    @Override
    protected void loadFiles() {
        loadAll();
    }

    @Override
    protected void removeFileLocal(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getSize(String fileName)
            throws DirectoryException {
        ZipEntry entry = getZipFileEntry(fileName);
        return entry.getSize();
    }

    @Override
    public long getCompressedSize(String fileName)
            throws DirectoryException {
        ZipEntry entry = getZipFileEntry(fileName);
        return entry.getCompressedSize();
    }

    @Override
    public int getCompressionLevel(String fileName)
            throws DirectoryException {
        ZipEntry entry = getZipFileEntry(fileName);
        return entry.getMethod();
    }

    private ZipEntry getZipFileEntry(String fileName)
            throws DirectoryException {
        ZipEntry entry = mZipFile.getEntry(fileName);
        if (entry == null) {
            throw new PathNotExist("Entry not found: " + fileName);
        }
        return entry;
    }

    private void loadAll() {
        mFiles = new LinkedHashSet<>();
        mDirs = new LinkedHashMap<>();

        String currentPath = getPath();
        String normalizedCurrentPath = currentPath.replace('\\', '/');
        if (!normalizedCurrentPath.isEmpty() && !normalizedCurrentPath.endsWith("/")) {
            normalizedCurrentPath = normalizedCurrentPath + "/";
        }

        Enumeration<? extends ZipEntry> entries = getZipFile().entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();

            // Normalize entry name to use forward slashes first
            String normalizedName = name.replace('\\', '/');

            // Use Path normalization to defend against traversal attempts
            Path entryPath;
            try {
                entryPath = Paths.get(normalizedName).normalize();
            } catch (Exception ignored) {
                // Malformed path, skip it
                continue;
            }

            // Reject absolute paths
            if (entryPath.isAbsolute()) {
                continue;
            }

            // Reject any path that contains a parent-directory ("..") segment
            boolean hasParentSegment = false;
            for (Path segment : entryPath) {
                String seg = segment.toString();
                if (seg.equals("..") || seg.isEmpty()) {
                    hasParentSegment = true;
                    break;
                }
            }
            if (hasParentSegment) {
                continue;
            }

            // Work with the normalized string representation from here on
            normalizedName = entryPath.toString().replace('\\', '/');

            // Ensure the entry is within the current logical directory
            if (!normalizedCurrentPath.isEmpty() && !normalizedName.startsWith(normalizedCurrentPath)) {
                continue;
            }

            String relativePath = normalizedCurrentPath.isEmpty()
                    ? normalizedName
                    : normalizedName.substring(normalizedCurrentPath.length());

            if (relativePath.isEmpty()) {
                // No path below this directory
                continue;
            }

            // Additional normalization-based check against traversal on the relative path
            Path relPath;
            try {
                relPath = Paths.get(relativePath).normalize();
            } catch (Exception ignored) {
                // Malformed path, skip it
                continue;
            }
            if (relPath.isAbsolute()) {
            // Reject any parent-directory segments in the relative path
            boolean relHasParentSegment = false;
            for (Path segment : relPath) {
                String seg = segment.toString();
                if (seg.equals("..") || seg.isEmpty()) {
                    relHasParentSegment = true;
                    break;
                }
            }
            if (relHasParentSegment) {
                continue;
            }
                // Escapes above the logical root, skip this entry
                continue;
            }

            // Use only the first path segment as the immediate child name
            String subname = relPath.toString().replace('\\', '/');
            int pos = subname.indexOf(separator);
            if (pos == -1) {
                if (!entry.isDirectory()) {
                    // subname must be a simple file name without separators or traversal
                    if (!subname.isEmpty() && !subname.equals("..") && subname.indexOf('/') == -1 && subname.indexOf('\\') == -1) {
                        mFiles.add(subname);
                    }
                    continue;
                }
            } else {
            // Final sanity check on directory subname
            if (subname.isEmpty() || subname.equals("..") || subname.indexOf('/') != -1 || subname.indexOf('\\') != -1) {
                continue;
            }

                subname = subname.substring(0, pos);
            }

            if (!mDirs.containsKey(subname)) {
                AbstractDirectory dir = new ZipRODirectory(getZipFile(), getPath() + subname + separator);
                mDirs.put(subname, dir);
            }
        }
    }

    private String getPath() {
        return mPath;
    }

    private ZipFile getZipFile() {
        return mZipFile;
    }

    public void close() throws IOException {
        mZipFile.close();
    }
}

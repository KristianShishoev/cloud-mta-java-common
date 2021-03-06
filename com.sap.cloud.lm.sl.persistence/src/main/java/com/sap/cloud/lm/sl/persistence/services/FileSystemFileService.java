package com.sap.cloud.lm.sl.persistence.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloud.lm.sl.persistence.DataSourceWithDialect;
import com.sap.cloud.lm.sl.persistence.message.Messages;
import com.sap.cloud.lm.sl.persistence.model.FileEntry;
import com.sap.cloud.lm.sl.persistence.processors.FileDownloadProcessor;

public class FileSystemFileService extends AbstractFileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemFileService.class);

    private static final String DEFAULT_FILES_STORAGE_PATH = "files";

    private String storagePath;

    public FileSystemFileService(DataSourceWithDialect dataSourceWithDialect) {
        this(dataSourceWithDialect, DEFAULT_FILES_STORAGE_PATH);
    }

    public FileSystemFileService(DataSourceWithDialect dataSourceWithDialect, String storagePath) {
        super(DEFAULT_TABLE_NAME, dataSourceWithDialect);
        this.storagePath = storagePath;
    }

    @Override
    protected boolean storeFile(FileEntry fileEntry, InputStream inputStream) throws FileStorageException {
        boolean contentStoredSuccessfully = storeFileContent(fileEntry, inputStream);
        if (!contentStoredSuccessfully) {
            return false;
        }
        return storeFileAttributes(fileEntry);
    }

    private boolean storeFileContent(FileEntry fileEntry, InputStream inputStream) throws FileStorageException {
        try {
            Path filesDirectory = getFilesDirectory(fileEntry.getSpace());
            Path newFilePath = Paths.get(filesDirectory.toString(), fileEntry.getId());
            Files.copy(inputStream, newFilePath, StandardCopyOption.REPLACE_EXISTING);
            return Files.exists(newFilePath);
        } catch (IOException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    @Override
    protected void deleteFileContent(String space, String id) throws FileStorageException {
        try {
            Path filesDirectory = getFilesDirectory(space);
            Path filePath = Paths.get(filesDirectory.toString(), id);
            LOGGER.info(MessageFormat.format(Messages.DELETING_FILE_WITH_PATH, filePath.toString()));
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new FileStorageException(MessageFormat.format(Messages.ERROR_DELETING_FILE_WITH_ID, id), e);
        }
    }

    @Override
    public void processFileContent(FileDownloadProcessor fileDownloadProcessor) throws FileStorageException {
        FileEntry fileEntry = fileDownloadProcessor.getFileEntry();
        if (removeOrphanedFileAttributes(fileEntry)) {
            throw new FileStorageException(
                MessageFormat.format(Messages.FILE_WITH_ID_DOES_NOT_EXIST, fileEntry.getId(), fileEntry.getSpace()));
        }
        InputStream fileContentStream = null;
        try {
            Path filePathLocation = getFilePath(fileDownloadProcessor.getFileEntry());
            fileContentStream = Files.newInputStream(filePathLocation);
            fileDownloadProcessor.processContent(fileContentStream);
        } catch (Exception e) {
            throw new FileStorageException(e);
        } finally {
            if (fileContentStream != null) {
                IOUtils.closeQuietly(fileContentStream);
            }
        }
    }

    private Path getFilePath(FileEntry entry) throws IOException {
        Path filesDirectory = getFilesDirectory(entry.getSpace());
        return Paths.get(filesDirectory.toString(), entry.getId());
    }

    @Override
    public int deleteAll(String space, String namespace) throws FileStorageException {
        throw new UnsupportedOperationException();
    }

    private Path getFilesDirectory(String space) throws IOException {
        Path filesPerSpaceDirectory = getFilesPerSpaceDirectory(space);
        if (!Files.exists(filesPerSpaceDirectory)) {
            Files.createDirectories(filesPerSpaceDirectory);
        }
        return filesPerSpaceDirectory;
    }

    @Override
    public int deleteAllByFileIds(Map<String, List<String>> spaceToFileIds) throws FileStorageException {
        int deletedFileAttributes = super.deleteAllByFileIds(spaceToFileIds);
        LOGGER.debug(MessageFormat.format(Messages.DELETED_FILE_ATTRIBUTES_COUNT, deletedFileAttributes));
        int deletedFiles = 0;
        for (String space : spaceToFileIds.keySet()) {
            for (String fileId : spaceToFileIds.get(space)) {
                deleteFileContent(space, fileId);
                deletedFiles++;
            }
        }
        return deletedFiles;
    }

    private Path getFilesPerSpaceDirectory(String space) {
        Path filesPerSpaceDirectory = Paths.get(storagePath, space, DEFAULT_FILES_STORAGE_PATH);
        return filesPerSpaceDirectory;
    }

    @Override
    public List<FileEntry> listFiles(String space, String namespace) throws FileStorageException {
        List<FileEntry> allEntries = super.listFiles(space, namespace);
        return removeOrhpanedFilesAttributes(allEntries);
    }

    @Override
    public List<FileEntry> listByModificationTime(final Date modificationTime) throws FileStorageException {
        List<FileEntry> fileEntriesAttributes = super.listByModificationTime(modificationTime);
        removeOrhpanedFilesAttributes(fileEntriesAttributes);

        final List<FileEntry> oldEntries = new ArrayList<FileEntry>();
        final FileTime modificationTimeUpperBound = FileTime.fromMillis(modificationTime.getTime());
        try {
            Files.walkFileTree(Paths.get(storagePath), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.lastModifiedTime().compareTo(modificationTimeUpperBound) < 0) {
                        oldEntries.add(getFileEntry(file, attrs));
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
        return oldEntries;
    }

    @Override
    public FileEntry getFile(String space, String id) throws FileStorageException {
        FileEntry entry = super.getFile(space, id);
        return removeOrphanedFileAttributes(entry) ? null : entry;
    }

    private List<FileEntry> removeOrhpanedFilesAttributes(List<FileEntry> entries) throws FileStorageException {
        List<FileEntry> entriesWithContent = new ArrayList<>();
        for (FileEntry entry : entries) {
            if (!removeOrphanedFileAttributes(entry)) {
                entriesWithContent.add(entry);
            }
        }
        return entriesWithContent;
    }

    private boolean removeOrphanedFileAttributes(FileEntry entry) throws FileStorageException {
        boolean shouldDelete = !hasContent(entry);
        if (shouldDelete) {
            deleteFileAttributes(entry.getSpace(), entry.getId());
        }
        return shouldDelete;
    }

    private boolean hasContent(FileEntry entry) throws FileStorageException {
        try {
            Path filePath = getFilePath(entry);
            return Files.exists(filePath);
        } catch (IOException e) {
            throw new FileStorageException(e.getMessage(), e);
        }
    }

    private FileEntry getFileEntry(Path file, BasicFileAttributes attrs) {
        FileEntry fileEntry = new FileEntry();
        fileEntry.setId(file.getFileName().toString());
        fileEntry.setSpace(file.getParent().toString());
        fileEntry.setModified(new Date(attrs.lastModifiedTime().toMillis()));
        return fileEntry;
    }

}

package com.netflix.gdrive.manager.service.google.drive;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.netflix.gdrive.manager.model.google.drive.NetflixFileEntity;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * DriveService is a service proxy to all calls to Drive API.
 */
@Component
public class DriveService {

    private static final int MAX_LIST_PAGE_SIZE = 100;
    private static final int MAX_PERMISSION_UPDATE_BATCH_SIZE = 20;
    private static final int MAX_ITEMS_SUPPORTED = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveService.class);

    @SneakyThrows
    public static Drive getDriveInstance(final Credential oauth2Credential) {
        final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        return new Drive.Builder(httpTransport, jsonFactory, oauth2Credential)
            .setApplicationName("Google Drive Manager")
            .build();
    }

    /**
     * Gets all the files and folders in the drive.
     * Maximum supported size in a drive: 1000
     *
     * @param drive driveInstance
     * @return list of all files and folders in the drive.
     */
    @SneakyThrows
    public List<NetflixFileEntity> getAllFiles(final Drive drive) {
        final List<File> files = new ArrayList<>();
        String nextPageToken = null;
        do {
            final FileList filePage = drive.files()
                                           .list()
                                           .setPageSize(MAX_LIST_PAGE_SIZE)
                                           .setPageToken(nextPageToken)
                                           .setFields("nextPageToken, files(id, name, parents, owners, capabilities/canAddChildren)")
                                           .execute();

            files.addAll(filePage.getFiles());
            nextPageToken = filePage.getNextPageToken();
        } while (nextPageToken != null && files.size() <= MAX_ITEMS_SUPPORTED);

        if (files.size() >= MAX_ITEMS_SUPPORTED) {
            throw new IllegalStateException("Drive is not supported for more than 1000 files/folders");
        }

        return files
            .stream()
            .map(googleFileEntity -> NetflixFileEntity.builder()
                                       .id(googleFileEntity.getId())
                                       .parentId(getParent(googleFileEntity))
                                       .name(googleFileEntity.getName())
                                       .canTransfer(googleFileEntity.getCapabilities().getCanAddChildren())
                                       .owner(getOwner(googleFileEntity))
                                       .ownedByMe(ownedByMe(googleFileEntity))
                                                      .build())

            .collect(Collectors.toList());
    }

    /**
     * Transfers the ownership of all files passed in to the new owner
     * in a batch request to Drive API.
     *
     * @param drive driveInstance
     * @param fileEntities fileEntities
     * @param newOwnerEmail newOwnerEmail
     */
    @SneakyThrows
    public void transferOwnership(final Drive drive,
                                  final List<NetflixFileEntity> fileEntities,
                                  final String newOwnerEmail) {

        final Permission newPermission = new Permission().setType("user")
                                                         .setRole("owner")
                                                         .setEmailAddress(newOwnerEmail);

        final List<NetflixFileEntity> canTransferFiles = fileEntities.stream()
                                                                     .filter(NetflixFileEntity::getCanTransfer)
                                                                     .collect(Collectors.toList());

        JsonBatchCallback<Permission> callback =
            new JsonBatchCallback<>() {
            @Override
            public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
                LOGGER.error("Could not transfer ownership " + e);
            }

            @Override
            public void onSuccess(Permission permission, HttpHeaders responseHeaders) {
                LOGGER.debug("Transferred successfully for " + permission.getId());
            }
        };

        // Process permission updates in batches to remain under the quota.
        for (int batchStart = 0; batchStart < canTransferFiles.size(); ) {
            BatchRequest batch = drive.batch();
            for (int index = batchStart; index < canTransferFiles.size(); index++) {
                drive.permissions()
                     .create(canTransferFiles.get(index).getId(), newPermission)
                     .setFields("id")
                     .setTransferOwnership(true)
                     .queue(batch, callback);
            }
            batch.execute();
            batchStart = batchStart + MAX_PERMISSION_UPDATE_BATCH_SIZE;
        }
    }

    private String getParent(final File file) {
        return !isEmpty(file.getParents()) ? file.getParents().get(0) : null;
    }

    private static boolean ownedByMe(final File file) {
        return !isEmpty(file.getOwners()) ? file.getOwners().get(0).getMe() : false;
    }

    private static String getOwner(final File file) {
        return !isEmpty(file.getOwners()) ? file.getOwners().get(0).getEmailAddress() : null;
    }
}

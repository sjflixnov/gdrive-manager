package com.netflix.gdrive.manager.service.google.drive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.netflix.gdrive.manager.model.google.drive.NetflixFileEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * FileManager is an in-memory helper module to arrange files in a hierarchy or mapped
 * by various attributes.
 */
@Component
public class FileManager {

    private static NetflixFileEntity MY_DRIVE_ROOT = NetflixFileEntity.builder().id("myDriveRoot").build();

    /**
     * Gets the FileEntity associated with fileId or null.
     *
     * @param fileEntities pool of files
     * @param fileId fileId
     * @return fileEntity
     */
    public NetflixFileEntity getFileEntity(final List<NetflixFileEntity> fileEntities,
                                           final String fileId) {
        return fileEntities.stream()
                           .filter(fe -> fe.getId().equals(fileId))
            .parallel()
            .findFirst().orElse(null);
    }

    /**
     * Gets all descendants recursively from a provided parent node.
     *
     * @param fileEntities pool of all fileEntities
     * @param parent starting parent node
     * @return all descendants of the parent.
     */
    public List<NetflixFileEntity> getDescendants(final List<NetflixFileEntity> fileEntities,
                                                  final NetflixFileEntity parent) {
        if (CollectionUtils.isEmpty(fileEntities)) {
            return new ArrayList<>();
        }
        List<NetflixFileEntity> childrens = fileEntities.stream()
                           .filter(fe -> fe.getParentId() != null)
                           .filter(fe -> fe.getParentId().equals(parent.getId()))
            .collect(Collectors.toList());

        final List<NetflixFileEntity> descendants = new ArrayList<>();
        for (NetflixFileEntity c : childrens) {
            descendants.addAll(getDescendants(fileEntities, c));
        }
        childrens.addAll(descendants);

        return childrens;
    }

    /**
     * Arranges all the fileEntities in a tree view hierarchically for assisting
     * in the representation on UI side.
     *
     * @param fileEntities pool of fileEntities
     * @return fileEntities arranged in a treeView or DFS serialization
     */
    public List<NetflixFileEntity> fetchFileTreeView(final List<NetflixFileEntity> fileEntities) {

        final Map<String, NetflixFileEntity> filesByFileId = getFilesMappedByFileId(fileEntities);
        final Map<NetflixFileEntity, List<NetflixFileEntity>> filesByParentIds =
            getFilesMappedByParentId(fileEntities, filesByFileId);

        List<NetflixFileEntity> fileTree = new ArrayList<>();
        for (NetflixFileEntity file : filesByParentIds.get(MY_DRIVE_ROOT)) {
            addChildren(fileTree, file, filesByParentIds);
        }
        return fileTree;
    }

    private Map<NetflixFileEntity, List<NetflixFileEntity>>
        getFilesMappedByParentId(final List<NetflixFileEntity> fileEntities,
                                 final Map<String, NetflixFileEntity> filesByFileId) {

        final Map<NetflixFileEntity, List<NetflixFileEntity>> filesByParents = new HashMap<>();
        filesByParents.put(MY_DRIVE_ROOT, new ArrayList<>());
        for (NetflixFileEntity file : fileEntities) {
            if (!filesByFileId.containsKey(file.getParentId())) {
                filesByParents.get(MY_DRIVE_ROOT).add(filesByFileId.get(file.getId()));
                continue;
            }
            if (!filesByParents.containsKey(filesByFileId.get(file.getParentId()))) {
                filesByParents.put(filesByFileId.get(file.getParentId()), new ArrayList<>());
            }
            filesByParents.get(filesByFileId.get(file.getParentId())).add(file);
        }
        return filesByParents;
    }

    private Map<String, NetflixFileEntity> getFilesMappedByFileId(final List<NetflixFileEntity> fileEntities) {
        final Map<String, NetflixFileEntity> filesByFileId = new HashMap<>();
        for (NetflixFileEntity file : fileEntities) {
            filesByFileId.put(file.getId(), file);
        }
        return filesByFileId;
    }

    private static void addChildren(List<NetflixFileEntity> files,
                                    NetflixFileEntity parentFile,
                                    Map<NetflixFileEntity, List<NetflixFileEntity>> filesByParents) {
        files.add(parentFile);
        if (filesByParents.get(parentFile) != null && !filesByParents.get(parentFile).isEmpty()) {
            for (NetflixFileEntity file : filesByParents.get(parentFile)) {
                addChildren(files, file, filesByParents);
            }
        }
    }
}

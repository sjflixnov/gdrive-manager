package com.netflix.gdrive.manager.model.google.drive;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * NetflixFileEntity is an internal representation for attributes of interest
 * from Google Drive File model.
 */
@ToString
@Getter
@Builder
public class NetflixFileEntity {

    private String id;
    private String name;
    private String parentId;
    private String owner;
    private Boolean ownedByMe;
    private Boolean canTransfer;
}
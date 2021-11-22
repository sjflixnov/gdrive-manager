package com.netflix.gdrive.manager.controller;

import java.util.List;

import com.google.api.services.drive.Drive;
import com.google.common.collect.Lists;
import com.netflix.gdrive.manager.model.google.drive.NetflixFileEntity;
import com.netflix.gdrive.manager.service.google.drive.DriveService;
import com.netflix.gdrive.manager.service.google.drive.FileManager;
import com.netflix.gdrive.manager.service.google.oauth2.Oauth2CrendentialsService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.netflix.gdrive.manager.service.google.drive.DriveService.getDriveInstance;

/**
 * Controller to manage file/folder operations like fetching all Files in Drive and
 * changing ownership of all folders within a hierarchy.
 */
@Controller
public class FilesController {

    @Autowired
    private Oauth2CrendentialsService oauth2CrendentialsService;

    @Autowired
    private DriveService driveService;

    @Autowired
    private FileManager fileManager;

    /**
     * Default view is to return all files in the drive.
     *
     * @param model model binding to add attributes
     * @param redirectAttributes attributes to pass over during a redirect to error page.
     * @param token authenticated token
     * @return viewName
     */
    @GetMapping({ "/", "/files" })
    public String files(Model model, RedirectAttributes redirectAttributes, OAuth2AuthenticationToken token) {
        try {
            model.addAttribute("userName", token.getPrincipal().getAttribute("name"));
            model.addAttribute("files",
                               fileManager.fetchFileTreeView(
                                   driveService.getAllFiles(
                                       getDriveInstance(oauth2CrendentialsService.getCredential(token)))));
            return "files";
        } catch (final Exception error) {
            redirectAttributes.addFlashAttribute("errMessage", error.getMessage());
            return "redirect:/";
        }
    }

    /**
     * Changes the ownership of a folder and all folders beneath it.
     *
     * @param model model binding to add attributes
     * @param fileId fileId of the folder
     * @param newOwnerEmail email of the new owner
     * @param token authenticated token
     * @param redirectAttrs attributes to pass over during a redirect to error page.
     * @return viewName
     */
    @PostMapping("/files/{id}/owner")
    public String updateOwner(final Model model,
                              @PathVariable("id") String fileId,
                              @RequestParam("email") String newOwnerEmail,
                              OAuth2AuthenticationToken token,
                              RedirectAttributes redirectAttrs) {
        model.addAttribute("userName", token.getPrincipal().getAttribute("name"));

        final Drive drive = getDriveInstance(oauth2CrendentialsService.getCredential(token));
        final List<NetflixFileEntity> allFiles = driveService.getAllFiles(drive);
        final NetflixFileEntity netflixFileEntity = fileManager.getFileEntity(allFiles, fileId);
        List<NetflixFileEntity> rootAndChildren = Lists.newArrayList(netflixFileEntity);
        rootAndChildren.addAll(fileManager.getDescendants(allFiles, netflixFileEntity));

        try {
            driveService.transferOwnership(drive, rootAndChildren, newOwnerEmail);
            return "redirect:/";
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errMessage", "could not transfer owner for some of items");
            return "redirect:/";
        }
    }
}

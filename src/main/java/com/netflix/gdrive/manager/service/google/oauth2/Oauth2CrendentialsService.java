package com.netflix.gdrive.manager.service.google.oauth2;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class Oauth2CrendentialsService {

    @Autowired
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    /**
     * Maps the Oauth2 token to the credential to pass to Google APIs.
     *
     * @param token local oauth2Token
     * @return Google Credential with access token loaded.
     */
    @SneakyThrows
    public Credential getCredential(final OAuth2AuthenticationToken token) {
        final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        return new GoogleCredential.Builder()
            .setTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .build()
            .setAccessToken(
                oAuth2AuthorizedClientService.loadAuthorizedClient(
                    token.getAuthorizedClientRegistrationId(),
                    token.getPrincipal().getName()).getAccessToken().getTokenValue());
    }
}

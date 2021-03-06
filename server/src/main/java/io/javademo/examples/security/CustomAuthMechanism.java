package io.javademo.examples.security;

import io.javademo.common.web.filter.CorsFilter;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.security.AuthenticationStatus;
import javax.security.auth.message.AuthException;
import javax.security.authentication.mechanism.http.HttpAuthenticationMechanism;
import javax.security.authentication.mechanism.http.HttpMessageContext;
import javax.security.identitystore.CredentialValidationResult;
import javax.security.identitystore.credential.Credential;
import javax.security.identitystore.credential.UsernamePasswordCredential;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.xml.bind.DatatypeConverter.parseBase64Binary;
import static org.glassfish.soteria.Utils.isEmpty;

/**
 * Created by marcomolteni on 04.06.17.
 */
@RequestScoped
public class CustomAuthMechanism implements HttpAuthenticationMechanism {

    private static final Logger LOGGER = Logger.getLogger(CustomAuthMechanism.class.getName());
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic ";

    @Inject
    CustomIdentityStore customIdentityStore;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response, HttpMessageContext httpMessageContext) throws AuthException {

        LOGGER.log(Level.INFO, "validateRequest: {0}", request.getRequestURI());

        Credential credential = null;

        MultivaluedMap<String, String> headers = CorsFilter.getHeaders();
        for (String key : headers.keySet()) {
            response.addHeader(key, headers.getFirst(key));
        }
        response.addHeader("Access-Control-Allow-Headers", "authorization,content-type, X-Requested-With");

        // the browser send a pre-flight request with OPTION in place of GET
        if (request.getMethod().equals("OPTIONS")) {
            httpMessageContext.getResponse().setStatus(200);

            return httpMessageContext.doNothing();
        }

        // we extract the credentials (username /  password) from the header of the request
        String[] credentials = extractCredentials(request);

        if (credentials != null && credentials.length == 2) {
            LOGGER.log(Level.INFO, "credentials : {0}, {1}", credentials);

            credential = new UsernamePasswordCredential(credentials[0], credentials[1]);

        } else {
            // this is a wrapper to a JASPIC method
            if (httpMessageContext.isProtected()) {
                // if there are no credentials and the resource is protected
                // we answer with a 401 code
                response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
                return AuthenticationStatus.SEND_FAILURE;
            } else {
                // there are no credentials but the resource is not protected
                // we don't do anything
                return httpMessageContext.doNothing();
            }
        }

        // validation of the credential using the identity store
        CredentialValidationResult result = customIdentityStore.validate(credential);

        if (result.getStatus() == CredentialValidationResult.Status.VALID) {
            LOGGER.log(Level.INFO, "user authenticated : {0}, {1}", new String[]{result.getCallerPrincipal().getName(), result.getCallerGroups().toString()});

            // we tell to the container that the user is valid and we return SUCCESS
            return httpMessageContext.notifyContainerAboutLogin(
                    result.getCallerPrincipal(), result.getCallerGroups());
        } else {

            // the authentication failed, we return the code 401 in the http response
            response.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
            return AuthenticationStatus.SEND_FAILURE;
        }
    }

    private String[] extractCredentials(HttpServletRequest request) {

        String authorizationHeader = request.getHeader(AUTHORIZATION);
        if (!isEmpty(authorizationHeader) && authorizationHeader.startsWith(BASIC)) {
            return new String(parseBase64Binary(authorizationHeader.substring(6))).split(":");
        }

        return null;
    }
}

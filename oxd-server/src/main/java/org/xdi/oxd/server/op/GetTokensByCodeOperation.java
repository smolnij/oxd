package org.xdi.oxd.server.op;

import com.google.common.base.Strings;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xdi.oxauth.client.ClientUtils;
import org.xdi.oxauth.client.TokenClient;
import org.xdi.oxauth.client.TokenRequest;
import org.xdi.oxauth.client.TokenResponse;
import org.xdi.oxauth.model.common.AuthenticationMethod;
import org.xdi.oxauth.model.common.GrantType;
import org.xdi.oxauth.model.jwt.Jwt;
import org.xdi.oxauth.model.jwt.JwtClaimName;
import org.xdi.oxd.common.Command;
import org.xdi.oxd.common.CommandResponse;
import org.xdi.oxd.common.ErrorResponseCode;
import org.xdi.oxd.common.ErrorResponseException;
import org.xdi.oxd.common.params.GetTokensByCodeParams;
import org.xdi.oxd.common.response.GetTokensByCodeResponse;
import org.xdi.oxd.server.service.SiteConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 22/09/2015
 */

public class GetTokensByCodeOperation extends BaseOperation<GetTokensByCodeParams> {

    private static final Logger LOG = LoggerFactory.getLogger(GetTokensByCodeOperation.class);

    /**
     * Base constructor
     *
     * @param command command
     */
    protected GetTokensByCodeOperation(Command command, final Injector injector) {
        super(command, injector, GetTokensByCodeParams.class);
    }

    @Override
    public CommandResponse execute(GetTokensByCodeParams params) throws Exception {
        validate(params);

        final SiteConfiguration site = getSite();

        final TokenRequest tokenRequest = new TokenRequest(GrantType.AUTHORIZATION_CODE);
        tokenRequest.setCode(params.getCode());
        tokenRequest.setRedirectUri(site.getAuthorizationRedirectUri());
        tokenRequest.setAuthUsername(site.getClientId());
        tokenRequest.setAuthPassword(site.getClientSecret());
        tokenRequest.setAuthenticationMethod(AuthenticationMethod.CLIENT_SECRET_BASIC);

        final TokenClient tokenClient = new TokenClient(getDiscoveryService().getConnectDiscoveryResponse(site.getOpHost()).getTokenEndpoint());
        tokenClient.setExecutor(getHttpService().getClientExecutor());
        tokenClient.setRequest(tokenRequest);
        final TokenResponse response = tokenClient.exec();
        ClientUtils.showClient(tokenClient);

        if (response.getStatus() == 200 || response.getStatus() == 302) { // success or redirect

            if (Strings.isNullOrEmpty(response.getIdToken())) {
                LOG.error("id_token is not returned. Please check whether 'openid' scope is present for 'get_authorization_url' command");
                throw new ErrorResponseException(ErrorResponseCode.NO_ID_TOKEN_RETURNED);
            }

            if (Strings.isNullOrEmpty(response.getAccessToken())) {
                LOG.error("access_token is not returned");
                throw new ErrorResponseException(ErrorResponseCode.NO_ACCESS_TOKEN_RETURNED);
            }


            final GetTokensByCodeResponse opResponse = new GetTokensByCodeResponse();
            opResponse.setAccessToken(response.getAccessToken());
            opResponse.setIdToken(response.getIdToken());
            opResponse.setRefreshToken(response.getRefreshToken());
            opResponse.setExpiresIn(response.getExpiresIn());

            final Jwt jwt = Jwt.parse(response.getIdToken());
            final String nonceFromToken = jwt.getClaims().getClaimAsString(JwtClaimName.NONCE);
            if (!getStateService().isNonceValid(nonceFromToken)) {
                throw new ErrorResponseException(ErrorResponseCode.INVALID_NONCE);
            }

            if (CheckIdTokenOperation.isValid(jwt, getDiscoveryService().getConnectDiscoveryResponse(site.getOpHost()), nonceFromToken, site.getClientId())) {
                final Map<String, List<String>> claims = jwt.getClaims() != null ? jwt.getClaims().toMap() : new HashMap<String, List<String>>();
                opResponse.setIdTokenClaims(claims);

                // persist tokens
                site.setIdToken(response.getIdToken());
                site.setAccessToken(response.getAccessToken());
                getSiteService().update(site);
                getStateService().invalidateState(params.getState());

                return okResponse(opResponse);
            } else {
                LOG.error("ID Token is not valid, token: " + response.getIdToken());
            }
        } else {
            LOG.error("Failed to get tokens because response code is: " + response.getScope());
        }
        return null;
    }

    private void validate(GetTokensByCodeParams params) {
        if (Strings.isNullOrEmpty(params.getCode())) {
            throw new ErrorResponseException(ErrorResponseCode.BAD_REQUEST_NO_CODE);
        }
        if (Strings.isNullOrEmpty(params.getState())) {
            throw new ErrorResponseException(ErrorResponseCode.BAD_REQUEST_NO_STATE);
        }
        if (!getStateService().isStateValid(params.getState())) {
            throw new ErrorResponseException(ErrorResponseCode.BAD_REQUEST_STATE_NOT_VALID);
        }
    }
}

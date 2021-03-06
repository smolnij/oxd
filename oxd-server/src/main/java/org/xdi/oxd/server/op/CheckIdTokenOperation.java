/**
 * All rights reserved -- Copyright 2015 Gluu Inc.
 */
package org.xdi.oxd.server.op;

import com.google.inject.Injector;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xdi.oxauth.client.JwkClient;
import org.xdi.oxauth.client.JwkResponse;
import org.xdi.oxauth.client.OpenIdConfigurationResponse;
import org.xdi.oxauth.model.crypto.PublicKey;
import org.xdi.oxauth.model.crypto.signature.RSAPublicKey;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithm;
import org.xdi.oxauth.model.jws.RSASigner;
import org.xdi.oxauth.model.jwt.Jwt;
import org.xdi.oxauth.model.jwt.JwtClaimName;
import org.xdi.oxauth.model.jwt.JwtHeaderName;
import org.xdi.oxd.common.Command;
import org.xdi.oxd.common.CommandResponse;
import org.xdi.oxd.common.CoreUtils;
import org.xdi.oxd.common.params.CheckIdTokenParams;
import org.xdi.oxd.common.response.CheckIdTokenResponse;
import org.xdi.oxd.server.service.SiteConfiguration;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Date;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 18/10/2013
 */

public class CheckIdTokenOperation extends BaseOperation<CheckIdTokenParams> {

    private static final Logger LOG = LoggerFactory.getLogger(CheckIdTokenOperation.class);

    protected CheckIdTokenOperation(Command command, final Injector injector) {
        super(command, injector, CheckIdTokenParams.class);
    }

    @Override
    public CommandResponse execute(CheckIdTokenParams params) throws Exception {

        OpenIdConfigurationResponse discoveryResponse = getDiscoveryService().getConnectDiscoveryResponseByOxdId(params.getOxdId());

        final SiteConfiguration site = getSite();
        final String idToken = params.getIdToken();
        final Jwt jwt = Jwt.parse(idToken);

        final Date issuedAt = jwt.getClaims().getClaimAsDate(JwtClaimName.ISSUED_AT);
        final Date expiresAt = jwt.getClaims().getClaimAsDate(JwtClaimName.EXPIRATION_TIME);

        final CheckIdTokenResponse opResponse = new CheckIdTokenResponse();
        opResponse.setActive(isValid(jwt, discoveryResponse, params.getNonce(), site.getClientId()));
        opResponse.setIssuedAt(issuedAt != null ? issuedAt.getTime() / 1000 : 0);
        opResponse.setExpiresAt(expiresAt != null ? expiresAt.getTime() / 1000 : 0);
        opResponse.setClaims(jwt.getClaims().toMap());
        return okResponse(opResponse);
    }

    public static boolean isValid(Jwt jwt, OpenIdConfigurationResponse discoveryResponse, String nonce, String clientId) {
        try {
            //                final String type = jwt.getHeader().getClaimAsString(JwtHeaderName.TYPE);
            final String algorithm = jwt.getHeader().getClaimAsString(JwtHeaderName.ALGORITHM);
            final String jwkUrl = discoveryResponse.getJwksUri();
            final String kid = jwt.getHeader().getClaimAsString(JwtHeaderName.KEY_ID);

            final String issuer = jwt.getClaims().getClaimAsString(JwtClaimName.ISSUER);
            final String nonceFromToken = jwt.getClaims().getClaimAsString(JwtClaimName.NONCE);
            final String audienceFromToken = jwt.getClaims().getClaimAsString(JwtClaimName.AUDIENCE);


            if (!nonceFromToken.endsWith(nonce)) {
                LOG.error("ID Token has invalid nonce. Expected nonce: " + nonce + ", nonce from token is: " + nonceFromToken);
                return false;
            }

            if (!clientId.equalsIgnoreCase(audienceFromToken)) {
                LOG.error("ID Token has invalid audience. Expected audience: " + clientId + ", audience from token is: " + audienceFromToken);
                return false;
            }

            final Date expiresAt = jwt.getClaims().getClaimAsDate(JwtClaimName.EXPIRATION_TIME);
            final Date now = new Date();
            if (now.after(expiresAt)) {
                LOG.error("ID Token is expired. (It is after " + now + ").");
                return false;
            }

            // 1. validate issuer
            if (!issuer.equals(discoveryResponse.getIssuer())) {
                LOG.error("ID Token issuer is invalid. Token issuer: " + issuer + ", discovery issuer: " + discoveryResponse.getIssuer());
                return false;
            }

            // 2. validate signature
            final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.fromString(algorithm);

            final RSAPublicKey publicKey = getRSAPublicKey(jwkUrl, kid);
            final RSASigner rsaSigner = new RSASigner(signatureAlgorithm, publicKey);
            final boolean signature = rsaSigner.validate(jwt);
            if (!signature) {
                LOG.error("ID Token signature is invalid.");
            }
            return signature;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return false;
        }
    }

    public static RSAPublicKey getRSAPublicKey(String jwkSetUri, String keyId) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        RSAPublicKey publicKey = null;

        JwkClient jwkClient = new JwkClient(jwkSetUri);
        jwkClient.setExecutor(new ApacheHttpClient4Executor(CoreUtils.createHttpClientTrustAll()));
        JwkResponse jwkResponse = jwkClient.exec();
        if (jwkResponse != null && jwkResponse.getStatus() == 200) {
            PublicKey pk = jwkResponse.getPublicKey(keyId);
            if (pk instanceof RSAPublicKey) {
                publicKey = (RSAPublicKey) pk;
            }
        }

        return publicKey;
    }
}

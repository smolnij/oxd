/**
 * All rights reserved -- Copyright 2015 Gluu Inc.
 */
package org.xdi.oxd.server.op;

import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xdi.oxd.common.Command;
import org.xdi.oxd.common.params.IParams;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 09/08/2013
 */

public class OperationFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OperationFactory.class);

    private OperationFactory() {
    }

    public static IOperation<? extends IParams> create(Command command, final Injector injector) {
        if (command != null && command.getCommandType() != null) {
            switch (command.getCommandType()) {
                case AUTHORIZATION_CODE_FLOW:
                    return new AuthorizationCodeFlowOperation(command, injector);
                case CHECK_ID_TOKEN:
                    return new CheckIdTokenOperation(command, injector);
                case CHECK_ACCESS_TOKEN:
                    return new CheckAccessTokenOperation(command, injector);
                case LICENSE_STATUS:
                    return new LicenseStatusOperation(command, injector);
                case GET_AUTHORIZATION_URL:
                    return new GetAuthorizationUrlOperation(command, injector);
                case GET_TOKENS_BY_CODE:
                    return new GetTokensByCodeOperation(command, injector);
                case GET_USER_INFO:
                    return new GetUserInfoOperation(command, injector);
                case IMPLICIT_FLOW:
                    return new ImplicitFlowOperation(command, injector);
                case REGISTER_SITE:
                    return new RegisterSiteOperation(command, injector);
                case GET_AUTHORIZATION_CODE:
                    return new GetAuthorizationCodeOperation(command, injector);
                case GET_LOGOUT_URI:
                    return new GetLogoutUrlOperation(command, injector);
                case UPDATE_SITE:
                    return new UpdateSiteOperation(command, injector);
                case RS_PROTECT:
                    return new RsProtectOperation(command, injector);
                case RS_CHECK_ACCESS:
                    return new RsCheckAccessOperation(command, injector);
                case RP_GET_RPT:
                    return new RpGetRptOperation(command, injector);
                case RP_AUTHORIZE_RPT:
                    return new RpAuthorizeRptOperation(command, injector);
                case RP_GET_GAT:
                    return new RpGetGatOperation(command, injector);
            }
            LOG.error("Command is not supported. Command: {}", command);
        } else {
            LOG.error("Command is invalid. Command: {}", command);
        }
        return null;
    }
}

package org.apereo.cas.ws.idp.web;

import org.apereo.cas.CasProtocolConstants;
import org.apereo.cas.authentication.AuthenticationServiceSelectionStrategy;
import org.apereo.cas.authentication.SecurityTokenServiceTokenFetcher;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.UnauthorizedServiceException;
import org.apereo.cas.ticket.SecurityTokenTicket;
import org.apereo.cas.ticket.SecurityTokenTicketFactory;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.util.http.HttpClient;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.apereo.cas.web.support.CookieRetrievingCookieGenerator;
import org.apereo.cas.web.support.CookieUtils;
import org.apereo.cas.ws.idp.WSFederationConstants;
import org.apereo.cas.ws.idp.services.WSFederationRegisteredService;
import org.apereo.cas.ws.idp.services.WSFederationRelyingPartyTokenProducer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.TicketValidator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This is {@link WSFederationValidateRequestCallbackController}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
public class WSFederationValidateRequestCallbackController extends BaseWSFederationRequestController {

    private final WSFederationRelyingPartyTokenProducer relyingPartyTokenProducer;
    private final TicketValidator ticketValidator;
    private final SecurityTokenServiceTokenFetcher securityTokenServiceTokenFetcher;

    public WSFederationValidateRequestCallbackController(final ServicesManager servicesManager,
                                                         final ServiceFactory<WebApplicationService> webApplicationServiceFactory,
                                                         final CasConfigurationProperties casProperties,
                                                         final WSFederationRelyingPartyTokenProducer relyingPartyTokenProducer,
                                                         final AuthenticationServiceSelectionStrategy serviceSelectionStrategy,
                                                         final HttpClient httpClient,
                                                         final SecurityTokenTicketFactory securityTokenTicketFactory,
                                                         final TicketRegistry ticketRegistry,
                                                         final CookieRetrievingCookieGenerator ticketGrantingTicketCookieGenerator,
                                                         final TicketRegistrySupport ticketRegistrySupport,
                                                         final TicketValidator ticketValidator,
                                                         final Service callbackService,
                                                         final SecurityTokenServiceTokenFetcher securityTokenServiceTokenFetcher) {
        super(servicesManager,
            webApplicationServiceFactory, casProperties,
            serviceSelectionStrategy, httpClient, securityTokenTicketFactory,
            ticketRegistry, ticketGrantingTicketCookieGenerator,
            ticketRegistrySupport, callbackService);
        this.relyingPartyTokenProducer = relyingPartyTokenProducer;
        this.ticketValidator = ticketValidator;
        this.securityTokenServiceTokenFetcher = securityTokenServiceTokenFetcher;
    }

    /**
     * Handle federation request.
     *
     * @param response the response
     * @param request  the request
     * @return the model and view
     * @throws Exception the exception
     */
    @GetMapping(path = WSFederationConstants.ENDPOINT_FEDERATION_REQUEST_CALLBACK)
    protected ModelAndView handleFederationRequest(final HttpServletResponse response, final HttpServletRequest request) throws Exception {
        final WSFederationRequest fedRequest = WSFederationRequest.of(request);
        LOGGER.debug("Received callback profile request [{}]", request.getRequestURI());

        final String serviceUrl = constructServiceUrl(request, response, fedRequest);
        final Service targetService = this.serviceSelectionStrategy.resolveServiceFrom(this.webApplicationServiceFactory.createService(serviceUrl));
        final WSFederationRegisteredService service = findAndValidateFederationRequestForRegisteredService(targetService, fedRequest);
        LOGGER.debug("Located matching service [{}]", service);

        final String ticket = CommonUtils.safeGetParameter(request, CasProtocolConstants.PARAMETER_TICKET);
        if (StringUtils.isBlank(ticket)) {
            LOGGER.error("Can not validate the request because no [{}] is provided via the request", CasProtocolConstants.PARAMETER_TICKET);
            return new ModelAndView(CasWebflowConstants.VIEW_ID_ERROR, new HashMap<>(), HttpStatus.FORBIDDEN);
        }

        final Assertion assertion = validateRequestAndBuildCasAssertion(response, request, fedRequest);
        SecurityToken securityToken = getSecurityTokenFromRequest(request);
        if (securityToken == null) {
            LOGGER.debug("No security token is yet available. Invoking security token service to issue token");
            securityToken = fetchSecurityTokenFromAssertion(assertion, targetService);
        }
        addSecurityTokenTicketToRegistry(request, securityToken);
        final String rpToken = produceRelyingPartyToken(request, fedRequest, securityToken, assertion, targetService);
        return postResponseBackToRelyingParty(rpToken, fedRequest);
    }

    private void addSecurityTokenTicketToRegistry(final HttpServletRequest request, final SecurityToken securityToken) {
        LOGGER.debug("Created security token as a ticket to CAS ticket registry...");
        final TicketGrantingTicket tgt = CookieUtils.getTicketGrantingTicketFromRequest(ticketGrantingTicketCookieGenerator, ticketRegistry, request);
        final SecurityTokenTicket ticket = securityTokenTicketFactory.create(tgt, securityToken);
        LOGGER.debug("Created security token ticket [{}]", ticket);
        this.ticketRegistry.addTicket(ticket);
        LOGGER.debug("Added security token as a ticket to CAS ticket registry...");
        this.ticketRegistry.updateTicket(tgt);
    }

    private static ModelAndView postResponseBackToRelyingParty(final String rpToken,
                                                               final WSFederationRequest fedRequest) {
        final String postUrl = StringUtils.isNotBlank(fedRequest.getWreply()) ? fedRequest.getWreply() : fedRequest.getWtrealm();

        final Map<String, Object> model = new HashMap<>();
        model.put("originalUrl", postUrl);

        final Map<String, String> parameters = new HashMap<>();
        parameters.put(WSFederationConstants.WA, WSFederationConstants.WSIGNIN10);
        parameters.put(WSFederationConstants.WRESULT, StringEscapeUtils.unescapeHtml4(rpToken));
        parameters.put(WSFederationConstants.WTREALM, fedRequest.getWtrealm());

        if (StringUtils.isNotBlank(fedRequest.getWctx())) {
            parameters.put(WSFederationConstants.WCTX, fedRequest.getWctx());
        }
        model.put("parameters", parameters);

        LOGGER.debug("Posting relying party token to [{}]", postUrl);
        return new ModelAndView(CasWebflowConstants.VIEW_ID_POST_RESPONSE, model);
    }

    private String produceRelyingPartyToken(final HttpServletRequest request,
                                            final WSFederationRequest fedRequest, final SecurityToken securityToken,
                                            final Assertion assertion, final Service targetService) {
        final WSFederationRegisteredService service = findAndValidateFederationRequestForRegisteredService(targetService, fedRequest);
        LOGGER.debug("Located registered service [{}] to create relying-party tokens...", service);
        return relyingPartyTokenProducer.produce(securityToken, service, fedRequest, request, assertion);
    }

    private SecurityToken fetchSecurityTokenFromAssertion(final Assertion assertion, final Service targetService) {
        final String principal = assertion.getPrincipal().getName();
        final Optional<SecurityToken> token = this.securityTokenServiceTokenFetcher.fetch(targetService, principal);
        if (!token.isPresent()) {
            LOGGER.warn("No security token could be retrieved for service [{}] and principal [{}]", targetService, principal);
            throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE);
        }
        return token.get();
    }

    private Assertion validateRequestAndBuildCasAssertion(final HttpServletResponse response,
                                                          final HttpServletRequest request,
                                                          final WSFederationRequest fedRequest) throws Exception {
        final String ticket = CommonUtils.safeGetParameter(request, CasProtocolConstants.PARAMETER_TICKET);
        final String serviceUrl = constructServiceUrl(request, response, fedRequest);
        LOGGER.trace("Created service url for validation: [{}]", serviceUrl);
        final Assertion assertion = this.ticketValidator.validate(ticket, serviceUrl);
        LOGGER.debug("Located CAS assertion [{}]", assertion);
        return assertion;
    }
}

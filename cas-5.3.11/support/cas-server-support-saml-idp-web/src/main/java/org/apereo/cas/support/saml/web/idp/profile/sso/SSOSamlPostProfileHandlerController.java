package org.apereo.cas.support.saml.web.idp.profile.sso;

import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import org.apache.commons.lang3.tuple.Pair;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.SamlIdPConstants;
import org.apereo.cas.support.saml.services.idp.metadata.cache.SamlRegisteredServiceCachingMetadataResolver;
import org.apereo.cas.support.saml.web.idp.profile.AbstractSamlProfileHandlerController;
import org.apereo.cas.support.saml.web.idp.profile.builders.SamlProfileObjectBuilder;
import org.apereo.cas.support.saml.web.idp.profile.builders.enc.SamlIdPObjectSigner;
import org.apereo.cas.support.saml.web.idp.profile.builders.enc.SamlObjectSignatureValidator;
import org.apereo.cas.support.saml.web.idp.profile.sso.request.SSOSamlHttpRequestExtractor;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.servlet.BaseHttpServletRequestXMLMessageDecoder;
import org.opensaml.saml.common.SignableSAMLObject;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPPostDecoder;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPRedirectDeflateDecoder;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The {@link SSOSamlPostProfileHandlerController} is responsible for
 * handling profile requests for SAML2 Web SSO.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
public class SSOSamlPostProfileHandlerController extends AbstractSamlProfileHandlerController {
    private final SSOSamlHttpRequestExtractor samlHttpRequestExtractor;

    public SSOSamlPostProfileHandlerController(final SamlIdPObjectSigner samlObjectSigner,
                                               final ParserPool parserPool,
                                               final AuthenticationSystemSupport authenticationSystemSupport,
                                               final ServicesManager servicesManager,
                                               final ServiceFactory<WebApplicationService> webApplicationServiceFactory,
                                               final SamlRegisteredServiceCachingMetadataResolver samlRegisteredServiceCachingMetadataResolver,
                                               final OpenSamlConfigBean configBean,
                                               final SamlProfileObjectBuilder<Response> responseBuilder,
                                               final CasConfigurationProperties casProperties,
                                               final SamlObjectSignatureValidator samlObjectSignatureValidator,
                                               final SSOSamlHttpRequestExtractor samlHttpRequestExtractor,
                                               final Service callbackService) {
        super(samlObjectSigner,
            parserPool,
            authenticationSystemSupport,
            servicesManager,
            webApplicationServiceFactory,
            samlRegisteredServiceCachingMetadataResolver,
            configBean,
            responseBuilder,
            casProperties,
            samlObjectSignatureValidator,
            callbackService);
        this.samlHttpRequestExtractor = samlHttpRequestExtractor;
    }


    /**
     * Handle SSO GET profile redirect request.
     *
     * @param response the response
     * @param request  the request
     * @throws Exception the exception
     */
    @GetMapping(path = SamlIdPConstants.ENDPOINT_SAML2_SSO_PROFILE_REDIRECT)
    public void handleSaml2ProfileSsoRedirectRequest(final HttpServletResponse response,
                                                     final HttpServletRequest request) throws Exception {
        handleSsoPostProfileRequest(response, request, new HTTPRedirectDeflateDecoder());
    }

    /**
     * Handle SSO HEAD profile redirect request (not allowed).
     *
     * @param response the response
     * @param request  the request
     * @throws Exception the exception
     */
    @RequestMapping(path = SamlIdPConstants.ENDPOINT_SAML2_SSO_PROFILE_REDIRECT, method = { RequestMethod.HEAD })
    public void handleSaml2ProfileSsoRedirectHeadRequest(final HttpServletResponse response,
                                                         final HttpServletRequest request) throws Exception {
        LOGGER.info("Endpoint [{}] called with HTTP HEAD returning 400 Bad Request", SamlIdPConstants.ENDPOINT_SAML2_SSO_PROFILE_REDIRECT);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    /**
     * Handle SSO POST profile request.
     *
     * @param response the response
     * @param request  the request
     * @throws Exception the exception
     */
    @PostMapping(path = SamlIdPConstants.ENDPOINT_SAML2_SSO_PROFILE_POST)
    public void handleSaml2ProfileSsoPostRequest(final HttpServletResponse response,
                                                 final HttpServletRequest request) throws Exception {
        handleSsoPostProfileRequest(response, request, new HTTPPostDecoder());
    }

    /**
     * Handle profile request.
     *
     * @param response the response
     * @param request  the request
     * @param decoder  the decoder
     * @throws Exception the exception
     */
    public void handleSsoPostProfileRequest(final HttpServletResponse response,
                                            final HttpServletRequest request,
                                            final BaseHttpServletRequestXMLMessageDecoder decoder) throws Exception {
        //包含解析request中的SAMLRequest参数
        final Pair<? extends SignableSAMLObject, MessageContext> authnRequest =
                this.samlHttpRequestExtractor.extract(request, decoder, AuthnRequest.class);
        //这里面包含检测SAML serviceId
        initiateAuthenticationRequest(authnRequest, response, request);
    }

}

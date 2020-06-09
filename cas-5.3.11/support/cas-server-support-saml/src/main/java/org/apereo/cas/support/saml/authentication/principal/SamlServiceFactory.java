package org.apereo.cas.support.saml.authentication.principal;

import org.apereo.cas.authentication.principal.AbstractServiceFactory;
import org.apereo.cas.support.saml.SamlIdPConstants;
import org.apereo.cas.support.saml.SamlProtocolConstants;
import org.apereo.cas.support.saml.util.Saml10ObjectBuilder;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.stream.Collectors;

/**
 * The {@link SamlServiceFactory} creates {@link SamlService} objects.
 *
 * @author Misagh Moayyed
 * @since 4.2
 */
@Slf4j
@RequiredArgsConstructor
public class SamlServiceFactory extends AbstractServiceFactory<SamlService> {
    private static final Namespace NAMESPACE_ENVELOPE = Namespace.getNamespace("http://schemas.xmlsoap.org/soap/envelope/");
    private static final Namespace NAMESPACE_SAML1 = Namespace.getNamespace("urn:oasis:names:tc:SAML:1.0:protocol");

    private final Saml10ObjectBuilder saml10ObjectBuilder;

    @Override
    public SamlService createService(final HttpServletRequest request) {
        /*
          * As per http://docs.oasis-open.org/security/saml/Post2.0/saml-ecp/v2.0/saml-ecp-v2.0.html we cannot create service from SAML ECP Request.
          * This will result in NullPointerException, when trying to get samlp:Request from S:Body.
          */
        final String requestURI = request.getRequestURI();
        LOGGER.trace("Current request URI is [{}]", requestURI);

        if (requestURI.contains(SamlIdPConstants.ENDPOINT_SAML2_IDP_ECP_PROFILE_SSO)) {
            LOGGER.trace("The [{}] request on [{}] seems to be a SOAP ECP Request, skip creating service from it.", request.getMethod(), requestURI);
            return null;
        }

        final String service = request.getParameter(SamlProtocolConstants.CONST_PARAM_TARGET);
        final String requestBody = requestURI.contains(SamlProtocolConstants.ENDPOINT_SAML_VALIDATE)
            && request.getMethod().equalsIgnoreCase(HttpMethod.POST.name()) ? getRequestBody(request) : null;

        String artifactId = null;
        String requestId = null;

        if (!StringUtils.hasText(service) && !StringUtils.hasText(requestBody)) {
            LOGGER.trace("Request does not specify a [{}] or request body is empty", SamlProtocolConstants.CONST_PARAM_TARGET);
            return null;
        }
        final String id = cleanupUrl(service);

        if (StringUtils.hasText(requestBody)) {
            LOGGER.debug("Request Body: [{}]", requestBody);

            request.setAttribute(SamlProtocolConstants.PARAMETER_SAML_REQUEST, requestBody);

            final Document document = saml10ObjectBuilder.constructDocumentFromXml(requestBody);
            if (document == null) {
                LOGGER.trace("Could not construct SAML document from request body [{}]", requestBody);
                return null;
            }
            final Element root = document.getRootElement();

            @NonNull final Element body = root.getChild("Body", NAMESPACE_ENVELOPE);
            if (body == null) {
                LOGGER.trace("Request body not specify a [Body] element");
                return null;
            }

            @NonNull final Element requestChild = body.getChild("Request", NAMESPACE_SAML1);
            if (requestChild == null) {
                LOGGER.trace("Request body not specify a [Request] element");
                return null;
            }

            @NonNull final Element artifactElement = requestChild.getChild("AssertionArtifact", NAMESPACE_SAML1);
            artifactId = artifactElement.getValue().trim();

            final Attribute requestIdAttribute = requestChild.getAttribute("RequestID");
            if (requestIdAttribute == null) {
                LOGGER.error("SAML request body does not specify the RequestID attribute. This is a required attribute per the schema definition and MUST be provided by the client. "
                    + " RequestID needs to be unique on a per-request basis and per OWASP, it may be 16 bytes of entropy in session identifiers which have similar requirements. "
                    + "While CAS does allow the RequestID attribute to be optional for the time being to preserve backward compatibility, this behavior MUST be fixed by the client "
                    + "and future CAS versions begin to enforce the presence of RequestID more forcefully to remain compliant with schema and protocol.");
            } else {
                requestId = requestIdAttribute.getValue().trim();
            }
        }

        LOGGER.debug("Extracted ArtifactId: [{}]. Extracted Request Id: [{}]", artifactId, requestId);
        final SamlService samlService = new SamlService(id, service, artifactId, requestId);
        samlService.setSource(SamlProtocolConstants.CONST_PARAM_TARGET);
        return samlService;
    }

    @Override
    public SamlService createService(final String id) {
        throw new NotImplementedException("This operation is not supported. ");
    }

    /**
     * Gets the request body from the request.
     *
     * @param request the request
     * @return the request body
     */
    private static String getRequestBody(final HttpServletRequest request) {
        String body = null;
        try (BufferedReader reader = request.getReader()) {
            if (reader == null) {
                LOGGER.debug("Request body could not be read because it's empty.");
            } else {
                body = reader.lines().collect(Collectors.joining());
            }
        } catch (final Exception e) {
            LOGGER.trace("Could not obtain the saml request body from the http request", e);
        }

        if (!StringUtils.hasText(body)) {
            LOGGER.trace("Looking at the request attribute [{}] to locate SAML request body", SamlProtocolConstants.PARAMETER_SAML_REQUEST);
            body = (String) request.getAttribute(SamlProtocolConstants.PARAMETER_SAML_REQUEST);
            LOGGER.trace("Located cached saml request body [{}] as a request attribute", body);
        }
        return body;
    }
}

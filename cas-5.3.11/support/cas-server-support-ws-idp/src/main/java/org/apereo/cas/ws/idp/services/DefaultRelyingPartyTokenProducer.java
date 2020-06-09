package org.apereo.cas.ws.idp.services;

import org.apereo.cas.CipherExecutor;
import org.apereo.cas.authentication.SecurityTokenServiceClient;
import org.apereo.cas.authentication.SecurityTokenServiceClientBuilder;
import org.apereo.cas.support.claims.WsFederationClaimsEncoder;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.ws.idp.WSFederationClaims;
import org.apereo.cas.ws.idp.WSFederationConstants;
import org.apereo.cas.ws.idp.web.WSFederationRequest;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.fediz.core.exception.ProcessingException;
import org.apache.cxf.rt.security.SecurityConstants;
import org.apache.cxf.staxutils.W3CDOMStreamWriter;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.apache.cxf.ws.security.trust.STSUtils;
import org.jasig.cas.client.validation.Assertion;
import org.w3c.dom.Element;

import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;

/**
 * This is {@link DefaultRelyingPartyTokenProducer}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultRelyingPartyTokenProducer implements WSFederationRelyingPartyTokenProducer {
    private final SecurityTokenServiceClientBuilder clientBuilder;
    private final CipherExecutor<String, String> credentialCipherExecutor;
    private final Collection<String> customClaims;

    @Override
    public String produce(final SecurityToken securityToken, final WSFederationRegisteredService service,
                          final WSFederationRequest fedRequest, final HttpServletRequest request,
                          final Assertion assertion) {
        LOGGER.debug("Building security token service client for service [{}]", service);
        final SecurityTokenServiceClient sts = clientBuilder.buildClientForRelyingPartyTokenResponses(securityToken, service);
        mapAttributesToRequestedClaims(service, sts, assertion);
        final Element rpToken = requestSecurityTokenResponse(service, sts, assertion);
        return serializeRelyingPartyToken(rpToken);
    }

    @SneakyThrows
    private static String serializeRelyingPartyToken(final Element rpToken) {
        final StringWriter sw = new StringWriter();
        final Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, BooleanUtils.toStringYesNo(Boolean.TRUE));
        t.transform(new DOMSource(rpToken), new StreamResult(sw));
        return sw.toString();
    }

    private void mapAttributesToRequestedClaims(final WSFederationRegisteredService service, final SecurityTokenServiceClient sts,
                                                final Assertion assertion) {
        try {
            final W3CDOMStreamWriter writer = new W3CDOMStreamWriter();
            writer.writeStartElement("wst", "Claims", STSUtils.WST_NS_05_12);
            writer.writeNamespace("wst", STSUtils.WST_NS_05_12);
            writer.writeNamespace("ic", WSFederationConstants.HTTP_SCHEMAS_XMLSOAP_ORG_WS_2005_05_IDENTITY);
            writer.writeAttribute("Dialect", WSFederationConstants.HTTP_SCHEMAS_XMLSOAP_ORG_WS_2005_05_IDENTITY);

            final Map<String, Object> attributes = assertion.getPrincipal().getAttributes();
            LOGGER.debug("Mapping principal attributes [{}] to claims for service [{}]", attributes, service);

            final WsFederationClaimsEncoder encoder = new WsFederationClaimsEncoder();
            attributes.forEach((k, v) -> {
                try {
                    final String claimName = encoder.encodeClaim(k);
                    if (WSFederationClaims.contains(claimName)) {
                        final String uri = WSFederationClaims.valueOf(k).getUri();
                        LOGGER.debug("Requesting claim [{}] mapped to [{}]", k, uri);
                        writeAttributeValue(writer, uri, v, service);
                    } else if (WSFederationClaims.containsUri(claimName)) {
                        LOGGER.debug("Requesting claim [{}] directly mapped to [{}]", k, claimName);
                        writeAttributeValue(writer, claimName, v, service);
                    } else if (customClaims.contains(claimName)) {
                        LOGGER.debug("Requesting CUSTOM CLAIM [{}] directly mapped to [{}]", k, claimName);
                        writeAttributeValue(writer, customClaims.stream().filter(
                            c -> c.equalsIgnoreCase(claimName)).findFirst().get(), v, service);
                    } else {
                        LOGGER.debug("Request claim [{}] is not defined/supported by CAS", claimName);
                        writeAttributeValue(writer, WSFederationConstants.getClaimInCasNamespace(claimName), v, service);
                    }
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });

            writer.writeEndElement();

            final Element claims = writer.getDocument().getDocumentElement();
            sts.setClaims(claims);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static void writeAttributeValue(final W3CDOMStreamWriter writer, final String uri,
                                            final Object attributeValue,
                                            final WSFederationRegisteredService service) throws Exception {
        writer.writeStartElement("ic", "ClaimValue", WSFederationConstants.HTTP_SCHEMAS_XMLSOAP_ORG_WS_2005_05_IDENTITY);
        writer.writeAttribute("Uri", uri);
        writer.writeAttribute("Optional", Boolean.TRUE.toString());

        final Collection values = CollectionUtils.toCollection(attributeValue);
        for (final Object value : values) {
            writer.writeStartElement("ic", "Value", WSFederationConstants.HTTP_SCHEMAS_XMLSOAP_ORG_WS_2005_05_IDENTITY);
            writer.writeCharacters(value.toString());
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    @SneakyThrows
    private Element requestSecurityTokenResponse(final WSFederationRegisteredService service,
                                                 final SecurityTokenServiceClient sts,
                                                 final Assertion assertion) {
        try {
            final String principal = assertion.getPrincipal().getName();
            sts.getProperties().put(SecurityConstants.USERNAME, principal);
            final String uid = credentialCipherExecutor.encode(principal);
            sts.getProperties().put(SecurityConstants.PASSWORD, uid);
            LOGGER.debug("Requesting security token response for service [{}] as [{}]", service, principal);
            return sts.requestSecurityTokenResponse(service.getAppliesTo());
        } catch (final SoapFault ex) {
            if (ex.getFaultCode() != null && "RequestFailed".equals(ex.getFaultCode().getLocalPart())) {
                throw new IllegalArgumentException(new ProcessingException(ProcessingException.TYPE.BAD_REQUEST));
            }
            throw ex;
        }
    }
}

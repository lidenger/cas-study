package org.apereo.cas.adaptors.duo.config;

import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.adaptors.duo.web.flow.DuoAuthenticationWebflowEventResolver;
import org.apereo.cas.adaptors.duo.web.flow.action.DuoAuthenticationWebflowAction;
import org.apereo.cas.adaptors.duo.web.flow.action.DuoDirectAuthenticationAction;
import org.apereo.cas.authentication.AuthenticationServiceSelectionPlan;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.MultifactorAuthenticationProviderSelector;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.web.flow.authentication.RankedMultifactorAuthenticationProviderSelector;
import org.apereo.cas.web.flow.resolver.CasWebflowEventResolver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.CookieGenerator;
import org.springframework.webflow.execution.Action;

/**
 * This is {@link DuoSecurityConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("duoSecurityConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Slf4j
public class DuoSecurityConfiguration {

    @Autowired
    @Qualifier("authenticationServiceSelectionPlan")
    private ObjectProvider<AuthenticationServiceSelectionPlan> authenticationRequestServiceSelectionStrategies;

    @Autowired
    @Qualifier("centralAuthenticationService")
    private ObjectProvider<CentralAuthenticationService> centralAuthenticationService;

    @Autowired
    @Qualifier("defaultAuthenticationSystemSupport")
    private ObjectProvider<AuthenticationSystemSupport> authenticationSystemSupport;

    @Autowired
    @Qualifier("defaultTicketRegistrySupport")
    private ObjectProvider<TicketRegistrySupport> ticketRegistrySupport;

    @Autowired
    @Qualifier("servicesManager")
    private ObjectProvider<ServicesManager> servicesManager;

    @Autowired(required = false)
    @Qualifier("multifactorAuthenticationProviderSelector")
    private MultifactorAuthenticationProviderSelector multifactorAuthenticationProviderSelector =
        new RankedMultifactorAuthenticationProviderSelector();

    @Autowired
    @Qualifier("warnCookieGenerator")
    private ObjectProvider<CookieGenerator> warnCookieGenerator;

    @Bean
    public Action duoNonWebAuthenticationAction() {
        return new DuoDirectAuthenticationAction();
    }

    @Bean
    public Action duoAuthenticationWebflowAction() {
        return new DuoAuthenticationWebflowAction(duoAuthenticationWebflowEventResolver());
    }

    @Bean
    public CasWebflowEventResolver duoAuthenticationWebflowEventResolver() {
        return new DuoAuthenticationWebflowEventResolver(authenticationSystemSupport.getIfAvailable(),
            centralAuthenticationService.getIfAvailable(),
            servicesManager.getIfAvailable(),
            ticketRegistrySupport.getIfAvailable(),
            warnCookieGenerator.getIfAvailable(),
            authenticationRequestServiceSelectionStrategies.getIfAvailable(),
            multifactorAuthenticationProviderSelector);
    }
}

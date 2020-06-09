package org.apereo.cas.ticket.registry;

import org.apereo.cas.CipherExecutor;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.ticket.ServiceTicket;
import org.apereo.cas.ticket.Ticket;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.apereo.cas.ticket.proxy.ProxyGrantingTicket;
import org.apereo.cas.util.DigestUtils;
import org.apereo.cas.util.serialization.SerializationUtils;

import com.google.common.io.ByteSource;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Scott Battaglia
 * @since 3.0.0
 * <p>
 * This is a published and supported CAS Server API.
 * </p>
 */
@Slf4j
@Setter
@NoArgsConstructor
public abstract class AbstractTicketRegistry implements TicketRegistry {

    private static final String MESSAGE = "Ticket encryption is not enabled. Falling back to default behavior";

    /**
     * The cipher executor for ticket objects.
     */
    protected CipherExecutor cipherExecutor;

    /**
     * @return specified ticket from the registry
     * @throws IllegalArgumentException if class is null.
     * @throws ClassCastException       if class does not match requested ticket
     *                                  class.
     */
    @Override
    public <T extends Ticket> T getTicket(final String ticketId, @NonNull final Class<T> clazz) {
        final Ticket ticket = this.getTicket(ticketId);
        if (ticket == null) {
            return null;
        }
        if (!clazz.isAssignableFrom(ticket.getClass())) {
            throw new ClassCastException("Ticket [" + ticket.getId() + " is of type " + ticket.getClass() + " when we were expecting " + clazz);
        }
        return (T) ticket;
    }

    @Override
    public long sessionCount() {
        try (Stream<Ticket> tgtStream = getTicketsStream().filter(TicketGrantingTicket.class::isInstance)) {
            return tgtStream.count();
        } catch (final Exception t) {
            LOGGER.trace("sessionCount() operation is not implemented by the ticket registry instance [{}]. "
                + "Message is: [{}] Returning unknown as [{}]", this.getClass().getName(), t.getMessage(), Long.MIN_VALUE);
            return Long.MIN_VALUE;
        }
    }

    @Override
    public long serviceTicketCount() {
        try (Stream<Ticket> stStream = getTicketsStream().filter(ServiceTicket.class::isInstance)) {
            return stStream.count();
        } catch (final Exception t) {
            LOGGER.trace("serviceTicketCount() operation is not implemented by the ticket registry instance [{}]. "
                + "Message is: [{}] Returning unknown as [{}]", this.getClass().getName(), t.getMessage(), Long.MIN_VALUE);
            return Long.MIN_VALUE;
        }
    }

    @Override
    public int deleteTicket(final String ticketId) {
        if (StringUtils.isBlank(ticketId)) {
            LOGGER.trace("No ticket id is provided for deletion");
            return 0;
        }
        final Ticket ticket = getTicket(ticketId);
        if (ticket == null) {
            LOGGER.trace("Could not fetch ticket id [{}] form the registry", ticketId);
            return 0;
        }
        return deleteTicket(ticket);
    }

    /**
     * Delete ticket.
     *
     * @param ticket the ticket
     * @return the count
     */
    @Override
    public int deleteTicket(final Ticket ticket) {
        final AtomicInteger count = new AtomicInteger(0);
        if (ticket instanceof TicketGrantingTicket) {
            LOGGER.debug("Removing children of ticket [{}] from the registry.", ticket.getId());
            final TicketGrantingTicket tgt = (TicketGrantingTicket) ticket;
            count.addAndGet(deleteChildren(tgt));
            if (ticket instanceof ProxyGrantingTicket) {
                deleteProxyGrantingTicketFromParent((ProxyGrantingTicket) ticket);
            } else {
                deleteLinkedProxyGrantingTickets(count, tgt);
            }
        }
        LOGGER.debug("Removing ticket [{}] from the registry.", ticket);
        if (deleteSingleTicket(ticket.getId())) {
            count.incrementAndGet();
        }
        return count.intValue();
    }

    /**
     * Delete tickets.
     *
     * @param tickets the tickets
     * @return the total number of deleted tickets
     */
    protected int deleteTickets(final Set<String> tickets) {
        return deleteTickets(tickets.stream());
    }

    /**
     * Delete tickets.
     *
     * @param tickets the tickets
     * @return the total number of deleted tickets
     */
    protected int deleteTickets(final Stream<String> tickets) {
        return tickets.mapToInt(this::deleteTicket).sum();
    }

    private void deleteLinkedProxyGrantingTickets(final AtomicInteger count, final TicketGrantingTicket tgt) {
        final Set<String> pgts = new LinkedHashSet<>(tgt.getProxyGrantingTickets().keySet());
        final boolean hasPgts = !pgts.isEmpty();
        count.getAndAdd(deleteTickets(pgts));
        if (hasPgts) {
            LOGGER.debug("Removing proxy-granting tickets from parent ticket-granting ticket");
            tgt.getProxyGrantingTickets().clear();
            updateTicket(tgt);
        }
    }

    private void deleteProxyGrantingTicketFromParent(final ProxyGrantingTicket ticket) {
        final ProxyGrantingTicket thePgt = ticket;
        thePgt.getTicketGrantingTicket().getProxyGrantingTickets().remove(thePgt.getId());
        updateTicket(thePgt.getTicketGrantingTicket());
    }

    /**
     * Delete TGT's service tickets.
     *
     * @param ticket the ticket
     * @return the count of tickets that were removed including child tickets and zero if the ticket was not deleted
     */
    protected int deleteChildren(final TicketGrantingTicket ticket) {
        final AtomicInteger count = new AtomicInteger(0);
        final Map<String, Service> services = ticket.getServices();
        if (services != null && !services.isEmpty()) {
            services.keySet().forEach(ticketId -> {
                if (deleteSingleTicket(ticketId)) {
                    LOGGER.debug("Removed ticket [{}]", ticketId);
                    count.incrementAndGet();
                } else {
                    LOGGER.debug("Unable to remove ticket [{}]", ticketId);
                }
            });
        }
        return count.intValue();
    }

    /**
     * Delete a single ticket instance from the store.
     *
     * @param ticketId the ticket id
     * @return true/false
     */
    public abstract boolean deleteSingleTicket(String ticketId);

    /**
     * Encode ticket id into a SHA-512.
     *
     * @param ticketId the ticket id
     * @return the ticket
     */
    protected String encodeTicketId(final String ticketId) {
        if (!isCipherExecutorEnabled()) {
            LOGGER.trace(MESSAGE);
            return ticketId;
        }
        if (StringUtils.isBlank(ticketId)) {
            return ticketId;
        }
        final String encodedId = DigestUtils.sha512(ticketId);
        LOGGER.debug("Encoded original ticket id [{}] to [{}]", ticketId, encodedId);
        return encodedId;
    }

    /**
     * Encode ticket.
     *
     * @param ticket the ticket
     * @return the ticket
     */
    @SneakyThrows
    protected Ticket encodeTicket(final Ticket ticket) {
        if (!isCipherExecutorEnabled()) {
            LOGGER.trace(MESSAGE);
            return ticket;
        }
        if (ticket == null) {
            LOGGER.debug("Ticket passed is null and cannot be encoded");
            return null;
        }
        LOGGER.debug("Encoding ticket [{}]", ticket);
        final byte[] encodedTicketObject = SerializationUtils.serializeAndEncodeObject(this.cipherExecutor, ticket);
        final String encodedTicketId = encodeTicketId(ticket.getId());
        final Ticket encodedTicket = new EncodedTicket(encodedTicketId, ByteSource.wrap(encodedTicketObject).read());
        LOGGER.debug("Created encoded ticket [{}]", encodedTicket);
        return encodedTicket;
    }

    /**
     * Decode ticket.
     *
     * @param result the result
     * @return the ticket
     */
    @SneakyThrows
    protected Ticket decodeTicket(final Ticket result) {
        if (!isCipherExecutorEnabled()) {
            LOGGER.trace(MESSAGE);
            return result;
        }
        if (result == null) {
            LOGGER.warn("Ticket passed is null and cannot be decoded");
            return null;
        }
        if (!result.getClass().isAssignableFrom(EncodedTicket.class)) {
            LOGGER.warn("Ticket passed is not an encoded ticket type; rather it's a [{}], no decoding is necessary.", result.getClass().getSimpleName());
            return result;
        }
        LOGGER.debug("Attempting to decode [{}]", result);
        final EncodedTicket encodedTicket = (EncodedTicket) result;
        final Ticket ticket = SerializationUtils.decodeAndDeserializeObject(encodedTicket.getEncodedTicket(), this.cipherExecutor, Ticket.class);
        LOGGER.debug("Decoded ticket to [{}]", ticket);
        return ticket;
    }

    /**
     * Decode tickets.
     *
     * @param items the items
     * @return the set
     */
    protected Collection<Ticket> decodeTickets(final Collection<Ticket> items) {
        return decodeTickets(items.stream()).collect(Collectors.toSet());
    }

    /**
     * Decode tickets.
     *
     * @param items the items
     * @return the set
     */
    protected Stream<Ticket> decodeTickets(final Stream<Ticket> items) {
        if (!isCipherExecutorEnabled()) {
            LOGGER.trace(MESSAGE);
            return items;
        }
        return items.map(this::decodeTicket);
    }

    protected boolean isCipherExecutorEnabled() {
        return this.cipherExecutor != null && this.cipherExecutor.isEnabled();
    }
}

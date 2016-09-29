package com.armedia.broker;

/**
 * Handling interface for received objects from message broker
 * 
 * @author dame.gjorgjievski
 *
 */
public interface AcmObjectBrokerClientHandler<E>
{
    /**
     * Handle for received objects
     * 
     * @param entity
     * @return handling result, true if handled successfully, false otherwise
     */
    public boolean handleObject(E entity);
}

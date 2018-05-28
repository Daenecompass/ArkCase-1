package com.armedia.acm.tools.activemq;

/*-
 * #%L
 * Tool Integrations: ActiveMQ Configuration
 * %%
 * Copyright (C) 2014 - 2018 ArkCase LLC
 * %%
 * This file is part of the ArkCase software. 
 * 
 * If the software was purchased under a paid ArkCase license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * ArkCase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * ArkCase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with ArkCase. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;

import javax.jms.Message;
import javax.jms.MessageListener;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by dmiller on 5/5/16.
 */
public class TestQueueListener implements MessageListener
{
    private transient final Logger log = LoggerFactory.getLogger(getClass());

    private AtomicInteger received = new AtomicInteger(0);

    @Override
    @JmsListener(destination = "testQueue.in")
    public void onMessage(Message message)
    {
        Integer sofar = received.incrementAndGet();
        log.info("got message # {}", sofar);

    }
}

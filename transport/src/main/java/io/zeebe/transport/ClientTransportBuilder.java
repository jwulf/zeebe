/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport;

import java.time.Duration;
import java.util.*;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.impl.*;
import io.zeebe.transport.impl.actor.*;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.transport.impl.memory.TransportMemoryPool;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;

public class ClientTransportBuilder
{
    /**
     * In the same order of magnitude of what apache and nginx use.
     */
    protected static final Duration DEFAULT_CHANNEL_KEEP_ALIVE_PERIOD = Duration.ofSeconds(5);
    protected static final long DEFAULT_CHANNEL_CONNECT_TIMEOUT = 500;

    private int messageMaxLength = 1024 * 512;
    protected Duration keepAlivePeriod = DEFAULT_CHANNEL_KEEP_ALIVE_PERIOD;

    protected Dispatcher receiveBuffer;
    private ActorScheduler scheduler;
    protected List<ClientInputListener> listeners;
    protected TransportChannelFactory channelFactory;

    private TransportMemoryPool requestMemoryPool = new NonBlockingMemoryPool(ByteValue.ofMegabytes(4));
    private TransportMemoryPool messageMemoryPool = new NonBlockingMemoryPool(ByteValue.ofMegabytes(4));

    protected Duration defaultRequestRetryTimeout = Duration.ofSeconds(15);

    public ClientTransportBuilder scheduler(ActorScheduler scheduler)
    {
        this.scheduler = scheduler;
        return this;
    }

    /**
     * Optional. If set, all incoming messages (single-message protocol) are put onto the provided buffer.
     * {@link ClientTransport#openSubscription(String, ClientMessageHandler)} can be used to consume from this buffer.
     */
    public ClientTransportBuilder messageReceiveBuffer(Dispatcher receiveBuffer)
    {
        this.receiveBuffer = receiveBuffer;
        return this;
    }

    public ClientTransportBuilder requestMemoryPool(TransportMemoryPool requestMemoryPool)
    {
        this.requestMemoryPool = requestMemoryPool;
        return this;
    }

    public ClientTransportBuilder messageMemoryPool(TransportMemoryPool messageMemoryPool)
    {
        this.messageMemoryPool = messageMemoryPool;
        return this;
    }

    public ClientTransportBuilder inputListener(ClientInputListener listener)
    {
        if (listeners == null)
        {
            listeners = new ArrayList<>();
        }
        this.listeners.add(listener);
        return this;
    }

    public ClientTransportBuilder messageMaxLength(int messageMaxLength)
    {
        this.messageMaxLength = messageMaxLength;
        return this;
    }

    /**
     * The period in which a dummy message is sent to keep the underlying TCP connection open.
     */
    public ClientTransportBuilder keepAlivePeriod(Duration keepAlivePeriod)
    {
        if (keepAlivePeriod.getSeconds() < 1)
        {
            throw new RuntimeException("Min value for keepalive period is 1s.");
        }
        this.keepAlivePeriod = keepAlivePeriod;
        return this;
    }

    public ClientTransportBuilder channelFactory(TransportChannelFactory channelFactory)
    {
        this.channelFactory = channelFactory;
        return this;
    }

    public ClientTransportBuilder defaultRequestRetryTimeout(Duration duration)
    {
        this.defaultRequestRetryTimeout = duration;
        return this;
    }

    public ClientTransport build()
    {
        validate();

        final ClientActorContext actorContext = new ClientActorContext();

        final Sender sender = new Sender(actorContext,
            messageMemoryPool,
            requestMemoryPool,
            keepAlivePeriod);

        final RemoteAddressListImpl remoteAddressList = new RemoteAddressListImpl();

        final TransportContext transportContext =
                buildTransportContext(
                        remoteAddressList,
                        new ClientReceiveHandler(sender, receiveBuffer, listeners),
                        receiveBuffer);

        return build(actorContext, transportContext);
    }

    protected TransportContext buildTransportContext(
            RemoteAddressListImpl addressList,
            FragmentHandler receiveHandler,
            Dispatcher receiveBuffer)
    {
        final TransportContext context = new TransportContext();
        context.setName("client");
        context.setReceiveBuffer(receiveBuffer);
        context.setMessageMaxLength(messageMaxLength);
        context.setRemoteAddressList(addressList);
        context.setReceiveHandler(receiveHandler);
        context.setChannelKeepAlivePeriod(keepAlivePeriod);

        if (channelFactory != null)
        {
            context.setChannelFactory(channelFactory);
        }
        else
        {
            context.setChannelFactory(new DefaultChannelFactory(scheduler.getMetricsManager(), context.getName()));
        }

        return context;
    }

    protected ClientTransport build(ClientActorContext actorContext, TransportContext context)
    {
        actorContext.setMetricsManager(scheduler.getMetricsManager());

        final ClientConductor conductor = new ClientConductor(actorContext, context);
        final Receiver receiver = new Receiver(actorContext, context);
        final Sender sender = actorContext.getSender();

        final ClientOutput output = new ClientOutputImpl(sender, defaultRequestRetryTimeout);

        context.setClientOutput(output);

        scheduler.submitActor(conductor, true);
        scheduler.submitActor(receiver, true);
        scheduler.submitActor(sender, true);

        return new ClientTransport(actorContext, context);
    }

    private void validate()
    {
        Objects.requireNonNull(scheduler, "Scheduler must be provided");
    }

}
/*
 * Copyright (c) 2019. Samuel Goldmann
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

package com.github.goldsam.axonframework.extensions.aws.eventhandling;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import static org.axonframework.common.BuilderUtils.assertNonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry;
import com.amazonaws.services.kinesis.model.Record;

import org.apache.http.util.ByteArrayBuffer;
import org.axonframework.eventhandling.EventMessage;

/**
 * DefaultKinesisMessageConverter
 */
public class DefaultKinesisMessageConverter implements KinesisMessageConverter {

    private final Serializer serializer;
    private final PartitionKeyResolver partitionKeyResolver;

    /**
     * Instantiates a {@link DefaultKinesisMessageConverter} based on the fields
     * contained in the {@link Builder}. The {@link PartitionKeyResolver} is
     * defaulted to a {@link AggregateIdentifierPartitionKeyResolver}. The
     * {@link Serializer} is a <b>hard requirement</b> and thus should be provided.
     * <p>
     * Will validate that the {@link Serializer} and {@link RoutingKeyResolver} are
     * not {@code null}, and will throw an {@link AxonConfigurationException} if for
     * either of them this holds.
     *
     * @param builder the {@link Builder} used to instantiate a
     *                {@link DefaultKinesisMessageConverter} instance.
     */
    protected DefaultKinesisMessageConverter(Builder builder) {
        builder.validate();
        this.serializer = builder.serializer;
        this.partitionKeyResolver = builder.partitionKeyResolver;
    }

    @Override
    public PutRecordsRequestEntry createPutRecordsRequestEntry(EventMessage<?> eventMessage) {

        ByteArrayOutputStream stream =

        SerializedObject<byte[]> serializedObject = eventMessage.serializePayload(serializer, byte[].class);
        String routingKey = partitionKeyResolver.resolvePartitionKey(eventMessage);


        // AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();
        // Map<String, Object> headers = new HashMap<>();
        // eventMessage.getMetaData().forEach((k, v) -> headers.put(Headers.MESSAGE_METADATA + "-" + k, v));
        // Headers.defaultHeaders(eventMessage, serializedObject).forEach((k, v) -> {
        //     if (k.equals(MESSAGE_TIMESTAMP)) {
        //         headers.put(k, formatInstant(eventMessage.getTimestamp()));
        //     } else {
        //         headers.put(k, v);
        //     }
        // });
        // properties.headers(headers);
        
        return new PutRecordsRequestEntry()
            .withData(ByteBuffer.wrap(serializedObject.getData()));
            
        
        //    (serializedObject.getData(), routingKey, properties.build(), false, false);
        
    }

    @Override
    public Optional<EventMessage<?>> readRecord(Record record) {
        if (!headers.keySet().containsAll(Arrays.asList(Headers.MESSAGE_ID, Headers.MESSAGE_TYPE))) {
            return Optional.empty();
        }
        Map<String, Object> metaData = new HashMap<>();
        headers.forEach((k, v) -> {
            if (k.startsWith(Headers.MESSAGE_METADATA + "-")) {
                metaData.put(k.substring((Headers.MESSAGE_METADATA + "-").length()), v);
            }
        });
        SimpleSerializedObject<byte[]> serializedMessage = new SimpleSerializedObject<>(
                messageBody, byte[].class,
                Objects.toString(headers.get(Headers.MESSAGE_TYPE)),
                Objects.toString(headers.get(Headers.MESSAGE_REVISION), null)
        );
        SerializedMessage<?> message = new SerializedMessage<>(
                Objects.toString(headers.get(Headers.MESSAGE_ID)),
                new LazyDeserializingObject<>(serializedMessage, serializer),
                new LazyDeserializingObject<>(MetaData.from(metaData))
        );
        String timestamp = Objects.toString(headers.get(MESSAGE_TIMESTAMP));
        if (headers.containsKey(Headers.AGGREGATE_ID)) {
            return Optional.of(new GenericDomainEventMessage<>(Objects.toString(headers.get(Headers.AGGREGATE_TYPE)),
                                                               Objects.toString(headers.get(Headers.AGGREGATE_ID)),
                                                               (Long) headers.get(Headers.AGGREGATE_SEQ),
                                                               message, () -> DateTimeUtils.parseInstant(timestamp)));
        } else {
            return Optional.of(new GenericEventMessage<>(message, () -> DateTimeUtils.parseInstant(timestamp)));
        }
    }

    /**
     * Builder class to instantiate a {@link DefaultKinesisMessageConverter}. The
     * {@link PartitionKeyResolver} is defaulted to an
     * {@link AggregateIdentifierPartitionKeyResolver}. The {@link Serializer} is a
     * <b>hard requirement</n> and thus should be provided.
     */
    public static class Builder {

        private Serializer serializer;
        private PartitionKeyResolver partitionKeyResolver = new AggregateIdentifierPartitionKeyResolver();

        /**
         * Sets the serializer to serialize the Event Message's payload and Metadata.
         *
         * @param serializer The serializer to serialize the Event Message's payload and
         *                   Meta Data with
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the strategy to use to resolve routing keys for Event Messages. Defaults
         * to a {@link PackageRoutingKeyResolver}.
         *
         * @param routingKeyResolver The strategy to use to resolve routing keys for
         *                           Event Messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder routingKeyResolver(PartitionKeyResolver partitionKeyResolver) {
            assertNonNull(partitionKeyResolver, "PartitionKeyResolver may not be null");
            this.partitionKeyResolver = partitionKeyResolver;
            return this;
        }

        /**
         * Initializes a {@link DefaultAMQPMessageConverter} as specified through this
         * Builder.
         *
         * @return a {@link DefaultAMQPMessageConverter} as specified through this
         *         Builder
         */
        public DefaultKinesisMessageConverter build() {
            return new DefaultKinesisMessageConverter(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect
         *                                    according to the Builder's specifications
         */
        protected void validate() {
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
            assertNonNull(partitionKeyResolver,
                    "The PartitionKeyResolver is a hard requirement and should be provided");
        }
    }
}

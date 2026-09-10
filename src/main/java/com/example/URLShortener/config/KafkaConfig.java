package com.example.URLShortener.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for asynchronous click-event processing.
 *
 * Uses String serialization/deserialization throughout to avoid
 * the Jackson 2 vs 3 classpath conflict in Spring Boot 4.
 *
 * JSON conversion is handled separately by the controller/consumer
 * using Jackson 3's ObjectMapper.
 *
 * Supports two TLS trust configurations:
 *
 * 1. Local/container development:
 *      ssl.truststore.location
 *
 * 2. Hosted deployment:
 *      ssl.truststore.certificates
 *
 * The second option allows the Aiven CA certificate to be supplied
 * through an environment variable, so the deployed container does
 * not require a mounted certificate file.
 */
@Configuration
public class KafkaConfig {

    public static final String CLICK_EVENTS_TOPIC =
            "url-click-events";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    /*
     * Used when the CA certificate is supplied as a file.
     *
     * Example:
     *
     * /run/secrets/aiven-kafka-ca.pem
     */
    @Value("${spring.kafka.properties.ssl.truststore.location:}")
    private String sslTruststoreLocation;

    /*
     * PEM is the appropriate type for the Aiven CA certificate.
     */
    @Value("${spring.kafka.properties.ssl.truststore.type:PEM}")
    private String sslTruststoreType;

    /*
     * Used by hosted deployments such as Render.
     *
     * The certificate itself can be supplied through an environment
     * variable instead of requiring a mounted file.
     */
    @Value("${spring.kafka.properties.ssl.truststore.certificates:}")
    private String sslTruststoreCertificates;


    // ========================================================================
    // Topic
    // ========================================================================

    @Bean
    public NewTopic clickEventsTopic() {

        return TopicBuilder
                .name(CLICK_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }


    // ========================================================================
    // Producer
    // ========================================================================

    @Bean
    public ProducerFactory<String, String> producerFactory() {

        Map<String, Object> props =
                baseKafkaProperties();

        props.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        props.put(
                ProducerConfig.ACKS_CONFIG,
                "1"
        );

        return new DefaultKafkaProducerFactory<>(props);
    }


    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory) {

        return new KafkaTemplate<>(
                producerFactory
        );
    }


    // ========================================================================
    // Consumer
    // ========================================================================

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {

        Map<String, Object> props =
                baseKafkaProperties();

        props.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "analytics-consumer-group"
        );

        props.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        props.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        return new DefaultKafkaConsumerFactory<>(
                props
        );
    }


    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String>
                factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                consumerFactory
        );

        /*
         * Keep three consumers so the application can make use
         * of multiple Kafka partitions when available.
         *
         * The current Aiven topic has one partition, so only one
         * consumer will actively receive records.
         */
        factory.setConcurrency(3);

        return factory;
    }


    // ========================================================================
    // Shared Kafka connection properties
    // ========================================================================

    private Map<String, Object> baseKafkaProperties() {

        Map<String, Object> props =
                new HashMap<>();

        // --------------------------------------------------------------------
        // Bootstrap servers
        // --------------------------------------------------------------------

        props.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );


        // --------------------------------------------------------------------
        // Security protocol
        // --------------------------------------------------------------------

        props.put(
                "security.protocol",
                securityProtocol
        );


        // --------------------------------------------------------------------
        // SASL
        // --------------------------------------------------------------------

        if (!saslMechanism.isBlank()) {

            props.put(
                    "sasl.mechanism",
                    saslMechanism
            );
        }

        if (!saslJaasConfig.isBlank()) {

            props.put(
                    "sasl.jaas.config",
                    saslJaasConfig
            );
        }


        // --------------------------------------------------------------------
        // TLS trust configuration
        // --------------------------------------------------------------------

        /*
         * Prefer certificate contents when supplied.
         *
         * This is useful for Render because Render does not need
         * a host-mounted CA file.
         */
        if (!sslTruststoreCertificates.isBlank()) {

            props.put(
                    "ssl.truststore.certificates",
                    sslTruststoreCertificates
            );

            props.put(
                    "ssl.truststore.type",
                    "PEM"
            );

        } else if (!sslTruststoreLocation.isBlank()) {

            /*
             * Fall back to the certificate file.
             *
             * This preserves the existing local Docker workflow.
             */
            props.put(
                    "ssl.truststore.location",
                    sslTruststoreLocation
            );

            props.put(
                    "ssl.truststore.type",
                    sslTruststoreType
            );
        }

        return props;
    }
} 
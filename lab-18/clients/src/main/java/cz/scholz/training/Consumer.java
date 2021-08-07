package cz.scholz.training;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SslConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.regex.Pattern;

public class Consumer {
    private final static Logger LOG = LoggerFactory.getLogger(Consumer.class);

    public static void main(String[] args) {
        /*
         * Configure the logger
         */
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");

        /*
         * Configure the consumer
         */
        Properties props = new Properties();

        // Initialize the config provider
        props.put("config.providers", "secrets");
        props.put("config.providers.secrets.class", "io.strimzi.kafka.KubernetesSecretConfigProvider");

        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "UPDATE-BASED-ON-YOUR-KAFKA-CLUSTER:9092");

        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "my-group");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");

        props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");
        props.put(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, "${secrets:myproject/my-user:user.crt}");
        props.put(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, "${secrets:myproject/my-user:user.key}");
        props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
        props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, "${secrets:myproject/my-cluster-cluster-ca-cert:ca.crt}");
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS");

        /*
         * Create the consumer and subscribe to topics
         */
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Pattern.compile("my-topic"));

        /*
         * Consume messages
         */
        while (true)    {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(60));

            if (records.isEmpty()) {
                LOG.warn("No messages received");
                break;
            }

            records.forEach(record -> {
                LOG.info("Received message: {}", record.value());
            });
        }

        /*
         * Close the consumer
         */
        consumer.close();
    }
}

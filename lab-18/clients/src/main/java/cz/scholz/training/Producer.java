package cz.scholz.training;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SslConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class Producer {
    private final static Logger LOG = LoggerFactory.getLogger(Producer.class);

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        /*
         * Configure the logger
         */
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");

        /*
         * Configure the producer
         */
        Properties props = new Properties();

        // Initialize the config provider
        props.put("config.providers", "secrets");
        props.put("config.providers.secrets.class", "io.strimzi.kafka.KubernetesSecretConfigProvider");

        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.1.72:31424");

        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");

        props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");
        props.put(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, "${secrets:myproject/my-user:user.crt}");
        props.put(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, "${secrets:myproject/my-user:user.key}");
        props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
        props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, "${secrets:myproject/my-cluster-cluster-ca-cert:ca.crt}");
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS");

        /*
         * Create the producer
         */
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        /*
         * Send message
         */
        LOG.info("Sending message ...");
        ProducerRecord<String, String> msg = new ProducerRecord<>("my-topic", "Hello World");
        producer.send(msg).get();

        /*
         * Close producer
         */
        producer.close();
    }
}

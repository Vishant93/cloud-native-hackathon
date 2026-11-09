package com.example.vishant.cloud_native_hackathon;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@Configuration
@EnableKafka
public class KafkaConfiguration {

	@Bean
	ProducerFactory<String, CheckInEvent> checkInProducerFactory(
			@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
		var properties = new HashMap<String, Object>();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
		properties.put(ProducerConfig.ACKS_CONFIG, "all");
		properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		return new DefaultKafkaProducerFactory<>(properties);
	}

	@Bean
	KafkaTemplate<String, CheckInEvent> checkInKafkaTemplate(
			ProducerFactory<String, CheckInEvent> checkInProducerFactory) {
		return new KafkaTemplate<>(checkInProducerFactory);
	}

	@Bean
	ConsumerFactory<String, CheckInEvent> checkInConsumerFactory(
			@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
			@Value("${spring.kafka.consumer.group-id:eventpulse-check-in-consumer}") String groupId,
			@Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset) {
		Map<String, Object> properties = new HashMap<>();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
		properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

		var valueDeserializer = new JacksonJsonDeserializer<>(CheckInEvent.class, false);
		valueDeserializer.addTrustedPackages("com.example.vishant.cloud_native_hackathon");
		return new DefaultKafkaConsumerFactory<>(
				properties,
				new StringDeserializer(),
				valueDeserializer);
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, CheckInEvent> checkInKafkaListenerContainerFactory(
			ConsumerFactory<String, CheckInEvent> checkInConsumerFactory,
			@Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
		var factory = new ConcurrentKafkaListenerContainerFactory<String, CheckInEvent>();
		factory.setConsumerFactory(checkInConsumerFactory);
		factory.setAutoStartup(autoStartup);
		return factory;
	}
}

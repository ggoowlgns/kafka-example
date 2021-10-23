package com.jhpark.kafka.demo.configuration;

import org.apache.kafka.clients.ClientRequest;
import org.apache.kafka.clients.ClientResponse;
import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.clients.RequestCompletionHandler;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.requests.AbstractRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Configuration
public class KafkaConfig {

  @Bean
  public KafkaProducer kafkaProducer() {
    Properties kafkaProperties = new Properties();
    KafkaProducer kafkaProducer = new KafkaProducer(kafkaProperties);
    return kafkaProducer;
  }

  @Bean
  public KafkaConsumer kafkaConsumer() {
    Properties kafkaProperties = new Properties();
    KafkaConsumer kafkaConsumer = new KafkaConsumer(kafkaProperties);
    return kafkaConsumer;
  }

  @Bean
  public KafkaClient kafkaClient() {
    KafkaClient kafkaClient = new KafkaClient() {
      @Override
      public boolean isReady(Node node, long l) {
        return false;
      }

      @Override
      public boolean ready(Node node, long l) {
        return false;
      }

      @Override
      public long connectionDelay(Node node, long l) {
        return 0;
      }

      @Override
      public long pollDelayMs(Node node, long l) {
        return 0;
      }

      @Override
      public boolean connectionFailed(Node node) {
        return false;
      }

      @Override
      public AuthenticationException authenticationException(Node node) {
        return null;
      }

      @Override
      public void send(ClientRequest clientRequest, long l) {

      }

      @Override
      public List<ClientResponse> poll(long l, long l1) {
        return null;
      }

      @Override
      public void disconnect(String s) {

      }

      @Override
      public void close(String s) {

      }

      @Override
      public Node leastLoadedNode(long l) {
        return null;
      }

      @Override
      public int inFlightRequestCount() {
        return 0;
      }

      @Override
      public boolean hasInFlightRequests() {
        return false;
      }

      @Override
      public int inFlightRequestCount(String s) {
        return 0;
      }

      @Override
      public boolean hasInFlightRequests(String s) {
        return false;
      }

      @Override
      public boolean hasReadyNodes(long l) {
        return false;
      }

      @Override
      public void wakeup() {

      }

      @Override
      public ClientRequest newClientRequest(String s, AbstractRequest.Builder<?> builder, long l, boolean b) {
        return null;
      }

      @Override
      public ClientRequest newClientRequest(String s, AbstractRequest.Builder<?> builder, long l, boolean b, int i, RequestCompletionHandler requestCompletionHandler) {
        return null;
      }

      @Override
      public void initiateClose() {

      }

      @Override
      public boolean active() {
        return false;
      }

      @Override
      public void close() throws IOException {

      }
    };
    return kafkaClient;
  }

}

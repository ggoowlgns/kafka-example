package com.jhpark.kafka.demo.configuration;

import org.apache.kafka.clients.ClientRequest;
import org.apache.kafka.clients.ClientResponse;
import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.clients.RequestCompletionHandler;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.requests.AbstractRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Configuration
public class KafkaConfig {

  @Value("${kafka.brokers}")
  private String kafkaBrokers;

  /**
   * Producer Properties
   * acks : 보내고 응답을 기다리는지 여부 (클수록 성능 낮고, 안전성 높음)
   *  - acks = 0 : 응답을 기다리지 않음 - 재요청 설정도 적용되지 않음 : 가장 높은 처리량 (성능)
   *    - 일반적인 운영 환경에서는 손실 발생x but, 브로커 다운 등 장애 상황에서는 손실 가능성 높음
   *  - acks = 1 : 리더의 기록은 보장. but, 팔로워는 확인x
   *    - 거의 손실은 발생하지 않지만 특수한 상황(아주 예외적인 상황)에서는 손실 가능성 존재
   *      - 리더에 write 을 응답하는 ack를 보내자 마자 브로커 죽음 -> 팔로워중 하나가 리더가 됐는데  메시지가 없음 = 손실
   *    - 유명한 producer application : logstash, filebeat 도 acks = 1 로 하고 있다.
   *  - acks = all | -1 : 리더는 ISR의 팔로워로 부터 데이터 ack까지 기다림 - 하나 이상의 팔로워가 있는 한 데이터는 손실되지 않음 - 데이터 무손실을 가장 강력하게 보호
   *    - 손실이 아예 없이 but, 전송 속도는 느림
   *      - 브로커의 설정도 바껴야 한다.
   *        - 최소 replication factor : min.insync.replicas - 레플리케이션 몇대까지 sync가 맞아야 OK를 하겠는가
   *          - 값이 1로 되어 있느면 실상 acks = 1 로 한것과 다르지 않다. (리더만 OK 하면 다 된걸로 인지할테니)
   *          - acks = all 로 할꺼면 rp factor 는 2 이상으로 ㄱㄱ
   *          - acks = all , min.insync.replicas = 2, replication factor = 3 으로 권장한다.
   *            - min.insync.replicas = 3 하면 안된다... 브로커 한대가 죽으면 replication 은 2개밖에 존재하지 않기 떄문에 error 발생
   *
   *  buffer.memory (batch 전송, 딜레이): 데이터를 보내기 위해 잠시 대기 하는 전체 메모리 Byte\
   *  compression.type : 데이터를 압축해서 보낼때 압축 방법
   *  batch.size : 큰 데이터는 배치를 시도하지 않는다 - 함께 보내는 사이즈 : 장애 발생시 모으고 있던 메시지는 날라감 -> 고가용성을 보장하고 싶으면 너무 높게 잡지 않는게 좋음
   *  linger.ms : batch 전송을 위해 기다리는 시간
   *      - 지정된 batch.size가 차면 해당 설정을 무시하고 바로 전송
   *      - default : 0 (지연 없음)
   *  max.request.size : 보낼수 있는 최대 메시지 사이즈 (byte)
   * @return
   */
  @Bean
  public KafkaProducer kafkaProducer() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaBrokers);
    //key, value 로 String 을 사용할 예정
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    props.put("acks", "1");
    props.put("compression.type", "gzip");

    KafkaProducer kafkaProducer = new KafkaProducer(props);
    return kafkaProducer;
  }

  /**
   * Consumer Properties
   * - bootstrap.servers : 브로커 리스트 전부를 입력하는걸 권장 - 하나만 입력해도 돌아가기는 하는데 but, 특정 broker가 죽으면 접속이 불가능
   * - fetch.min.bytes : 한번에 가져오는 최소 data size
   * - fetch.max.bytes : 한번에 가져오는 최대 data size
   * - group.id
   * - enable.auto.commit : 백그라운드로 주기적으로 offset commit
   * - auto.offset.reset : kafka에 저장된 초기 오프셋이 없거나 현재 오프셋이 더이상 존재하지 않는 경우 reset 옵션
   *    - earliest : 가장 초기의 offset
   *    - latest : 가장 마지막 오프셋으로
   *    - none : 이전 offset을 찾지 못하면 error 내림
   * - request.timeout.ms : 응답 기다리는 최대 시간
   * - session.timeout.ms (default : 10): 컨슈머가 해당 시간동안 heartbeat를 보내지 않으면 (그룹 코디네이터에게) -> 해당 컨슈머는 장애가 발생한 것으로 판단하고 컨슈머 그룹은 리밸런싱을 시도한다.
   *    - 해당 값을 낮추면 실패를 빨리 감지할수는 있지만, GC|poll 시간이 증가하면 원하지 않는 리밸런스가 일어나기도 한다.
   * - heartbeat.interval.ms (default : 3초) : heartbeat를 얼마나 자주 보낼지 결정  - session.timeout.ms 보다 무조건 낮아야 한다.
   *                            보통 session.timeout.ms보다 1/3 값으로 설정
   * - max.poll.records : 단일 호출 poll()에 대한 최대 레코드 수를 조정 - app이 polling loop에서 데이터 양 조정
   * - max.poll.interval.ms : 계속 heartbeat만 보내고 실제 메시지를 가져가지 않는 경우도 있는데, 이러한 경우 컨슈머가 무한정 파티션을 점유할 수 있기 때문에
   *                          일정 기간동안 실제 poll를 하지 않으면 장애라고 판단하는 시간 -> 다른 컨슈머에게 점유하고 있는 파티션을 나눠준다.
   * - fetch.max.wait.ms : min.bytes 데이터보다 적은 경우 요청에 응답을 기다리는 최대 시간
   * @return
   */
  @Bean
  public KafkaConsumer kafkaConsumer() {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", kafkaBrokers);
    properties.put("group.id", "jhpark-consumer");
    properties.put("enable.auto.commit", "true");
    properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringSerializer");
    KafkaConsumer kafkaConsumer = new KafkaConsumer(properties);
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

package com.jhpark.kafka.demo.component;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ch 5. Consumer
 * 주요 기능
 *  1. 특정 파티션을 관리하고 있는 파티션 리더(브로커)에 메시지 가져오기 요청을 보낸다.
 *  2. 각 요청은 offset 을 명시하고 그 위치에서 로그 메시지를 수신 -> 즉, 가져올 메시지를 조절할 수 있다. (이미 가져온 메시지를 또 가져올수도 있다.)
 *
 *  종류
 *   1. 올드 컨슈머 : 0.9 이전 - offset 을 znode 에 저장하는 방식
 *   2. 뉴 컨슈머 : 0.9 이후 - offset을 topic에 저장하는 방식
 *
 * 기본 개념
 *  - msg를 produce하면 각 파티션에 분할되어 저장된다. -> 파티션이 분할되어 저장되어 있으면 집어 넣은대로 다 들어오지는 않는다. (동일한 파티션만을 생각하면 순서대로 들어옴)
 *        -but, 파티션과 파티션 사이에는 순서를 보장하지 않는다.
   *    - 1 : b,e
   *    - 2 : a,d
   *    - 3 : c
 *    - 결과 : adbec 로 들어옴
 *  그럼 순서를 보장해야되는 서비스는?
 *    - partition 설정을 1로 하면 된다..
 *    - 단점 : 분산처리가 안되기 떄문에 처리량이 높지 않다.
 *
 * 컨슈머 그룹
 *  - 하나의 토픽에 여러개의 consumer.group 이 붙어서 데이터를 가져갈 수 있다. (하나의 데이터를 여러곳에서 활용 가능)
 *  - 갑자기 프로듀스 하는 데이터 양이 많아지면 ? : 컨슈머 속도가 밀릴수도 있다.
 *  - 컨슈머 그룹내에 컨슈머를 추가하면 (c1 -> c1, c2, c3) : c1이 소유하고 있던 파티션을 넘겨준다. (리밸런스)
 *    - but, 리밸런스가 발생하면 해당 컨슈머 그룹은 일시적으로 사용할 수 없는 단점이 있다.
 *  - 또 하나의 파티션에는 하나의 컨슈머만 붙을수 있다. -> c4로 늘려도 파티션 수가 3개이면 의미가 없다.
 *    - 즉, 컨슈머 속도가 따라가지 못한다면 -> 토픽의 파티션 수를 늘리고 컨슈머 수를 같이 늘려야 한다.
 *
 * 컨슈머 다운 상황
 *  - 컨슈머가 잘 유지되는 상황은 : heartbeat를 잘 보내는것 -> [중요] heartbeat는 poll 할 때 가져간 메시지의 오프셋을 commit 할 때 보낸다.
 *        -> 컨슈머가 오랜기간 heartbeat를 보내지 않으면 session.timeout -> 컨슈머 다운으로 취급 -> 리밸런스
 *
 * % 컨슈머 그룹마다 각자 offset은 별도로 관리된다.
 *
 * 커밋과 오프셋
 *  - 컨슈머 그룹이 어디까지 msg를 가져갔는지 위치를 업데이트 -> 커밋
 *  - 커밋 방법
 *    - 자동 커밋
 *       - poll  호출의 가장 마지막에 offset commit (commit 주기도 있고), poll 마지막 마다 commit 할 시간인지를 체크하고 맞으면 마지막에 commit
 *       - 문제 상황
 *          - 가져오는 중간에 commit 시간이 되지 않았는데 리밸런싱 발생 -> 다른 컨슈머가 해당 파티션에서 땡겨옴 (이미 가져간 애들도 또 가져옴) -> 메시지 중복
 *          - auto commit interval time 을 줄이면 중복 확률을 낮출수는 있다.
 *     - 수동 커밋 : `consumeMessageWithManualCommit()`
 *        - 필요 상황 : 가져온 메시지의 처리가 완료되기 전까지 메시지를 가져온 것으로 간주되면 안되는 상황 -> poll 하고, 처리도 다 하고 commit 해야함 (auto는 poll 끝나자 마자 commit)
 *          - 가져와서 db에 넣는 상황 : auto commit이면 최악의 상황에 poll 정상 종료 & auto commit 까지 하고 app down되면 데이터 유실 발생
 *             - db에 저장을 하고 commit을 하는게 가장 안전하다. (app down을 하면 다른데서 긁어가서 update하면 되니까까)
 *
 *  특정 파티션 할당 : `consumerMessageSpecificPartition()`
 *   - 특정 파티션만 세밀하게 제어
 *   - 배경
 *    - 특정 파티션에만 특수하게 msg 저장 & 그 파티션에서만 가져와야함
 *    - 컨슈머 프로세스의 가용성이 보장되어 해당 컨슈머의 실패를 재조정할 필요가 없는경우
 */
@Component
@RequiredArgsConstructor
public class KafkaConsumerComponent {
  private final Logger LOG = LoggerFactory.getLogger(KafkaConsumerComponent.class);

  private final KafkaConsumer kafkaConsumer;

  private List<String> topics;

  @PostConstruct
  void init() {
    topics = new ArrayList<>();
    topics.add("jhpark-topic-1");
  }

  public void consumeMessage() {
    kafkaConsumer.subscribe(topics);
    try {
      while (true) {
        /**
         컨슈머는 주기적인 polling 을 유지해야 한다. : 그렇지 않으면 종료된것으로 간주되어 현재 컨슈머에 할당된 파티션은 다른 컨슈머에게 전달되고, 그 컨슈머에서 소비된다.
            - poll(timeout) : 데이터가 컨슈머 버퍼에 없으면 timeout 시간 동안 블럭된다. : 0으로 설정하면 즉시 리턴;
         */
        ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
        for (ConsumerRecord record : records) {
          LOG.info("Topics : {}, \n Partitions : {}, \n Offset: {}, \n Key : {}, \n Value : {} \n",
              record.topic(), record.partition(), record.offset(), record.key(), record.value());
        }
      }
    }
    finally {
      /**
       *  컨슈머를 종료하기 전에 close()메소드로 네트워크 연결과 소켓을 종료한다. -> 이렇게 하는게 바로 리밸런스 유도가 가능하다. (코디네이터가 감지하는것 보다 훨씬 빠르다.)
       */
      kafkaConsumer.close();
    }
  }

  /**
    수동 커밋
    % 필수 : properties.put("enable.auto.commit", "false");
    메시지를 가져온 것으로 간주되는 시점을 자유롭게 조정 가능
    - 문제
      - 그래도 아직 중복 저장의 이슈는 존재한다. :
        - 극악의 상황 : db insert 과정에서 error (이미 조금 들어감 - transactional 치면 문제 없을듯 하지만,,) -> kafka commit을 하지는 않았기에 다른데서 또 가져가서 insert 시도 함
   */
  public void consumeMessageWithManualCommit() {
    kafkaConsumer.subscribe(topics);
    try {
      while (true) {
        ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
        for (ConsumerRecord record : records) {
          LOG.info("Topics : {}, \n Partitions : {}, \n Offset: {}, \n Key : {}, \n Value : {} \n",
              record.topic(), record.partition(), record.offset(), record.key(), record.value());
        }
        try {
          kafkaConsumer.commitSync();
        }catch (CommitFailedException e) {
          LOG.error("consumeMessageWithManualCommit : ", e);
        }
      }
    } finally {
      kafkaConsumer.close();
    }
  }

  /**
   * 특수 상황 : 특정 partition만 가져오기
   * % 필수 : properties.put("enable.auto.commit", "false");
   * 직접 파티션을 특정하여 가져온다.
   * - 주의
   *   - 컨슈머 인스턴스마다 group.id 를 다르게 설정해야함, -> 동일한 group.id로 설정하면, 다른 컨슈머가 요 partition을 할당받아서 땡겨가고 commit 할수도 있다.
   *     -> 그럼 현재 컨슈머는 msg를 부분적으로만 수신이 가능하다.
   *
   * 번외 : 특정 offset에서도 가져올수 있다. : kafkaConsumer.seek(partition0, 2)
   */
  public void consumerMessageSpecificPartition() {
    String topic = "jhpark-special-topic-1";
    TopicPartition partition0 = new TopicPartition(topic, 0);
    TopicPartition partition1 = new TopicPartition(topic, 1);
    kafkaConsumer.assign(Arrays.asList(partition0,partition1));
    try {
      while (true) {
        ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
        for (ConsumerRecord record : records) {
          LOG.info("Topics : {}, \n Partitions : {}, \n Offset: {}, \n Key : {}, \n Value : {} \n",
              record.topic(), record.partition(), record.offset(), record.key(), record.value());
        }
        try {
          kafkaConsumer.commitSync();
        } catch (CommitFailedException e) {
          LOG.error("consumeMessageWithManualCommit : ", e);
        }
      }
    } finally {
      kafkaConsumer.close();
    }
  }

}

package com.jhpark.kafka.demo.component;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class KafkaProducerComponent {
  private Logger LOG = LoggerFactory.getLogger(KafkaProducerComponent.class);

  private final KafkaProducer kafkaProducer;

  /**
   * send 하고 기다림
   * @param topic
   * @param msg
   * 보통 예외는 크게 두가지
   * 1. 재시도 가능한 예외 -> ex) 커넥션 에러
   * 2. 재시도 불가능한 예외 -> ex) msg가 너무 큰 경우
   */
  public void sendMessageSync(String topic, String msg) {
    try {
      RecordMetadata metadata = (RecordMetadata) kafkaProducer.send(new ProducerRecord(topic, msg)).get();
      LOG.info("Partition : {}, Offset : {}", metadata.partition(), metadata.offset());
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    } finally {
      kafkaProducer.close();
    }
  }

  /**
   * send Async
   * @param topic
   * @param msg
   * 에러로 남기거나 Callback으로 후속 처리
   */
  public void sendMessageAsync(String topic, String msg) {
    try {
      kafkaProducer.send(new ProducerRecord(topic, msg), new KafkaSendCallback());
    } catch (Exception e){
      LOG.error("Exception : ", e);
    }
    finally {
      kafkaProducer.close();
    }
  }

  /**
   * send with Key : msg 별로 partition 특정
   * @param topic
   * @param msg
   *
   */
  public void sendMessageWithKey(String topic, String msg) {
    String oddKey = "1";
    String evenKey = "2";
    try {
      for (int i = 1; i<11 ;i++) {
        if (i % 2 == 1) {
          kafkaProducer.send(new ProducerRecord(topic, oddKey, msg), new KafkaSendCallback());
        } else {
          kafkaProducer.send(new ProducerRecord(topic, evenKey, msg), new KafkaSendCallback());
        }
      }
    } catch (Exception e){
      LOG.error("Exception : ", e);
    }
    finally {
      kafkaProducer.close();
    }
  }

  class KafkaSendCallback implements Callback {
    private Logger LOG = LoggerFactory.getLogger(KafkaSendCallback.class);
    @Override
    public void onCompletion(RecordMetadata metadata, Exception e) {
      if (metadata != null) {
        LOG.info("Partition : {}, Offset : {}", metadata.partition(), metadata.offset());
      }else {
        LOG.error("Exception : ", e);
      }
    }
  }
}

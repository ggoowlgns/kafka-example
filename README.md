### Kafka library demo project
> https://github.com/onlybooks/kafka
> https://docs.confluent.io/platform/current/clients/consumer.html

책(Apache Kafka 카프카, 데이터플랫폼의 최강자)을 참고로 하여 정리 목적으로 만든 
Spring Boot 기반 프로젝트 입니다.



#### 용어 정리
* broker
* topic
* partition
  * topic 을 여러개로 쪼갬 : for multi clients : 처리량 관련
  * 브로커당 최대 2000개 이내의 partition 수를 권장하고 있다.
  * 10MB/sec의 데이터 per partition 에 넣을수 있다. 
  * 무작정 증가시 단점
    * file handler 증가
    * broker 한대가 불확실하게 죽어버리면 (kill -9) 비가용성은 Partition 갯수에 비례한다.
      * ex) 한 broker에 2000개의 Partition 존재 (2 Replication factor) -> 리더 파티션이 약 1000개 -> 얘(broker)가 죽음 -> 파티션 한개당 리더 이전 시간 5ms 라고 생각하면(순차적으로 리더 이전) -> 어떤 파티션은 5초동안 불능 
      * 즉, partition을 너무 많이 늘리면 비가용성 증가 -> but, 보면 replication factor 를 증가시키면 그만큼 비가용성을 반비례 시킬수가 있긴 하다. (disk 사용량은 늘어나겠지만,,)
* replcation
  * partition 을 복수개로 관리 : for 고가용성, 안전성
  * 역할
    * 리더 : 모든 데이터 읽기, 쓰기 처리
    * 팔로워 : 리더의 데이터를 가져와서 동기화
  * ISR (In Sync Replica):
    * replication 되고 있는 그룹
    * 여기에 속한 replication 팔로워만 다음 리더로 승격될 수 있다.(동기화가 보장된 친구들)
    * replica.lag.time.max.ms 주기 만큼 리더의 데이터 확인 (Fetch 요청) : 리더가 팔로워의 요청을 받지 못하면 ISR 그룹에서 제거 -> 리더로 올라갈수 있는 자격 박탈
  * 브로커 전체 다운 상황 대응 : 설정 가능 - server.properties : unclean.leader.election.enable
    * (false) 마지막 리더가 있는 브로커가 살아나기를 기다린다. (제일 먼저 올라오는 브로커를 마지막 리더로 유도) -> 메시지 손실 방지 but, 장애 지연
    * (true) ISR에서 추방되었지만 먼저 살아나면 자동으로 리더가 되도록 -> 빠른 서비스 제공, 메시지 손실 o
* producer
* consumer
  * zookeeper 의 /kafka/consumers 에 영구 ZNode로 각 파티션들에 대해 어디까지 읽었는지를 기록해둔다.
    * -> consumer의 group.id를  UUID 로 하면 안될듯? (재시작하면 파티션의 처음부터 읽는꼴? ㄴㄴ ㄱㅊ -> latest msg 를 읽게 할 수 도 있다.)
      * 새로 들어오면 auto.offset.reset 정책에 의해서 어디서 부터 읽을지 결정함 : latest
    




#### 깨알 배경지식
 * Zookeeper
    * Znode
      * 영구 : delete 를 호출하여 삭제
      * 임시 : 생성한 Client의 연결이 끊어지면|장애 발생시 삭제
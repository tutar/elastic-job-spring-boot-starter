
# elastic-job--spring-boot-starter

### setup 1
***import dependency***
```java
        <dependency>
            <groupId>com.vanke</groupId>
            <artifactId>elastic-job-spring-boot-starter</artifactId>
            <version>0.1-SNAPSHOT</version>
        </dependency>
```
### setup 2
***setting up application.yml***
```
#elastic-job
elaticjob:
  zookeeper:
    serverList: localhost:2181
    namespace: dd-job
  event:
    datasource:
      url: jdbc:mysql://localhost:3306/job_event_db?useUnicode=true&characterEncoding=utf-8&verifyServerCertificate=false&useSSL=false&requireSSL=false
      driver-class-name: com.mysql.jdbc.Driver
      username: root
      password: root
```
### setup 3
***define job class***
```java
import com.dangdang.elasticjob.annotation.ElasticSimpleJob;
import com.dangdang.ddframe.job.api.ShardingContext;


@Component
public class MyJob {

    //Job configuration annotation
    @ElasticSimpleJob("0 * * * * ?")
    public void tastExample1(ShardingContext shardingContext) {
        //do something
    }
}
```

### Notation
1、该注解目前只在方法级别，类级别待扩展
2、若不声明分片数，默认分片数为1
3、event存储支持数据源配置
```java/*
   a、配置文件中通过elaticjob.evet.datasource声明
   b、注解ElasticSimpleJob中dataSource属性指定，其value必须是已注入spring中的bean
   后者会覆盖前者，若都不指定，event不会存储，console事件追踪页面会为空
   */
```

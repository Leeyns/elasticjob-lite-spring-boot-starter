package com.renheng.elasticjob.lite.autoconfigure;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dangdang.ddframe.job.api.simple.SimpleJob;
import com.dangdang.ddframe.job.config.JobCoreConfiguration;
import com.dangdang.ddframe.job.config.simple.SimpleJobConfiguration;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbConfiguration;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.lite.spring.api.SpringJobScheduler;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperConfiguration;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter;
import com.renheng.elasticjob.lite.annotation.ElasticSimpleJob;
import com.renheng.elasticjob.lite.config.ZookeeperConfigurationProperties;

@Configuration
@EnableConfigurationProperties(ZookeeperConfigurationProperties.class)
@ConditionalOnExpression("'${spring.elasticjob.zookeeper.server-lists}'.length() > 0")
public class ElasticJobAutoConfiguration {

	public static final String ELASTIC_JOB_REGISTRY_CENTER = "elasticJobRegistryCenter";
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	@Autowired
	private ZookeeperConfigurationProperties zookeeperConfigurationProperties;
	
	@Autowired
	private ApplicationContext applicationContext;
	
	
	@Bean(name = ELASTIC_JOB_REGISTRY_CENTER,initMethod = "init")
    @ConditionalOnMissingBean
    public ZookeeperRegistryCenter elasticJobRegistryCenter() {
		logger.info("Create zookeeper registry center...");
        ZookeeperConfiguration zookeeperConfiguration = new ZookeeperConfiguration(zookeeperConfigurationProperties.getServerLists(), zookeeperConfigurationProperties.getNamespace());
        zookeeperConfiguration.setBaseSleepTimeMilliseconds(zookeeperConfigurationProperties.getBaseSleepTimeMilliseconds());
        zookeeperConfiguration.setConnectionTimeoutMilliseconds(zookeeperConfigurationProperties.getConnectionTimeoutMilliseconds());
        zookeeperConfiguration.setMaxSleepTimeMilliseconds(zookeeperConfigurationProperties.getMaxSleepTimeMilliseconds());
        zookeeperConfiguration.setSessionTimeoutMilliseconds(zookeeperConfigurationProperties.getSessionTimeoutMilliseconds());
        zookeeperConfiguration.setMaxRetries(zookeeperConfigurationProperties.getMaxRetries());
        zookeeperConfiguration.setDigest(zookeeperConfigurationProperties.getDigest());
        return new ZookeeperRegistryCenter(zookeeperConfiguration);
    }
	
	@PostConstruct
	public void initElasticJob(){
		logger.info("Start initElasticJob...");
		ZookeeperRegistryCenter regCenter = elasticJobRegistryCenter();
		Map<String, SimpleJob> map = applicationContext.getBeansOfType(SimpleJob.class);
		for(Map.Entry<String, SimpleJob> entry : map.entrySet()){
			SimpleJob simpleJob = entry.getValue();
			ElasticSimpleJob elasticSimpleJobAnnotation=simpleJob.getClass().getAnnotation(ElasticSimpleJob.class);
			String cron=StringUtils.defaultIfBlank(elasticSimpleJobAnnotation.cron(), elasticSimpleJobAnnotation.value());
			String jobName=StringUtils.defaultIfBlank(elasticSimpleJobAnnotation.jobName(), simpleJob.getClass().getName());
			SimpleJobConfiguration simpleJobConfiguration=new SimpleJobConfiguration(JobCoreConfiguration.newBuilder(jobName, cron, elasticSimpleJobAnnotation.shardingTotalCount()).shardingItemParameters(elasticSimpleJobAnnotation.shardingItemParameters()).jobParameter(elasticSimpleJobAnnotation.jobParameter()).build(), simpleJob.getClass().getCanonicalName());
			LiteJobConfiguration liteJobConfiguration=LiteJobConfiguration.newBuilder(simpleJobConfiguration).overwrite(elasticSimpleJobAnnotation.overwrite()).build();
			String dataSourceRef=elasticSimpleJobAnnotation.dataSource();
			if(StringUtils.isNotBlank(dataSourceRef)){
				if(!applicationContext.containsBean(dataSourceRef)){
					throw new RuntimeException("not exist datasource ["+dataSourceRef+"] !");
				}
				DataSource dataSource=(DataSource)applicationContext.getBean(dataSourceRef);
				JobEventRdbConfiguration jobEventRdbConfiguration=new JobEventRdbConfiguration(dataSource);
				SpringJobScheduler jobScheduler=new SpringJobScheduler(simpleJob, regCenter, liteJobConfiguration,jobEventRdbConfiguration);
				jobScheduler.init();
			}else{
				SpringJobScheduler jobScheduler=new SpringJobScheduler(simpleJob, regCenter, liteJobConfiguration);
				jobScheduler.init();
			}
		}
	}
}

package com.dangdang.elasticjob.autoconfigure;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;
import javax.sql.DataSource;

import com.alibaba.druid.pool.DruidDataSource;
import com.dangdang.elasticjob.MethodDelegateJob;
import com.dangdang.elasticjob.annotation.ElasticSimpleJob;
import com.dangdang.elasticjob.annotation.ElasticSimpleJobs;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import com.dangdang.ddframe.job.config.JobCoreConfiguration;
import com.dangdang.ddframe.job.config.simple.SimpleJobConfiguration;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbConfiguration;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.lite.spring.api.SpringJobScheduler;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringValueResolver;


public class ElasticJobAutoConfiguration implements BeanPostProcessor,EmbeddedValueResolverAware {

	protected final Log logger = LogFactory.getLog(getClass());

    @Resource
    private ZookeeperRegistryCenter regCenter;

	private final Set<Class<?>> nonAnnotatedClasses =
			Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>(64));

	@Autowired
	private ApplicationContext applicationContext;

	private StringValueResolver embeddedValueResolver;

	@Autowired(required = false)
	private DataSource defaultDataSource;

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

		Class<?> targetClass = AopUtils.getTargetClass(bean);
		if (!this.nonAnnotatedClasses.contains(targetClass)) {
			Map<Method, Set<ElasticSimpleJob>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
					new MethodIntrospector.MetadataLookup<Set<ElasticSimpleJob>>() {
						@Override
						public Set<ElasticSimpleJob> inspect(Method method) {
							Set<ElasticSimpleJob> scheduledMethods =
									AnnotatedElementUtils.getMergedRepeatableAnnotations(method, ElasticSimpleJob.class, ElasticSimpleJobs.class);
							return (!scheduledMethods.isEmpty() ? scheduledMethods : null);
						}
					});
			if (annotatedMethods.isEmpty()) {
				this.nonAnnotatedClasses.add(targetClass);
				if (logger.isTraceEnabled()) {
					logger.trace("No @ElasticSimpleJob annotations found on bean class: " + bean.getClass());
				}
			} else {
				// Non-empty set of methods
				for (Map.Entry<Method, Set<ElasticSimpleJob>> entry : annotatedMethods.entrySet()) {
					Method method = entry.getKey();
					for (ElasticSimpleJob scheduled : entry.getValue()) {
						processScheduled(scheduled, method, bean, this.regCenter);
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @ElasticSimpleJob methods processed on bean '" + beanName +
							"': " + annotatedMethods);
				}
			}
		}
		return bean;
	}


	protected void processScheduled(ElasticSimpleJob elasticSimpleJobAnnotation, Method method, Object bean,ZookeeperRegistryCenter regCenter) {
		try {

			Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
            SimpleJob simpleJob = new MethodDelegateJob(bean, invocableMethod);

			String cron=StringUtils.defaultIfBlank(elasticSimpleJobAnnotation.cron(), elasticSimpleJobAnnotation.value());

			String jobName = "";
			if(StringUtils.isNotBlank(elasticSimpleJobAnnotation.jobName())){
				jobName = elasticSimpleJobAnnotation.jobName();
			}else{
				jobName = new StringBuilder().append(bean.getClass().getName()).append(".").append(method.getName()).toString();
			}

			if(embeddedValueResolver !=null){
				cron = this.embeddedValueResolver.resolveStringValue(cron);
				jobName = this.embeddedValueResolver.resolveStringValue(jobName);
			}

			JobCoreConfiguration jobCoreConfiguration = JobCoreConfiguration.newBuilder(jobName, cron, elasticSimpleJobAnnotation.shardingTotalCount())
					.shardingItemParameters(elasticSimpleJobAnnotation.shardingItemParameters())
					.jobParameter(elasticSimpleJobAnnotation.jobParameter()).build();

			SimpleJobConfiguration simpleJobConfiguration=new SimpleJobConfiguration(jobCoreConfiguration,bean.getClass().getCanonicalName());

			LiteJobConfiguration liteJobConfiguration=LiteJobConfiguration.newBuilder(simpleJobConfiguration).overwrite(true).build();

			// 首先获取配置数据源 没有则使用默认数据源
			DataSource dataSource=getDataSource();
			if(!Objects.nonNull(dataSource)){
				dataSource = defaultDataSource;
			}

			// 注解中ElasticSimpleJob dataSource可以覆盖配置文件指定的 elasticJobEventDataSource
			String dataSourceRef=elasticSimpleJobAnnotation.dataSource();
			if(StringUtils.isNotBlank(dataSourceRef)){
				if(!applicationContext.containsBean(dataSourceRef)){
					throw new RuntimeException("not exist datasource ["+dataSourceRef+"] !");
				}
				dataSource = (DataSource)applicationContext.getBean(dataSourceRef);
			}

			if(Objects.nonNull(dataSource)){

				JobEventRdbConfiguration jobEventRdbConfiguration=new JobEventRdbConfiguration(dataSource);
                SpringJobScheduler jobScheduler=new SpringJobScheduler(simpleJob, regCenter, liteJobConfiguration,jobEventRdbConfiguration);
				jobScheduler.init();
			}else{
				logger.warn("elastic job event not be persistent, please config elaticjob.event.datasource or set dataSource in annotation ElasticSimpleJob");
                SpringJobScheduler jobScheduler=new SpringJobScheduler(simpleJob, regCenter, liteJobConfiguration);
				jobScheduler.init();
			}
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"Encountered invalid @ElasticSimpleJob method '" + method.getName() + "': " + ex.getMessage());
		}
	}

	private DataSource getDataSource(){

		if(embeddedValueResolver !=null){
			String driverClassName = this.embeddedValueResolver.resolveStringValue("${elaticjob.event.datasource.driver-class-name:}");
			String url = this.embeddedValueResolver.resolveStringValue("${elaticjob.event.datasource.url:}");
			String username = this.embeddedValueResolver.resolveStringValue("${elaticjob.event.datasource.username:}");
			String password = this.embeddedValueResolver.resolveStringValue("${elaticjob.event.datasource.password:}");

			DruidDataSource dataSource = new DruidDataSource();
			dataSource.setUrl(url);
			dataSource.setUsername(username);
			dataSource.setPassword(password);
			dataSource.setDriverClassName(driverClassName);
			dataSource.setInitialSize(2);
			dataSource.setMaxActive(20);
			dataSource.setMinIdle(0);
			dataSource.setMaxWait(60000);
			dataSource.setValidationQuery("SELECT 1");
			dataSource.setTestOnBorrow(false);
			dataSource.setTestWhileIdle(true);
			dataSource.setPoolPreparedStatements(false);
			return dataSource;
		}
		return null;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}
}

package com.dangdang.elasticjob.annotation;

import java.lang.annotation.*;

import org.springframework.core.annotation.AliasFor;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(ElasticSimpleJobs.class)
public @interface ElasticSimpleJob {
	
	@AliasFor("cron")
	public abstract String value() default "";

	@AliasFor("value")
	public abstract String cron() default "";
	
	public abstract String jobName() default "";
	
	public abstract int shardingTotalCount() default 1;
	
	public abstract String shardingItemParameters() default "";

	public abstract String jobParameter() default "";

	public abstract String dataSource() default "";
	
	public abstract String description() default "";

	public abstract boolean disabled() default false;

	public abstract boolean overwrite() default true;
}

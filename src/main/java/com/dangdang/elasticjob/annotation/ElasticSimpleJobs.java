package com.dangdang.elasticjob.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ElasticSimpleJobs {

	ElasticSimpleJob[] value();
}

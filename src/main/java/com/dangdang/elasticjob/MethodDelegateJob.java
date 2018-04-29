package com.dangdang.elasticjob;

import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Created by tutar on 2018/4/7.
 */
public class MethodDelegateJob implements SimpleJob{
    private final Object target;

    private final Method method;


    public MethodDelegateJob(Object target, Method method) {
        this.target = target;
        this.method = method;
    }

    public MethodDelegateJob(Object target, String methodName) throws NoSuchMethodException {
        this.target = target;
        this.method = target.getClass().getMethod(methodName);
    }


    public Object getTarget() {
        return this.target;
    }

    public Method getMethod() {
        return this.method;
    }

    @Override
    public void execute(ShardingContext shardingContext) {
        try {
            ReflectionUtils.makeAccessible(this.method);
            if(this.method.getParameterCount()==1 && this.method.getParameterTypes()[0].getName().equals(ShardingContext.class.getName())){
                this.method.invoke(this.target,shardingContext);
            } else {
                this.method.invoke(this.target);
            }
        }
        catch (InvocationTargetException ex) {
            ReflectionUtils.rethrowRuntimeException(ex.getTargetException());
        }
        catch (IllegalAccessException ex) {
            throw new UndeclaredThrowableException(ex);
        }
    }
}

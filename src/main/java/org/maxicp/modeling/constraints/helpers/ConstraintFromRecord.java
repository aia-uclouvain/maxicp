package org.maxicp.modeling.constraints.helpers;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.util.ImmutableSet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Utility class to automagically compute the scope of a record constraint.
 * Only works with some basic types:
 * - {@code Expressions} and derivatives
 * - {@code Collection<Expression>} and derivatives
 * - {@code Collection<Collection<Expression>>} and derivatives
 * - {@code Expression[]}
 * - {@code Expression[][]}
 *
 * Explodes when it discovers a type it doesn't know.
 * You can @IgnoreScope custom types you want it to ignore.
 */
public interface ConstraintFromRecord extends Constraint, CacheScope {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    @interface IgnoreScope {}

    @Override
    default Collection<? extends Expression> computeScope() {
        return ConstraintFromRecordHelper.computeScope(this);
    }
}

class ConstraintFromRecordHelper {
    static Collection<? extends Expression> computeScope(ConstraintFromRecord record) {
        if(!record.getClass().isRecord())
            throw new ClassCastException("A class implementing ConstraintFromRecord must be a record");
        ImmutableSet.Builder<Expression> fset = new ImmutableSet.Builder<>();
        for(RecordComponent rc: record.getClass().getRecordComponents()) {
            rc.getAccessor().setAccessible(true);
            findTypeAndExtract(fset, record, rc);
        }
        return fset.build();
    }

    static ImmutableSet<Type> ignored = ImmutableSet.<Type>builder().add(
            int.class, long.class, float.class, double.class, char.class, short.class,
            Integer.class, Long.class, Float.class, Double.class, Character.class, Short.class, String.class,
            int[].class, long[].class, float[].class, double[].class, char[].class, short[].class, String[].class,
            int[][].class, long[][].class, float[][].class, double[][].class, char[][].class, short[][].class, String[][].class
    ).build();

    static void findTypeAndExtract(ImmutableSet.Builder<Expression> fset, ConstraintFromRecord record, RecordComponent rc) {
        try {
            Type type = rc.getGenericType();

            if(rc.isAnnotationPresent(ConstraintFromRecord.IgnoreScope.class))
                return;

            switch (type) {
                case ParameterizedType pt -> {
                    Type rawType = pt.getRawType();
                    if (rawType instanceof Class<?> && Collection.class.isAssignableFrom((Class<?>) rawType)) {
                        processCollection(fset, record, rc);
                        return;
                    }
                }
                case Class<?> cls -> {
                    if (Expression.class.isAssignableFrom(cls)) {
                        fset.add((Expression) rc.getAccessor().invoke(record));
                        return;
                    }

                    if(Expression[].class.isAssignableFrom(cls)) {
                        fset.add((Expression[]) rc.getAccessor().invoke(record));
                        return;
                    }

                    if(Expression[][].class.isAssignableFrom(cls)) {
                        Expression[][] t = (Expression[][]) rc.getAccessor().invoke(record);
                        for(Expression[] tt: t)
                            fset.add(tt);
                        return;
                    }

                    if(ignored.contains(cls))
                        return;
                }
                default -> {
                    //ignore
                }
            }
            throw new IllegalArgumentException("Unknown argument type " + type);
        }
        catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException("Unknown argument type");
        }
    }

    static void processCollection(ImmutableSet.Builder<Expression> fset, ConstraintFromRecord record, RecordComponent rc) throws InvocationTargetException, IllegalAccessException {
        ParameterizedType type = (ParameterizedType)rc.getGenericType();
        assert Collection.class.isAssignableFrom((Class<?>) type.getRawType());
        Type[] ta = type.getActualTypeArguments();
        //For now we support only basic collections
        if(ta.length != 1)
            throw new IllegalArgumentException("Unknown argument type");

        switch (ta[0]) {
            case Class<?> cls -> {
                if(Expression.class.isAssignableFrom((Class<?>) cls)) {
                    fset.addAll((Collection<? extends Expression>) rc.getAccessor().invoke(record));
                    return;
                }
                throw new IllegalArgumentException("Unknown argument type " + rc);
            }
            case ParameterizedType pt -> {
                Type rawType = pt.getRawType();
                if (rawType instanceof Class<?> && Collection.class.isAssignableFrom((Class<?>) rawType)) {
                    Type[] tb = pt.getActualTypeArguments();
                    if(tb.length == 1 && tb[0] instanceof Class<?> && Expression.class.isAssignableFrom((Class<?>) tb[0])) {
                        Collection<Collection<? extends Expression>> top = (Collection<Collection<? extends Expression>>) rc.getAccessor().invoke(record);
                        for(Collection<? extends Expression> c: top)
                            fset.addAll(c);
                        return;
                    }
                    throw new IllegalArgumentException("Unknown argument type");
                }
                else
                    throw new IllegalArgumentException("Unknown argument type " + pt);
            }
            default -> {
                throw new IllegalArgumentException("Unknown argument type " + rc);
            }
        }
    }
}